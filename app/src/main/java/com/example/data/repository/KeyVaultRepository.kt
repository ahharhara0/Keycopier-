package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.local.KeyAttachmentEntity
import com.example.data.local.KeyCopierDao
import com.example.data.local.KeyRecordEntity
import com.example.domain.model.AttachmentType
import com.example.domain.model.KeyAttachment
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File

class KeyVaultRepository(
    private val context: Context,
    private val dao: KeyCopierDao,
    private val cryptoManager: KeystoreCryptoManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaDir: File by lazy {
        File(context.filesDir, "vault_media").apply { mkdirs() }
    }

    val allKeys: Flow<List<KeyRecord>> = dao.observeAllKeys().map { list ->
        list.map { it.toDomain() }
    }

    val attachmentCount: Flow<Int> = dao.observeAttachmentCount()

    suspend fun getKeyById(id: Long): KeyRecord? = dao.getKeyById(id)?.toDomain()

    suspend fun saveKey(record: KeyRecord): Long {
        val now = System.currentTimeMillis()
        val entity = record.toEntity(now)
        return if (record.id == 0L) {
            dao.insertKey(entity)
        } else {
            dao.updateKey(entity)
            record.id
        }
    }

    suspend fun importRecords(records: List<KeyRecord>): Int {
        var count = 0
        for (record in records) {
            saveKey(record.copy(id = 0L, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            count++
        }
        return count
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun deleteKey(id: Long) = withContext(Dispatchers.IO) {
        val attachments = dao.getAttachmentsForKey(id)
        for (att in attachments) {
            runCatching { File(att.localPath).delete() }
        }
        dao.deleteKeyById(id)
    }

    fun observeAttachments(keyRecordId: Long): Flow<List<KeyAttachment>> =
        dao.observeAttachmentsForKey(keyRecordId).map { list -> list.map { it.toDomain() } }

    suspend fun saveEncryptedPhotoAttachment(
        keyRecordId: Long,
        bitmap: Bitmap,
        type: AttachmentType,
        isPrimary: Boolean = true
    ): KeyAttachment = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        val fileName = "key_${keyRecordId}_${type.name}_${System.currentTimeMillis()}.enc"
        val targetFile = File(mediaDir, fileName)
        cryptoManager.saveEncryptedFile(targetFile, out.toByteArray())

        val entity = KeyAttachmentEntity(
            keyRecordId = keyRecordId,
            localPath = targetFile.absolutePath,
            fileType = type.name,
            isPrimary = isPrimary,
            createdAt = System.currentTimeMillis()
        )
        val newId = dao.insertAttachment(entity)
        entity.copy(id = newId).toDomain()
    }

    suspend fun loadEncryptedBitmap(attachment: KeyAttachment): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(attachment.localPath)
            val plain = cryptoManager.readEncryptedFile(file)
            if (plain.isEmpty()) null else BitmapFactory.decodeByteArray(plain, 0, plain.size)
        }.getOrNull()
    }

    fun calculateVaultStorageBytes(): Long {
        val mediaBytes = mediaDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val dbFile = context.getDatabasePath("keycopier_vault_encrypted.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        return mediaBytes + dbBytes
    }

    private fun KeyRecordEntity.toDomain(): KeyRecord {
        val depths = runCatching { json.decodeFromString<List<Float>>(depthsJson) }.getOrDefault(emptyList())
        val spacings = runCatching { json.decodeFromString<List<Float>>(spacingsJson) }.getOrDefault(emptyList())
        val cat = runCatching { KeyCategory.valueOf(category) }.getOrDefault(KeyCategory.OTHER)
        return KeyRecord(
            id = id,
            name = name,
            category = cat,
            keyStandardId = keyStandard,
            bittingCode = bittingCode,
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm,
            bladeThicknessMm = bladeThicknessMm,
            pinCount = pinCount,
            depthsMm = depths,
            spacingsMm = spacings,
            isDoubleSided = isDoubleSided,
            notes = notes,
            tags = tags,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun KeyRecord.toEntity(now: Long): KeyRecordEntity = KeyRecordEntity(
        id = id,
        name = name,
        category = category.name,
        keyStandard = keyStandardId,
        bittingCode = bittingCode,
        bladeLengthMm = bladeLengthMm,
        bladeWidthMm = bladeWidthMm,
        bladeThicknessMm = bladeThicknessMm,
        pinCount = pinCount,
        depthsJson = json.encodeToString(depthsMm),
        spacingsJson = json.encodeToString(spacingsMm),
        isDoubleSided = isDoubleSided,
        notes = notes,
        tags = tags,
        isFavorite = isFavorite,
        createdAt = if (id == 0L) now else createdAt,
        updatedAt = now
    )

    private fun KeyAttachmentEntity.toDomain(): KeyAttachment = KeyAttachment(
        id = id,
        keyRecordId = keyRecordId,
        localPath = localPath,
        fileType = runCatching { AttachmentType.valueOf(fileType) }.getOrDefault(AttachmentType.ORIGINAL_PHOTO),
        isPrimary = isPrimary,
        createdAt = createdAt
    )
}
