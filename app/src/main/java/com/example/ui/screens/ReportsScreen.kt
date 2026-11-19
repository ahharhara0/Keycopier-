package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.KeyRecord
import com.example.export.ExportFormat
import com.example.ui.VaultUiState
import com.example.ui.VaultViewModel
import com.example.ui.components.KeySilhouetteCanvas
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(
    vaultState: VaultUiState,
    preselectedKeyId: Long?,
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedKeyId by remember(preselectedKeyId, vaultState.allKeys) {
        mutableStateOf(
            preselectedKeyId?.takeIf { id -> vaultState.allKeys.any { it.id == id } }
                ?: vaultState.allKeys.firstOrNull()?.id
        )
    }

    LaunchedEffect(preselectedKeyId) {
        if (preselectedKeyId != null) selectedKeyId = preselectedKeyId
    }

    LaunchedEffect(vaultState.statusMessage) {
        val msg = vaultState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val activeKey: KeyRecord? = vaultState.allKeys.firstOrNull { it.id == selectedKeyId }
    var pendingSaveFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var pendingKcbExportPassphrase by remember { mutableStateOf<String?>(null) }
    var showKcbExportDialog by remember { mutableStateOf(false) }
    var showKcbImportDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var passphraseInput by remember { mutableStateOf("") }

    val exportSuccessTemplate = stringResource(R.string.reports_export_success)
    val importSuccessTemplate = stringResource(R.string.reports_import_success)

    val safSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingSaveFormat
        val key = activeKey
        if (uri != null && fmt != null && key != null) {
            viewModel.exportKeyToUri(key, fmt, uri) { fileName ->
                scope.launch {
                    snackbarHostState.showSnackbar(String.format(exportSuccessTemplate, fileName))
                }
            }
        }
        pendingSaveFormat = null
    }

    val safBackupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val pass = pendingKcbExportPassphrase
        if (uri != null) {
            val isEncrypted = pass != null
            viewModel.exportVaultBackupToUri(uri, isEncrypted, pass.orEmpty()) { fileName ->
                scope.launch {
                    snackbarHostState.showSnackbar(String.format(exportSuccessTemplate, fileName))
                }
            }
        }
        pendingKcbExportPassphrase = null
    }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.toString().lowercase()
            if (name.endsWith(".kcb")) {
                pendingImportUri = uri
                passphraseInput = ""
                showKcbImportDialog = true
            } else {
                viewModel.importBackupFromUri(uri, "") { count ->
                    scope.launch {
                        snackbarHostState.showSnackbar(String.format(importSuccessTemplate, count))
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.reports_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        if (vaultState.allKeys.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        text = stringResource(R.string.reports_no_keys),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.reports_select_key),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vaultState.allKeys.forEach { key ->
                        FilterChip(
                            selected = key.id == activeKey?.id,
                            onClick = { selectedKeyId = key.id },
                            label = { Text("${key.name} (${key.bittingCode})") }
                        )
                    }
                }
            }

            if (activeKey != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KeySilhouetteCanvas(
                                record = activeKey,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(6.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeKey.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${activeKey.keyStandardId} • ${activeKey.bittingCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Fabrication Export Formats
                item {
                    ExportFormatCard(
                        title = stringResource(R.string.reports_format_stl),
                        desc = stringResource(R.string.reports_format_stl_desc),
                        icon = Icons.Default.ViewInAr,
                        accent = PrimaryBlue,
                        onSave = {
                            pendingSaveFormat = ExportFormat.STL
                            safSaveLauncher.launch("${activeKey.name}.stl")
                        },
                        onShare = { viewModel.shareKeyTemplate(activeKey, ExportFormat.STL) },
                        tag = "export_stl_card"
                    )
                }
                item {
                    ExportFormatCard(
                        title = stringResource(R.string.reports_format_svg),
                        desc = stringResource(R.string.reports_format_svg_desc),
                        icon = Icons.Default.Architecture,
                        accent = SecondaryTeal,
                        onSave = {
                            pendingSaveFormat = ExportFormat.SVG
                            safSaveLauncher.launch("${activeKey.name}.svg")
                        },
                        onShare = { viewModel.shareKeyTemplate(activeKey, ExportFormat.SVG) },
                        tag = "export_svg_card"
                    )
                }
                item {
                    ExportFormatCard(
                        title = stringResource(R.string.reports_format_dxf),
                        desc = stringResource(R.string.reports_format_dxf_desc),
                        icon = Icons.Default.Code,
                        accent = StatusWarning,
                        onSave = {
                            pendingSaveFormat = ExportFormat.DXF
                            safSaveLauncher.launch("${activeKey.name}.dxf")
                        },
                        onShare = { viewModel.shareKeyTemplate(activeKey, ExportFormat.DXF) },
                        tag = "export_dxf_card"
                    )
                }
                item {
                    ExportFormatCard(
                        title = stringResource(R.string.reports_format_pdf),
                        desc = stringResource(R.string.reports_format_pdf_desc),
                        icon = Icons.Default.PictureAsPdf,
                        accent = Color(0xFFDC2626),
                        onSave = {
                            pendingSaveFormat = ExportFormat.PDF
                            safSaveLauncher.launch("${activeKey.name}_1to1.pdf")
                        },
                        onShare = { viewModel.shareKeyTemplate(activeKey, ExportFormat.PDF) },
                        tag = "export_pdf_card"
                    )
                }
                item {
                    ExportFormatCard(
                        title = stringResource(R.string.reports_format_png),
                        desc = stringResource(R.string.reports_format_png_desc),
                        icon = Icons.Default.Image,
                        accent = StatusSuccess,
                        onSave = {
                            pendingSaveFormat = ExportFormat.PNG
                            safSaveLauncher.launch("${activeKey.name}_blueprint.png")
                        },
                        onShare = { viewModel.shareKeyTemplate(activeKey, ExportFormat.PNG) },
                        tag = "export_png_card"
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryBlue)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.reports_format_text), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.reports_format_text_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { viewModel.shareKeySpecText(activeKey) }) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reports_share_now))
                            }
                        }
                    }
                }
            }
        }

        // Vault Backup & Restore
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.reports_backup_section),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            passphraseInput = ""
                            showKcbExportDialog = true
                        },
                        enabled = vaultState.allKeys.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reports_export_kcb))
                    }
                    OutlinedButton(
                        onClick = {
                            pendingKcbExportPassphrase = null
                            safBackupSaveLauncher.launch("keycopier_vault_backup.json")
                        },
                        enabled = vaultState.allKeys.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reports_export_json))
                    }
                    OutlinedButton(
                        onClick = { safImportLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reports_import_backup))
                    }
                }
            }
        }
    }

    if (showKcbExportDialog) {
        AlertDialog(
            onDismissRequest = { showKcbExportDialog = false },
            title = { Text(stringResource(R.string.reports_export_kcb)) },
            text = {
                OutlinedTextField(
                    value = passphraseInput,
                    onValueChange = { passphraseInput = it },
                    label = { Text("Backup Passphrase (AES-256-GCM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showKcbExportDialog = false
                    pendingKcbExportPassphrase = passphraseInput
                    safBackupSaveLauncher.launch("keycopier_vault_backup.kcb")
                }) {
                    Text(stringResource(R.string.reports_save_saf))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKcbExportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showKcbImportDialog) {
        AlertDialog(
            onDismissRequest = { showKcbImportDialog = false },
            title = { Text(stringResource(R.string.reports_import_backup)) },
            text = {
                OutlinedTextField(
                    value = passphraseInput,
                    onValueChange = { passphraseInput = it },
                    label = { Text("Backup Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingImportUri
                    showKcbImportDialog = false
                    if (uri != null) {
                        viewModel.importBackupFromUri(uri, passphraseInput) { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(String.format(importSuccessTemplate, count))
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKcbImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExportFormatCard(
    title: String,
    desc: String,
    icon: ImageVector,
    accent: Color,
    onSave: () -> Unit,
    onShare: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier.testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reports_save_saf), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reports_share_now), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
