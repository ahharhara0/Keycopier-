package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.domain.model.VaultBackupPayload
import com.example.security.KeystoreCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    STL("stl", "application/octet-stream"),
    SVG("svg", "image/svg+xml"),
    DXF("dxf", "application/dxf"),
    PDF("pdf", "application/pdf"),
    PNG("png", "image/png"),
    JSON("json", "application/json"),
    KCB("kcb", "application/octet-stream")
}

class ExportManager(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun generateRecordExportBytes(record: KeyRecord, format: ExportFormat): ByteArray =
        withContext(Dispatchers.Default) {
            when (format) {
                ExportFormat.STL -> {
                    val mesh = StlMeshGenerator.generateMesh(record)
                    StlMeshGenerator.toBinaryStl(mesh, "KeyCopier Pro | ${record.keyStandardId} | ${record.bittingCode}")
                }
                ExportFormat.SVG -> SvgTemplateGenerator.generateSvg(record).toByteArray(Charsets.UTF_8)
                ExportFormat.DXF -> DxfTemplateGenerator.generateDxf(record).toByteArray(Charsets.UTF_8)
                ExportFormat.PDF -> PdfScaleTemplateGenerator.generate1To1PdfBytes(record)
                ExportFormat.PNG -> PngBlueprintGenerator.generateBlueprintPngBytes(record)
                ExportFormat.JSON -> json.encodeToString(VaultBackupPayload(records = listOf(record))).toByteArray(Charsets.UTF_8)
                ExportFormat.KCB -> {
                    val raw = json.encodeToString(VaultBackupPayload(records = listOf(record))).toByteArray(Charsets.UTF_8)
                    cryptoManager.encryptBackupEnvelope(raw, record.bittingCode.ifBlank { "KEYCOPIER" })
                }
            }
        }

    suspend fun generateVaultBackupBytes(
        records: List<KeyRecord>,
        encrypted: Boolean,
        passphrase: String = ""
    ): ByteArray = withContext(Dispatchers.Default) {
        val raw = json.encodeToString(VaultBackupPayload(records = records)).toByteArray(Charsets.UTF_8)
        if (encrypted) {
            cryptoManager.encryptBackupEnvelope(raw, passphrase.ifBlank { "KEYCOPIER_VAULT" })
        } else {
            raw
        }
    }

    fun parseVaultBackupBytes(bytes: ByteArray, passphrase: String = ""): List<KeyRecord> {
        val plainBytes = if (cryptoManager.isEncryptedBackupEnvelope(bytes)) {
            cryptoManager.decryptBackupEnvelope(bytes, passphrase.ifBlank { "KEYCOPIER_VAULT" })
        } else {
            bytes
        }
        val payload = json.decodeFromString<VaultBackupPayload>(plainBytes.toString(Charsets.UTF_8))
        return payload.records.map { it.copy(id = 0L) }
    }

    suspend fun writeBytesToUri(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: error("Unable to open output stream")
    }

    suspend fun readBytesFromUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: error("Unable to read selected file")
    }

    suspend fun shareBytes(fileName: String, mimeType: String, bytes: ByteArray) {
        val shareFile = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, sanitizeFileName(fileName))
            file.writeBytes(bytes)
            file
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, fileName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sharePlainTextSpec(record: KeyRecord) {
        val text = formatStructuredSpecText(record)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "KeyCopier Spec: ${record.name}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, record.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun formatStructuredSpecText(record: KeyRecord): String {
        val spec = KeyStandards.getById(record.keyStandardId)
        return buildString {
            appendLine("=== KeyCopier Pro Specification ===")
            appendLine("Name      : ${record.name}")
            appendLine("Standard  : ${spec.displayName}")
            appendLine("Blade Type: ${if (record.isDoubleSided) "Double-Sided" else "Single-Sided"}")
            appendLine("Bitting   : ${record.bittingCode}")
            appendLine(
                String.format(
                    Locale.US,
                    "Blade     : %.2f x %.2f x %.2f mm (%d pins)",
                    record.bladeLengthMm, record.bladeWidthMm, record.bladeThicknessMm, record.pinCount
                )
            )
            appendLine("Pin Table :")
            record.spacingsMm.forEachIndexed { idx, spacing ->
                val depth = record.depthsMm.getOrElse(idx) { 0f }
                appendLine(
                    String.format(
                        Locale.US,
                        "  #%d  pos=%.2fmm  depth=%.2fmm",
                        idx + 1, spacing, depth
                    )
                )
            }
            if (record.notes.isNotBlank()) {
                appendLine("Notes     : ${record.notes}")
            }
        }
    }

    fun suggestedFileName(record: KeyRecord, format: ExportFormat): String {
        val base = sanitizeFileName(record.name.ifBlank { "key_profile" })
        return "${base}_${record.keyStandardId}_${record.bittingCode}.${format.extension}"
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
}
