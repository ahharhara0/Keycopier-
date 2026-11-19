package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.model.AppSettings
import com.example.engine.BittingCalculator
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.LongitudinalKeyPadCanvas
import com.example.ui.theme.SecondaryTeal
import java.util.Locale

@Composable
fun LongitudinalKeyPadDialog(
    state: StudioUiState,
    settings: AppSettings,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit
) {
    var lockSpacing by remember { mutableStateOf(true) }
    val activeIdx = state.selectedPinIndex.coerceIn(0, (state.pinCount - 1).coerceAtLeast(0))
    val activeDepth = state.depthsMm.getOrElse(activeIdx) { 0f }
    val activeSpacing = state.spacingsMm.getOrElse(activeIdx) { 5f }
    val nearestLevel = viewModel.nearestQuickLevel(activeDepth)
    val bittingDigits = state.bittingResult.pins.map { it.codeDigit }

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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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

                        // Single/Double Sided + Spacing Lock Row
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
                                selected = lockSpacing,
                                onClick = { lockSpacing = !lockSpacing },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (lockSpacing) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(if (lockSpacing) "Pos Locked" else "Pos Free")
                                }
                            )
                        }
                    }
                }

                // Vertical 1:1 Key Placement Area
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
                        lockSpacingOnDrag = lockSpacing,
                        onSelectPin = { idx -> viewModel.selectPin(idx) },
                        onPinDrag = { idx, newDepth, newSpacing ->
                            viewModel.updatePinDepth(idx, newDepth)
                            if (newSpacing != null) {
                                viewModel.updatePinSpacing(idx, newSpacing)
                            }
                        },
                        onBladeLengthChange = { len -> viewModel.updateBladeDimensions(lengthMm = len) },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("longitudinal_pad_canvas")
                    )

                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.studio_longitudinal_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Bottom Control Dock: Pin Selector + Arrows + 0..9 Quick Levels
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pin Tabs + Prev/Next Arrows
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.selectPin((activeIdx - 1).coerceAtLeast(0)) },
                                enabled = activeIdx > 0,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("longitudinal_prev_pin_button")
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
                                    val lvl = if (depthVal <= 0.08f) 0 else state.bittingResult.pins.getOrNull(idx)?.codeDigit ?: 0
                                    FilterChip(
                                        selected = idx == activeIdx,
                                        onClick = { viewModel.selectPin(idx) },
                                        label = { Text("P${idx + 1}:$lvl") }
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.selectPin((activeIdx + 1).coerceAtMost(state.pinCount - 1)) },
                                enabled = activeIdx < state.pinCount - 1,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("longitudinal_next_pin_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.studio_arrow_next_pin)
                                )
                            }
                        }

                        // Up/Down Cut Depth Arrows + Live Depth/Position Readout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.stepPinLevel(activeIdx, -1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("longitudinal_arrow_up"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.studio_arrow_shallower)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.studio_arrow_shallower),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "P${activeIdx + 1} • Lvl $nearestLevel",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${BittingCalculator.formatDimension(activeDepth, settings.preferredUnit)} / ${String.format(Locale.US, "%.1f", activeSpacing)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryTeal
                                )
                            }

                            FilledTonalButton(
                                onClick = { viewModel.stepPinLevel(activeIdx, +1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("longitudinal_arrow_down"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.studio_arrow_deeper)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.studio_arrow_deeper),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // 0 (Flat) .. 9 Quick Cut Level Number Grid
                        QuickCutLevelBar(
                            selectedLevel = nearestLevel,
                            isFlat = activeDepth <= 0.08f,
                            onSelectLevel = { lvl -> viewModel.setPinLevelCode(activeIdx, lvl) },
                            tagPrefix = "longitudinal"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable 0 (Flat) .. 9 quick cut level grid + fine nudge controls.
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
