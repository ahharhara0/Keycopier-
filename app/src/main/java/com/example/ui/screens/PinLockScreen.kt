package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.LockUiState
import com.example.ui.theme.SecondaryTeal

@Composable
fun PinLockScreen(
    lockState: LockUiState,
    biometricEnabled: Boolean,
    onPinCreated: (String) -> Unit,
    onPinAttempt: (String) -> Boolean,
    onBiometricSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    var pinBuffer by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isSetupMode = !lockState.hasPinConfigured
    val isConfirmStep = isSetupMode && firstSetupPin != null

    val canUseBiometric = remember(context, biometricEnabled, isSetupMode) {
        if (isSetupMode || !biometricEnabled || activity == null) {
            false
        } else {
            val mgr = BiometricManager.from(context)
            mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    val promptTitle = stringResource(R.string.pin_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.pin_biometric_prompt_subtitle)
    val promptNegative = stringResource(R.string.pin_biometric_negative)

    val launchBiometric: () -> Unit = {
        if (activity != null && canUseBiometric) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onBiometricSuccess()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setNegativeButtonText(promptNegative)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
        }
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) {
            launchBiometric()
        }
    }

    val lengthErrorText = stringResource(R.string.pin_length_error)
    val mismatchErrorText = stringResource(R.string.pin_mismatch_error)
    val invalidTemplate = stringResource(R.string.pin_invalid_error)

    fun submitPin() {
        if (pinBuffer.length !in 4..8) {
            errorMessage = lengthErrorText
            return
        }
        if (isSetupMode) {
            if (firstSetupPin == null) {
                firstSetupPin = pinBuffer
                pinBuffer = ""
                errorMessage = null
            } else if (firstSetupPin == pinBuffer) {
                onPinCreated(pinBuffer)
            } else {
                errorMessage = mismatchErrorText
                firstSetupPin = null
                pinBuffer = ""
            }
        } else {
            val ok = onPinAttempt(pinBuffer)
            if (!ok) {
                val remaining = (lockState.maxAttempts - (lockState.failedAttempts + 1)).coerceAtLeast(1)
                errorMessage = String.format(invalidTemplate, remaining)
                pinBuffer = ""
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Offline Security Badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SecondaryTeal.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SecondaryTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.pin_offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryTeal
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSetupMode) Icons.Default.Key else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                AnimatedContent(
                    targetState = Triple(isSetupMode, isConfirmStep, lockState.hasPinConfigured),
                    label = "pin_header"
                ) { (setup, confirm, _) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                setup && confirm -> stringResource(R.string.pin_confirm_title)
                                setup -> stringResource(R.string.pin_setup_title)
                                else -> stringResource(R.string.pin_unlock_title)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                setup && confirm -> stringResource(R.string.pin_confirm_subtitle)
                                setup -> stringResource(R.string.pin_setup_subtitle)
                                else -> stringResource(R.string.pin_unlock_subtitle)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // PIN Dots Indicator (supports 4 to 8 digits)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("pin_dots_row")
                ) {
                    val slotCount = maxOf(4, pinBuffer.length).coerceAtMost(8)
                    for (i in 0 until slotCount) {
                        val filled = i < pinBuffer.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pin_error_text")
                    )
                }
            }

            // Custom Numeric Keypad
            Column(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { digit ->
                            KeypadDigitButton(
                                digit = digit,
                                onClick = {
                                    if (pinBuffer.length < 8) {
                                        pinBuffer += digit
                                        errorMessage = null
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action: Biometric or Clear
                    if (canUseBiometric) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(SecondaryTeal.copy(alpha = 0.14f))
                                .clickable { launchBiometric() }
                                .testTag("pin_biometric_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = stringResource(R.string.pin_use_biometric),
                                tint = SecondaryTeal,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        KeypadActionButton(
                            onClick = {
                                pinBuffer = ""
                                errorMessage = null
                            },
                            tag = "pin_clear_button"
                        ) {
                            Text(
                                text = stringResource(R.string.pin_clear),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    KeypadDigitButton(
                        digit = "0",
                        onClick = {
                            if (pinBuffer.length < 8) {
                                pinBuffer += "0"
                                errorMessage = null
                            }
                        }
                    )

                    KeypadActionButton(
                        onClick = {
                            if (pinBuffer.isNotEmpty()) {
                                pinBuffer = pinBuffer.dropLast(1)
                            }
                        },
                        tag = "pin_backspace_button"
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.pin_backspace)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Button(
                    onClick = { submitPin() },
                    enabled = pinBuffer.length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pin_submit_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (isSetupMode) stringResource(R.string.pin_continue)
                        else stringResource(R.string.pin_unlock_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (canUseBiometric) {
                    OutlinedButton(
                        onClick = launchBiometric,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.pin_use_biometric))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("pin_digit_$digit"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeypadActionButton(
    onClick: () -> Unit,
    tag: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
