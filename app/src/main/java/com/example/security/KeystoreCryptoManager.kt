package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides Android Keystore AES-256-GCM encryption, PBKDF2-HMAC-SHA256 PIN hashing,
 * EncryptedSharedPreferences storage, SQLCipher passphrase provisioning, and encrypted .KCB envelopes.
 */
class KeystoreCryptoManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MEDIA_KEY_ALIAS = "keycopier_media_aes256_v1"
        private const val PREFS_FILE = "keycopier_secure_vault_prefs"
        private const val KEY_PIN_HASH = "pin_hash_v1"
        private const val KEY_PIN_SALT = "pin_salt_v1"
        private const val KEY_DB_PASSPHRASE = "sqlcipher_passphrase_v1"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private val BACKUP_MAGIC = "KCB1".toByteArray(Charsets.US_ASCII)
    }

    private val secureRandom = SecureRandom()

    val securePrefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Fallback for Robolectric JVM environment where AndroidKeyStore is not emulated
            context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun hasPinConfigured(): Boolean =
        !securePrefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()

    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) {
            "PIN must be 4 to 8 digits"
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2(pin.toCharArray(), salt)
        securePrefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = securePrefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = securePrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val candidateHash = pbkdf2(pin.toCharArray(), salt)
        return MessageDigest.isEqual(expectedHash, candidateHash)
    }

    fun getOrCreateDatabasePassphrase(): ByteArray {
        val existing = securePrefs.getString(KEY_DB_PASSPHRASE, null)
        if (!existing.isNullOrEmpty()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val keyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        securePrefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
            .apply()
        return keyBytes
    }

    fun encryptMediaBytes(plain: ByteArray): ByteArray {
        val key = getOrCreateMediaKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + ciphertext.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(ciphertext, 0, this, 1 + iv.size, ciphertext.size)
        }
    }

    fun decryptMediaBytes(blob: ByteArray): ByteArray {
        if (blob.size < 14) return ByteArray(0)
        val ivLen = blob[0].toInt() and 0xFF
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ciphertext = blob.copyOfRange(1 + ivLen, blob.size)
        val key = getOrCreateMediaKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun saveEncryptedFile(file: File, plainBytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(encryptMediaBytes(plainBytes))
    }

    fun readEncryptedFile(file: File): ByteArray {
        if (!file.exists()) return ByteArray(0)
        return decryptMediaBytes(file.readBytes())
    }

    /**
     * Portable password-derived AES-256-GCM envelope for .KCB backup files.
     * Format: [4B "KCB1"][16B salt][12B IV][ciphertext+GCM tag]
     */
    fun encryptBackupEnvelope(plainBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val derivedKey = SecretKeySpec(pbkdf2(passphrase.toCharArray(), salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        return ByteArray(BACKUP_MAGIC.size + salt.size + iv.size + ciphertext.size).apply {
            var pos = 0
            System.arraycopy(BACKUP_MAGIC, 0, this, pos, BACKUP_MAGIC.size); pos += BACKUP_MAGIC.size
            System.arraycopy(salt, 0, this, pos, salt.size); pos += salt.size
            System.arraycopy(iv, 0, this, pos, iv.size); pos += iv.size
            System.arraycopy(ciphertext, 0, this, pos, ciphertext.size)
        }
    }

    fun isEncryptedBackupEnvelope(bytes: ByteArray): Boolean {
        if (bytes.size < BACKUP_MAGIC.size + 28) return false
        return bytes.copyOfRange(0, BACKUP_MAGIC.size).contentEquals(BACKUP_MAGIC)
    }

    fun decryptBackupEnvelope(blob: ByteArray, passphrase: String): ByteArray {
        require(isEncryptedBackupEnvelope(blob)) { "Invalid KCB backup header" }
        var pos = BACKUP_MAGIC.size
        val salt = blob.copyOfRange(pos, pos + 16); pos += 16
        val iv = blob.copyOfRange(pos, pos + GCM_IV_BYTES); pos += GCM_IV_BYTES
        val ciphertext = blob.copyOfRange(pos, blob.size)
        val derivedKey = SecretKeySpec(pbkdf2(passphrase.toCharArray(), salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateMediaKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(MEDIA_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                MEDIA_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }.getOrElse {
            // Deterministic fallback key in EncryptedSharedPreferences if Keystore unavailable (Robolectric)
            val keyB64 = securePrefs.getString("fallback_media_key", null)
            val raw = if (keyB64 != null) {
                Base64.decode(keyB64, Base64.NO_WRAP)
            } else {
                ByteArray(32).also {
                    secureRandom.nextBytes(it)
                    securePrefs.edit().putString("fallback_media_key", Base64.encodeToString(it, Base64.NO_WRAP)).apply()
                }
            }
            SecretKeySpec(raw, "AES")
        }
    }
}
