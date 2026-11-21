package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.KeyBowStyle
import com.example.domain.model.KeyTipStyle
import com.example.engine.BittingCalculator
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.theme.SecondaryTeal
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Arrow-only D-Pad (Vertical, Horizontal & 4 Diagonal Slant Directions) +
 * Pin Selector, Slope/Flat Width Shaping, and 0..9 Quick Levels.
 * Eliminates all accidental touch-dragging on the key canvas.
 */
@Composable
fun DirectionalToothControlPad(
    state: StudioUiState,
    settings: AppSettings,
    viewModel: StudioViewModel,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val activeIdx = state.selectedPinIndex.coerceIn(0, (state.pinCount - 1).coerceAtLeast(0))
    val activeDepth = state.depthsMm.getOrElse(activeIdx) { 0f }
    val activeSpacing = state.spacingsMm.getOrElse(activeIdx) { 5f }
    val activeFlat = state.flatWidthsMm.getOrElse(activeIdx) { 1.15f }
    val activeLeftSlope = state.leftSlopesDeg.getOrElse(activeIdx) { 45f }
    val activeRightSlope = state.rightSlopesDeg.getOrElse(activeIdx) { 45f }
    val nearestLevel = viewModel.nearestQuickLevel(activeDepth)
    val step = state.stepPrecisionMm

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        // Touch-drag locked indicator badge
        Surface(
            color = SecondaryTeal.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SecondaryTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.studio_touch_locked_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryTeal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Pin Selector Row with Prev / Next Arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { viewModel.selectPin((activeIdx - 1).coerceAtLeast(0)) },
                enabled = activeIdx > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_prev_pin_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.studio_arrow_prev_pin)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (idx in 0 until state.pinCount) {
                    val depthVal = state.depthsMm.getOrElse(idx) { 0f }
                    val digit = state.bittingResult.pins.getOrNull(idx)?.codeDigit ?: 0
                    val labelDigit = if (depthVal <= 0.08f) "0" else "$digit"
                    FilterChip(
                        selected = idx == activeIdx,
                        onClick = { viewModel.selectPin(idx) },
                        label = { Text("P${idx + 1}:$labelDigit") },
                        modifier = Modifier.testTag("${tagPrefix}_pin_chip_$idx")
                    )
                }
            }

            IconButton(
                onClick = { viewModel.selectPin((activeIdx + 1).coerceAtMost(state.pinCount - 1)) },
                enabled = activeIdx < state.pinCount - 1,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_next_pin_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.studio_arrow_next_pin)
                )
            }
        }

        // Arrow Step Precision Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.studio_step_precision),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.10f, 0.25f, 0.50f).forEach { s ->
                    FilterChip(
                        selected = kotlin.math.abs(step - s) < 0.02f,
                        onClick = { viewModel.setStepPrecision(s) },
                        label = { Text(String.format(Locale.US, "%.2fmm", s)) }
                    )
                }
            }
        }

        // 8-Way Directional & Diagonal Slant D-Pad
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Diagonal NW | Up (Shallower) | Diagonal NE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RepeatableArrowButton(
                        icon = Icons.Default.NorthWest,
                        label = "↖ ${activeLeftSlope.roundToInt()}°",
                        contentDesc = stringResource(R.string.studio_left_slope),
                        onStep = {
                            viewModel.adjustPinShapeSlope(
                                index = activeIdx,
                                leftSlopeDeltaDeg = -4f,
                                depthDeltaMm = -step * 0.5f
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_diag_nw")
                    )
                    RepeatableArrowButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        label = stringResource(R.string.studio_arrow_shallower),
                        contentDesc = stringResource(R.string.studio_arrow_shallower),
                        onStep = { viewModel.movePinByArrow(activeIdx, dxMm = 0f, dyDepthMm = -step) },
                        isPrimary = true,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_up")
                    )
                    RepeatableArrowButton(
                        icon = Icons.Default.NorthEast,
                        label = "${activeRightSlope.roundToInt()}° ↗",
                        contentDesc = stringResource(R.string.studio_right_slope),
                        onStep = {
                            viewModel.adjustPinShapeSlope(
                                index = activeIdx,
                                rightSlopeDeltaDeg = -4f,
                                depthDeltaMm = -step * 0.5f
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_diag_ne")
                    )
                }

                // Row 2: Move Left | Center Telemetry | Move Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RepeatableArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        label = stringResource(R.string.studio_arrow_left),
                        contentDesc = stringResource(R.string.studio_arrow_left),
                        onStep = { viewModel.movePinByArrow(activeIdx, dxMm = -step, dyDepthMm = 0f) },
                        isPrimary = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_left")
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1.25f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "P${activeIdx + 1} • Lvl $nearestLevel",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "D:${BittingCalculator.formatDimension(activeDepth, settings.preferredUnit)} X:${String.format(Locale.US, "%.2f", activeSpacing)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    RepeatableArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        label = stringResource(R.string.studio_arrow_right),
                        contentDesc = stringResource(R.string.studio_arrow_right),
                        onStep = { viewModel.movePinByArrow(activeIdx, dxMm = +step, dyDepthMm = 0f) },
                        isPrimary = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_right")
                    )
                }

                // Row 3: Diagonal SW | Down (Deeper) | Diagonal SE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RepeatableArrowButton(
                        icon = Icons.Default.SouthWest,
                        label = "↙ +4°",
                        contentDesc = stringResource(R.string.studio_left_slope),
                        onStep = {
                            viewModel.adjustPinShapeSlope(
                                index = activeIdx,
                                leftSlopeDeltaDeg = +4f,
                                depthDeltaMm = +step * 0.5f
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_diag_sw")
                    )
                    RepeatableArrowButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        label = stringResource(R.string.studio_arrow_deeper),
                        contentDesc = stringResource(R.string.studio_arrow_deeper),
                        onStep = { viewModel.movePinByArrow(activeIdx, dxMm = 0f, dyDepthMm = +step) },
                        isPrimary = true,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_down")
                    )
                    RepeatableArrowButton(
                        icon = Icons.Default.SouthEast,
                        label = "+4° ↘",
                        contentDesc = stringResource(R.string.studio_right_slope),
                        onStep = {
                            viewModel.adjustPinShapeSlope(
                                index = activeIdx,
                                rightSlopeDeltaDeg = +4f,
                                depthDeltaMm = +step * 0.5f
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("${tagPrefix}_arrow_diag_se")
                    )
                }
            }
        }

        // Valley Flat Width & Slope Fine Shaping Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactStepperPill(
                title = stringResource(R.string.studio_flat_width),
                value = String.format(Locale.US, "%.2fmm", activeFlat),
                onMinus = { viewModel.adjustPinShapeSlope(activeIdx, flatWidthDeltaMm = -0.15f) },
                onPlus = { viewModel.adjustPinShapeSlope(activeIdx, flatWidthDeltaMm = +0.15f) },
                modifier = Modifier.weight(1f)
            )
            CompactStepperPill(
                title = stringResource(R.string.studio_left_slope),
                value = "${activeLeftSlope.roundToInt()}°",
                onMinus = { viewModel.adjustPinShapeSlope(activeIdx, leftSlopeDeltaDeg = -5f) },
                onPlus = { viewModel.adjustPinShapeSlope(activeIdx, leftSlopeDeltaDeg = +5f) },
                modifier = Modifier.weight(1f)
            )
            CompactStepperPill(
                title = stringResource(R.string.studio_right_slope),
                value = "${activeRightSlope.roundToInt()}°",
                onMinus = { viewModel.adjustPinShapeSlope(activeIdx, rightSlopeDeltaDeg = -5f) },
                onPlus = { viewModel.adjustPinShapeSlope(activeIdx, rightSlopeDeltaDeg = +5f) },
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Datum & Tooth Alignment Helpers (Copy Prev | 0 Flat | Shift All <-/->)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.copyPreviousPinDepth(activeIdx) },
                enabled = activeIdx > 0,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("${tagPrefix}_copy_prev_button")
            ) {
                Text(stringResource(R.string.studio_copy_prev), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { viewModel.setPinLevelCode(activeIdx, 0) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("${tagPrefix}_zero_flat_button")
            ) {
                Text(stringResource(R.string.studio_flat_zero), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { viewModel.nudgeAllPinsX(-step) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("${tagPrefix}_shift_all_left")
            ) {
                Text(stringResource(R.string.studio_shift_all_left), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { viewModel.nudgeAllPinsX(+step) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("${tagPrefix}_shift_all_right")
            ) {
                Text(stringResource(R.string.studio_shift_all_right), style = MaterialTheme.typography.labelSmall)
            }
        }

        // 0 (Flat) .. 9 Quick Cut Levels
        QuickCutLevelBar(
            selectedLevel = nearestLevel,
            isFlat = activeDepth <= 0.08f,
            onSelectLevel = { lvl -> viewModel.setPinLevelCode(activeIdx, lvl) },
            tagPrefix = tagPrefix
        )
    }
}

/**
 * Lock Frame Resize & Scale Card:
 * Adjusts on-screen zoom, bow/head style & scale, blade length, blade width, and blade thickness.
 */
@Composable
fun LockFrameResizeSection(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
    settings: AppSettings = AppSettings()
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.studio_frame_resize_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Bow / Head Geometry Style Selector (7 styles)
            Text(
                text = stringResource(R.string.studio_bow_style_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyBowStyle.entries.forEach { style ->
                    val label = if (settings.language == AppLanguage.ARABIC) style.labelAr else style.labelEn
                    FilterChip(
                        selected = state.bowStyle == style,
                        onClick = { viewModel.updateBowStyle(style) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("studio_bow_style_${style.name.lowercase()}")
                    )
                }
            }

            // On-screen Frame Zoom Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.studio_frame_zoom),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.updateFrameZoom(state.frameZoom - 0.10f) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("studio_frame_zoom_out")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out frame")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.resetFrameZoom() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("studio_frame_zoom_reset")
                    ) {
                        Text("${(state.frameZoom * 100).roundToInt()}%")
                    }
                    IconButton(
                        onClick = { viewModel.updateFrameZoom(state.frameZoom + 0.10f) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("studio_frame_zoom_in")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in frame")
                    }
                }
            }
            Slider(
                value = state.frameZoom,
                onValueChange = { viewModel.updateFrameZoom(it) },
                valueRange = 0.50f..2.20f
            )

            // Key Head / Bow Size Scale
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.studio_bow_scale),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "${(state.bowScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryTeal
                )
            }
            Slider(
                value = state.bowScale,
                onValueChange = { viewModel.updateBowScale(it) },
                valueRange = 0.60f..1.55f
            )

            // Blade Length, Width & Thickness Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStepperPill(
                    title = stringResource(R.string.studio_blade_length),
                    value = String.format(Locale.US, "%.1f", state.bladeLengthMm),
                    onMinus = { viewModel.updateBladeDimensions(lengthMm = state.bladeLengthMm - 0.5f) },
                    onPlus = { viewModel.updateBladeDimensions(lengthMm = state.bladeLengthMm + 0.5f) },
                    modifier = Modifier.weight(1f)
                )
                CompactStepperPill(
                    title = stringResource(R.string.studio_blade_width),
                    value = String.format(Locale.US, "%.1f", state.bladeWidthMm),
                    onMinus = { viewModel.updateBladeDimensions(widthMm = state.bladeWidthMm - 0.2f) },
                    onPlus = { viewModel.updateBladeDimensions(widthMm = state.bladeWidthMm + 0.2f) },
                    modifier = Modifier.weight(1f)
                )
                CompactStepperPill(
                    title = stringResource(R.string.studio_blade_thickness),
                    value = String.format(Locale.US, "%.2f", state.bladeThicknessMm),
                    onMinus = { viewModel.updateBladeDimensions(thicknessMm = state.bladeThicknessMm - 0.05f) },
                    onPlus = { viewModel.updateBladeDimensions(thicknessMm = state.bladeThicknessMm + 0.05f) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Rear Key Tip Customization Card:
 * Switch among 5 rear tip geometries, taper length, and vertical apex bias.
 */
@Composable
fun TipCustomizationSection(
    state: StudioUiState,
    settings: AppSettings,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.studio_tip_custom_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyTipStyle.entries.forEach { style ->
                    val label = if (settings.language == AppLanguage.ARABIC) style.labelAr else style.labelEn
                    FilterChip(
                        selected = state.tipStyle == style,
                        onClick = { viewModel.updateTipCustomization(style = style) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("studio_tip_style_${style.name.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStepperPill(
                    title = stringResource(R.string.studio_tip_length),
                    value = String.format(Locale.US, "%.1fmm", state.tipLengthMm),
                    onMinus = { viewModel.updateTipCustomization(lengthMm = state.tipLengthMm - 0.2f) },
                    onPlus = { viewModel.updateTipCustomization(lengthMm = state.tipLengthMm + 0.2f) },
                    modifier = Modifier.weight(1f)
                )
                CompactStepperPill(
                    title = stringResource(R.string.studio_tip_offset),
                    value = String.format(Locale.US, "%+.1fmm", state.tipOffsetMm),
                    onMinus = { viewModel.updateTipCustomization(offsetMm = state.tipOffsetMm - 0.2f) },
                    onPlus = { viewModel.updateTipCustomization(offsetMm = state.tipOffsetMm + 0.2f) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Compact Zoom Bar displayed above/below Key Canvases.
 */
@Composable
fun FrameZoomCompactBar(
    frameZoom: Float,
    onZoomChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = { onZoomChange(frameZoom - 0.10f) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(Icons.Default.ZoomOut, contentDescription = "Shrink frame", modifier = Modifier.size(16.dp))
        }
        FilledTonalButton(
            onClick = onReset,
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("${(frameZoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = { onZoomChange(frameZoom + 0.10f) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(Icons.Default.ZoomIn, contentDescription = "Enlarge frame", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RepeatableArrowButton(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val latestOnStep by rememberUpdatedState(onStep)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(360L)
            while (true) {
                latestOnStep()
                delay(95L)
            }
        }
    }

    FilledTonalButton(
        onClick = { latestOnStep() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = if (isPrimary) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        modifier = modifier
    ) {
        Icon(icon, contentDescription = contentDesc, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CompactStepperPill(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onMinus, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                IconButton(onClick = onPlus, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Reusable 0 (Flat) .. 9 quick cut level grid.
 */
@Composable
fun QuickCutLevelBar(
    selectedLevel: Int,
    isFlat: Boolean,
    onSelectLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "studio"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.studio_quick_levels),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (lvl in 0..4) {
                val active = if (lvl == 0) isFlat else (!isFlat && selectedLevel == lvl)
                QuickLevelButton(
                    level = lvl,
                    selected = active,
                    label = if (lvl == 0) stringResource(R.string.studio_flat_zero) else "$lvl",
                    onClick = { onSelectLevel(lvl) },
                    tagPrefix = tagPrefix,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (lvl in 5..9) {
                val active = !isFlat && selectedLevel == lvl
                QuickLevelButton(
                    level = lvl,
                    selected = active,
                    label = "$lvl",
                    onClick = { onSelectLevel(lvl) },
                    tagPrefix = tagPrefix,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickLevelButton(
    level: Int,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .testTag("${tagPrefix}_level_button_$level")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
