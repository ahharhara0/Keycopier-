package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.AutoLockTimeout
import com.example.domain.model.MeasurementUnit
import com.example.engine.PerspectiveCalibrator
import com.example.ui.VaultUiState
import com.example.ui.VaultViewModel
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusSuccess
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    vaultState: VaultUiState,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val biometricSupported = remember(context) {
        val mgr = BiometricManager.from(context)
        mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showDpiCalibrationDialog by remember { mutableStateOf(false) }
    var githubCopied by remember { mutableStateOf(false) }

    val formattedSize = remember(vaultState.storageBytes) {
        val kb = vaultState.storageBytes / 1024.0
        if (kb < 1024.0) String.format(Locale.US, "%.1f KB", kb)
        else String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Security Section
        item {
            SectionTitle(stringResource(R.string.settings_security_section))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangePinDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_change_pin"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null, tint = PrimaryBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_change_pin), style = MaterialTheme.typography.titleSmall)
                            Text("PBKDF2-SHA256 + Keystore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SecondaryTeal)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_biometric_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.settings_biometric_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) },
                            enabled = biometricSupported
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = PrimaryBlue)
                            Text(stringResource(R.string.settings_autolock_title), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val timeouts = listOf(
                                AutoLockTimeout.IMMEDIATE to R.string.autolock_immediate,
                                AutoLockTimeout.THIRTY_SECONDS to R.string.autolock_30s,
                                AutoLockTimeout.ONE_MINUTE to R.string.autolock_1m,
                                AutoLockTimeout.FIVE_MINUTES to R.string.autolock_5m,
                                AutoLockTimeout.NEVER to R.string.autolock_never
                            )
                            timeouts.take(3).forEach { (item, res) ->
                                FilterChip(
                                    selected = settings.autoLockSeconds == item.seconds,
                                    onClick = { viewModel.setAutoLockSeconds(item.seconds) },
                                    label = { Text(stringResource(res)) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = settings.autoLockSeconds == AutoLockTimeout.FIVE_MINUTES.seconds,
                                onClick = { viewModel.setAutoLockSeconds(AutoLockTimeout.FIVE_MINUTES.seconds) },
                                label = { Text(stringResource(R.string.autolock_5m)) }
                            )
                            FilterChip(
                                selected = settings.autoLockSeconds == AutoLockTimeout.NEVER.seconds,
                                onClick = { viewModel.setAutoLockSeconds(AutoLockTimeout.NEVER.seconds) },
                                label = { Text(stringResource(R.string.autolock_never)) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.lockNow() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pin_unlock_title))
                    }
                }
            }
        }

        // Precision & DPI Calibration Section
        item {
            SectionTitle(stringResource(R.string.settings_calibration_section))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val effectiveDpi = if (settings.screenDpiOverride > 50f) {
                        settings.screenDpiOverride
                    } else {
                        context.resources.displayMetrics.xdpi
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDpiCalibrationDialog = true }
                            .testTag("settings_dpi_calibrate"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = SecondaryTeal)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_dpi_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${stringResource(R.string.settings_dpi_desc)} (${String.format(Locale.US, "%.1f", effectiveDpi)} DPI)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(stringResource(R.string.settings_unit_title), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.preferredUnit == MeasurementUnit.MM,
                            onClick = { viewModel.setUnit(MeasurementUnit.MM) },
                            label = { Text(stringResource(R.string.unit_mm)) }
                        )
                        FilterChip(
                            selected = settings.preferredUnit == MeasurementUnit.MIL,
                            onClick = { viewModel.setUnit(MeasurementUnit.MIL) },
                            label = { Text(stringResource(R.string.unit_mil)) }
                        )
                    }
                }
            }
        }

        // Appearance & Language Section
        item {
            SectionTitle(stringResource(R.string.settings_appearance_section))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryBlue)
                        Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                            label = { Text(stringResource(R.string.lang_english)) },
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                        FilterChip(
                            selected = settings.language == AppLanguage.ARABIC,
                            onClick = { viewModel.setLanguage(AppLanguage.ARABIC) },
                            label = { Text(stringResource(R.string.lang_arabic)) },
                            modifier = Modifier.testTag("settings_lang_ar")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = PrimaryBlue)
                        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.theme == AppThemeMode.SYSTEM,
                            onClick = { viewModel.setTheme(AppThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) }
                        )
                        FilterChip(
                            selected = settings.theme == AppThemeMode.LIGHT,
                            onClick = { viewModel.setTheme(AppThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) }
                        )
                        FilterChip(
                            selected = settings.theme == AppThemeMode.DARK,
                            onClick = { viewModel.setTheme(AppThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) }
                        )
                    }
                }
            }
        }

        // Offline Privacy & Storage Card
        item {
            SectionTitle(stringResource(R.string.settings_privacy_section))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = StatusSuccess)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.settings_privacy_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            String.format(
                                stringResource(R.string.settings_storage_stats),
                                vaultState.allKeys.size,
                                vaultState.attachmentCount,
                                formattedSize
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Open Source & Owner Credits Section (Last Item)
        item {
            SectionTitle(stringResource(R.string.settings_about_section))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_owner_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_keycopier_icon_src),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SecondaryTeal.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = SecondaryTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_open_source_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SecondaryTeal
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_open_source_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "برمجة وتطوير : أحمد أمين بن هرهرة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("settings_owner_ar")
                        )
                        Text(
                            text = "AHMED AMEEN BAN HARHARA",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("settings_owner_en")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("GitHub", "@ahharhara"))
                            githubCopied = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_github_button")
                    ) {
                        Icon(
                            imageVector = if (githubCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (githubCopied) {
                                stringResource(R.string.settings_github_copied)
                            } else {
                                stringResource(R.string.settings_github_handle)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onSave = { newPin ->
                viewModel.changePin(newPin)
                showChangePinDialog = false
            }
        )
    }

    if (showDpiCalibrationDialog) {
        val defaultXdpi = context.resources.displayMetrics.xdpi.coerceIn(120f, 640f)
        DpiCalibrationDialog(
            initialDpi = if (settings.screenDpiOverride > 50f) settings.screenDpiOverride else defaultXdpi,
            defaultDpi = defaultXdpi,
            onDismiss = { showDpiCalibrationDialog = false },
            onSave = { newDpi ->
                viewModel.setScreenDpiOverride(newDpi)
                showDpiCalibrationDialog = false
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lenErr = stringResource(R.string.pin_length_error)
    val mismatchErr = stringResource(R.string.pin_mismatch_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { pin1 = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text(stringResource(R.string.pin_setup_title)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { pin2 = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text(stringResource(R.string.pin_confirm_title)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin1.length !in 4..8 -> error = lenErr
                    pin1 != pin2 -> error = mismatchErr
                    else -> onSave(pin1)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DpiCalibrationDialog(
    initialDpi: Float,
    defaultDpi: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var dpi by remember { mutableFloatStateOf(initialDpi) }
    val density = LocalDensity.current
    val cardWidthDp = with(density) {
        ((PerspectiveCalibrator.ID1_WIDTH_MM / 25.4f) * dpi).toDp()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dpi_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_dpi_desc), style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .width(cardWidthDp.coerceAtMost(290.dp))
                        .height(34.dp)
                        .background(SecondaryTeal, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "85.60 mm (ID-1)",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f DPI", dpi),
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = dpi,
                    onValueChange = { dpi = it },
                    valueRange = 140f..620f
                )
                TextButton(onClick = { dpi = defaultDpi }) {
                    Text("Reset (${String.format(Locale.US, "%.1f", defaultDpi)} DPI)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(dpi) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
