package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyStandards
import com.example.engine.BittingCalculator
import com.example.ui.StudioMode
import com.example.ui.StudioUiState
import com.example.ui.StudioViewModel
import com.example.ui.components.BittingCadCanvas
import com.example.ui.components.Key3DPreviewCanvas
import com.example.ui.components.PerspectiveCornerCanvas
import com.example.ui.components.ScreenRulerCanvas
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    state: StudioUiState,
    settings: AppSettings,
    viewModel: StudioViewModel,
    snackbarHostState: SnackbarHostState,
    onSavedToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSaveSheet by remember { mutableStateOf(false) }
    var showStandardMenu by remember { mutableStateOf(false) }
    var autoRotate3D by remember { mutableStateOf(true) }
    var wireframe3D by remember { mutableStateOf(false) }
    var bittingEditText by remember(state.bittingResult.bittingCode) {
        mutableStateOf(state.bittingResult.bittingCode)
    }

    val savedToastMsg = stringResource(R.string.studio_saved_toast)

    // Zero-Permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                viewModel.setSourceBitmap(bmp)
            }
        }
    }

    // Runtime Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setShowCameraPreview(true)
        }
    }

    if (state.showCameraPreview) {
        CameraCaptureOverlay(
            onCaptured = { bmp -> viewModel.setSourceBitmap(bmp) },
            onClose = { viewModel.setShowCameraPreview(false) },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Save Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.studio_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { showSaveSheet = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("studio_save_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.studio_save_key))
            }
        }

        // Studio Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                selected = state.mode == StudioMode.BITTING_CAD,
                label = stringResource(R.string.studio_mode_bitting),
                icon = Icons.Default.Tune,
                onClick = { viewModel.setMode(StudioMode.BITTING_CAD) },
                tag = "studio_mode_bitting"
            )
            ModeChip(
                selected = state.mode == StudioMode.SCREEN_RULER,
                label = stringResource(R.string.studio_mode_ruler),
                icon = Icons.Default.Straighten,
                onClick = { viewModel.setMode(StudioMode.SCREEN_RULER) },
                tag = "studio_mode_ruler"
            )
            ModeChip(
                selected = state.mode == StudioMode.PHOTO_CALIBRATE,
                label = stringResource(R.string.studio_mode_photo),
                icon = Icons.Default.CameraAlt,
                onClick = { viewModel.setMode(StudioMode.PHOTO_CALIBRATE) },
                tag = "studio_mode_photo"
            )
            ModeChip(
                selected = state.mode == StudioMode.PREVIEW_3D,
                label = stringResource(R.string.studio_mode_3d),
                icon = Icons.Default.ViewInAr,
                onClick = { viewModel.setMode(StudioMode.PREVIEW_3D) },
                tag = "studio_mode_3d"
            )
            FilterChip(
                selected = state.showLongitudinalPad,
                onClick = { viewModel.setShowLongitudinalPad(true) },
                leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(R.string.studio_longitudinal_btn)) },
                modifier = Modifier.testTag("studio_open_longitudinal_button")
            )
        }

        // Live Bitting, Standard & Blade Sides (Single vs Double-Sided) Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { showStandardMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("studio_standard_selector")
                        ) {
                            Text(state.standard.id, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("(${state.pinCount}P)", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(
                            expanded = showStandardMenu,
                            onDismissRequest = { showStandardMenu = false }
                        ) {
                            KeyStandards.ALL.forEach { spec ->
                                DropdownMenuItem(
                                    text = { Text(spec.displayName) },
                                    onClick = {
                                        viewModel.selectStandard(spec)
                                        showStandardMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.studio_bitting_code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.bittingResult.bittingCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("studio_bitting_readout")
                        )
                    }
                }

                // Single-Sided vs Double-Sided Blade Toggle + Quick Longitudinal Launch
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
                        modifier = Modifier.testTag("studio_single_sided_chip")
                    )
                    FilterChip(
                        selected = state.isDoubleSided,
                        onClick = { viewModel.setDoubleSided(true) },
                        leadingIcon = {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.studio_double_sided)) },
                        modifier = Modifier.testTag("studio_double_sided_chip")
                    )
                    FilledTonalButton(
                        onClick = { viewModel.setShowLongitudinalPad(true) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("studio_longitudinal_cta")
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.studio_longitudinal_btn), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Mode-Specific Interactive Viewport
        when (state.mode) {
            StudioMode.PHOTO_CALIBRATE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.setShowCameraPreview(true)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.studio_capture_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.studio_pick_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (state.sourceBitmap != null) {
                    Text(
                        text = stringResource(R.string.studio_drag_corners_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        PerspectiveCornerCanvas(
                            bitmap = state.sourceBitmap,
                            corners = state.cardCorners,
                            onCornerMoved = { idx, pt -> viewModel.updateCorner(idx, pt) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetCorners() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.studio_reset_corners))
                        }
                        Button(
                            onClick = { viewModel.rectifyReferenceCard() },
                            enabled = !state.isRectifying,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.isRectifying) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.studio_rectify_card), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (state.rectifiedBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusSuccess.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                                Text(
                                    text = stringResource(R.string.studio_card_calibrated),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusSuccess
                                )
                            }
                        }
                        Image(
                            bitmap = state.rectifiedBitmap.asImageBitmap(),
                            contentDescription = "Rectified orthographic card",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.setMode(StudioMode.BITTING_CAD) }
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.studio_drag_corners_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            StudioMode.SCREEN_RULER -> {
                Text(
                    text = stringResource(R.string.studio_ruler_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    ScreenRulerCanvas(
                        screenDpiOverride = settings.screenDpiOverride,
                        bladeLengthMm = state.bladeLengthMm,
                        bladeWidthMm = state.bladeWidthMm,
                        spacingsMm = state.spacingsMm,
                        depthsMm = state.depthsMm,
                        selectedPinIndex = state.selectedPinIndex,
                        isDoubleSided = state.isDoubleSided,
                        onPinDrag = { idx, depth -> viewModel.updatePinDepth(idx, depth) },
                        onBladeLengthChange = { len -> viewModel.updateBladeDimensions(lengthMm = len) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            StudioMode.BITTING_CAD -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(245.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    BittingCadCanvas(
                        bladeLengthMm = state.bladeLengthMm,
                        bladeWidthMm = state.bladeWidthMm,
                        spacingsMm = state.spacingsMm,
                        depthsMm = state.depthsMm,
                        selectedPinIndex = state.selectedPinIndex,
                        isDoubleSided = state.isDoubleSided,
                        rectifiedBitmap = state.rectifiedBitmap,
                        onPinDrag = { idx, depth, spacing ->
                            viewModel.updatePinDepth(idx, depth)
                            viewModel.updatePinSpacing(idx, spacing)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            StudioMode.PREVIEW_3D -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(265.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Key3DPreviewCanvas(
                        mesh = state.mesh3D,
                        wireframe = wireframe3D,
                        autoRotate = autoRotate3D,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = stringResource(R.string.studio_3d_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(checked = autoRotate3D, onCheckedChange = { autoRotate3D = it })
                        Text(stringResource(R.string.studio_3d_turntable), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(checked = wireframe3D, onCheckedChange = { wireframe3D = it })
                        Text(stringResource(R.string.studio_3d_wireframe), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Precision Pin Selector & 0.05mm Steppers (shared for CAD & Screen Ruler)
        if (state.mode == StudioMode.BITTING_CAD || state.mode == StudioMode.SCREEN_RULER) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Direct Bitting Input & Pin Count Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = bittingEditText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(state.pinCount)
                                bittingEditText = filtered
                                if (filtered.length == state.pinCount) {
                                    viewModel.applyBittingCodeInput(filtered)
                                }
                            },
                            label = { Text(stringResource(R.string.studio_bitting_code)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_bitting_input")
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.studio_pin_count), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setPinCount(state.pinCount - 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease pins")
                                }
                                Text("${state.pinCount}", style = MaterialTheme.typography.titleMedium)
                                IconButton(
                                    onClick = { viewModel.setPinCount(state.pinCount + 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase pins")
                                }
                            }
                        }
                    }

                    // Pin Selector Chips with Prev/Next Arrows
                    val activeIdx = state.selectedPinIndex.coerceIn(0, state.pinCount - 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.selectPin((activeIdx - 1).coerceAtLeast(0)) },
                            enabled = activeIdx > 0,
                            modifier = Modifier.size(48.dp)
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
                            val decodedPins = state.bittingResult.pins
                            for (idx in 0 until state.pinCount) {
                                val pinInfo = decodedPins.getOrNull(idx)
                                val depthVal = state.depthsMm.getOrElse(idx) { 0f }
                                val labelDigit = if (depthVal <= 0.08f) "0" else "${pinInfo?.codeDigit ?: "-"}"
                                FilterChip(
                                    selected = state.selectedPinIndex == idx,
                                    onClick = { viewModel.selectPin(idx) },
                                    label = {
                                        Text("P${idx + 1}: $labelDigit")
                                    }
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.selectPin((activeIdx + 1).coerceAtMost(state.pinCount - 1)) },
                            enabled = activeIdx < state.pinCount - 1,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.studio_arrow_next_pin)
                            )
                        }
                    }

                    // Active Pin Depth + Level Up/Down Arrows + 0..9 Quick Level Grid
                    val activeDepth = state.depthsMm.getOrElse(activeIdx) { 0f }
                    val activeSpacing = state.spacingsMm.getOrElse(activeIdx) { 5f }
                    val activeDecoded = state.bittingResult.pins.getOrNull(activeIdx)
                    val nearestLevel = viewModel.nearestQuickLevel(activeDepth)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.stepPinLevel(activeIdx, -1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_arrow_up"),
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
                        FilledTonalButton(
                            onClick = { viewModel.stepPinLevel(activeIdx, +1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_arrow_down"),
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

                    QuickCutLevelBar(
                        selectedLevel = nearestLevel,
                        isFlat = activeDepth <= 0.08f,
                        onSelectLevel = { lvl -> viewModel.setPinLevelCode(activeIdx, lvl) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pin #${activeIdx + 1} Depth",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${BittingCalculator.formatDimension(activeDepth, settings.preferredUnit)} " +
                                    "(Δ ${String.format(Locale.US, "%+.2f", activeDecoded?.errorDeltaMm ?: 0f)} mm)",
                                style = MaterialTheme.typography.labelMedium,
                                color = SecondaryTeal
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.updatePinDepth(activeIdx, activeDepth - 0.05f) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("studio_depth_minus")
                            ) {
                                Text("-0.05")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.updatePinDepth(activeIdx, activeDepth + 0.05f) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("studio_depth_plus")
                            ) {
                                Text("+0.05")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pin #${activeIdx + 1} Pos",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = BittingCalculator.formatDimension(activeSpacing, settings.preferredUnit),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.updatePinSpacing(activeIdx, activeSpacing - 0.05f) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("-0.05")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.updatePinSpacing(activeIdx, activeSpacing + 0.05f) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+0.05")
                            }
                        }
                    }

                    // Blade Thickness & Length Quick Steppers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DimensionMiniCard(
                            label = stringResource(R.string.studio_blade_length),
                            value = String.format(Locale.US, "%.2f", state.bladeLengthMm),
                            onMinus = { viewModel.updateBladeDimensions(lengthMm = state.bladeLengthMm - 0.5f) },
                            onPlus = { viewModel.updateBladeDimensions(lengthMm = state.bladeLengthMm + 0.5f) },
                            modifier = Modifier.weight(1f)
                        )
                        DimensionMiniCard(
                            label = stringResource(R.string.studio_blade_thickness),
                            value = String.format(Locale.US, "%.2f", state.bladeThicknessMm),
                            onMinus = { viewModel.updateBladeDimensions(thicknessMm = state.bladeThicknessMm - 0.05f) },
                            onPlus = { viewModel.updateBladeDimensions(thicknessMm = state.bladeThicknessMm + 0.05f) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Save to Vault Bottom Sheet
    if (showSaveSheet) {
        ModalBottomSheet(onDismissRequest = { showSaveSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.studio_save_key),
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = state.keyName,
                    onValueChange = { viewModel.updateMetadata(name = it) },
                    label = { Text(stringResource(R.string.studio_key_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_save_name_input")
                )

                Text(stringResource(R.string.studio_category), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyCategory.entries.forEach { cat ->
                        val catLabel = if (settings.language == AppLanguage.ARABIC) cat.labelAr else cat.labelEn
                        FilterChip(
                            selected = state.category == cat,
                            onClick = { viewModel.updateMetadata(category = cat) },
                            label = { Text(catLabel) }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { viewModel.updateMetadata(notes = it) },
                    label = { Text(stringResource(R.string.studio_key_notes)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.saveKeyToVault {
                            showSaveSheet = false
                            scope.launch {
                                snackbarHostState.showSnackbar(savedToastMsg)
                            }
                            onSavedToVault()
                        }
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("studio_confirm_save_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.action_save))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Full-Screen Longitudinal 1:1 Physical Key Pad
    if (state.showLongitudinalPad) {
        LongitudinalKeyPadDialog(
            state = state,
            settings = settings,
            viewModel = viewModel,
            onDismiss = { viewModel.setShowLongitudinalPad(false) }
        )
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(label) },
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun DimensionMiniCard(
    label: String,
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
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onMinus, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }
                Text(value, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
