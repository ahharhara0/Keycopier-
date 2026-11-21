package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.model.AppSettings
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.DirectionalToothControlPad
import com.example.ui.components.FrameZoomCompactBar
import com.example.ui.components.LockFrameResizeSection
import com.example.ui.components.LongitudinalKeyPadCanvas
import com.example.ui.components.TipCustomizationSection

@Composable
fun LongitudinalKeyPadDialog(
    state: StudioUiState,
    settings: AppSettings,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null
) {
    var activeDockTab by remember { mutableIntStateOf(0) } // 0 = Teeth Arrows D-Pad, 1 = Frame & Tip
    val activeIdx = state.selectedPinIndex.coerceIn(0, (state.pinCount - 1).coerceAtLeast(0))
    val bittingDigits = state.bittingResult.pins.map { it.codeDigit }
    val context = LocalContext.current

    DisposableEffect(state.highContrastGlassMode, context) {
        val window = (context as? Activity)?.window
        val origBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window != null && state.highContrastGlassMode) {
            val attrs = window.attributes
            attrs.screenBrightness = 1.0f
            window.attributes = attrs
        }
        onDispose {
            if (window != null && state.highContrastGlassMode) {
                val attrs = window.attributes
                attrs.screenBrightness = origBrightness
                window.attributes = attrs
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Toolbar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("longitudinal_close_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_done))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.studio_longitudinal_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${state.standard.id} • ${state.bittingResult.bittingCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("longitudinal_done_button")
                            ) {
                                Text(stringResource(R.string.action_done))
                            }
                        }

                        // Blade Sides + Frame Zoom + Bottom Dock Mode Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !state.isDoubleSided,
                                onClick = { viewModel.setDoubleSided(false) },
                                label = { Text(stringResource(R.string.studio_single_sided)) },
                                modifier = Modifier.testTag("longitudinal_single_sided_chip")
                            )
                            FilterChip(
                                selected = state.isDoubleSided,
                                onClick = { viewModel.setDoubleSided(true) },
                                leadingIcon = {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(stringResource(R.string.studio_double_sided)) },
                                modifier = Modifier.testTag("longitudinal_double_sided_chip")
                            )
                            FilterChip(
                                selected = activeDockTab == 0,
                                onClick = { activeDockTab = 0 },
                                leadingIcon = {
                                    Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(stringResource(R.string.studio_tab_dpad)) }
                            )
                            FilterChip(
                                selected = activeDockTab == 1,
                                onClick = { activeDockTab = 1 },
                                leadingIcon = {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(stringResource(R.string.studio_tab_frame_tip)) }
                            )
                            FilterChip(
                                selected = state.highContrastGlassMode,
                                onClick = { viewModel.toggleHighContrastGlassMode() },
                                leadingIcon = {
                                    Icon(Icons.Default.BrightnessHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(stringResource(R.string.studio_glass_mode_btn)) },
                                modifier = Modifier.testTag("longitudinal_glass_mode_chip")
                            )
                            if (onOpenCatalog != null) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onDismiss()
                                        onOpenCatalog()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text(stringResource(R.string.studio_catalog_btn)) },
                                    modifier = Modifier.testTag("longitudinal_catalog_chip")
                                )
                            }
                        }
                    }
                }

                // Vertical 1:1 Key Placement Area (Zero Touch-Drag Interference)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LongitudinalKeyPadCanvas(
                        screenDpiOverride = settings.screenDpiOverride,
                        bladeLengthMm = state.bladeLengthMm,
                        bladeWidthMm = state.bladeWidthMm,
                        spacingsMm = state.spacingsMm,
                        depthsMm = state.depthsMm,
                        bittingDigits = bittingDigits,
                        selectedPinIndex = activeIdx,
                        isDoubleSided = state.isDoubleSided,
                        flatWidthsMm = state.flatWidthsMm,
                        leftSlopesDeg = state.leftSlopesDeg,
                        rightSlopesDeg = state.rightSlopesDeg,
                        tipStyle = state.tipStyle,
                        tipLengthMm = state.tipLengthMm,
                        tipOffsetMm = state.tipOffsetMm,
                        bowStyle = state.bowStyle,
                        bowScale = state.bowScale,
                        frameZoom = state.frameZoom,
                        customContourMm = state.customContourMm,
                        useExactPhotoContour = state.useExactPhotoContour,
                        highContrastGlassMode = state.highContrastGlassMode,
                        onSelectPin = { idx -> viewModel.selectPin(idx) },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("longitudinal_pad_canvas")
                    )

                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.studio_longitudinal_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        FrameZoomCompactBar(
                            frameZoom = state.frameZoom,
                            onZoomChange = { viewModel.updateFrameZoom(it) },
                            onReset = { viewModel.resetFrameZoom() },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                // Bottom Control Dock: Arrow-Only 8-way D-Pad + 0..9 Levels OR Frame & Tip Customizer
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeDockTab == 0) {
                            DirectionalToothControlPad(
                                state = state,
                                settings = settings,
                                viewModel = viewModel,
                                tagPrefix = "longitudinal",
                                compact = true
                            )
                        } else {
                            LockFrameResizeSection(
                                state = state,
                                viewModel = viewModel,
                                settings = settings
                            )
                            TipCustomizationSection(
                                state = state,
                                settings = settings,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
