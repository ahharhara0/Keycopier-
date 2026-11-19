package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.KeyVaultRepository
import com.example.domain.model.AttachmentType
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandardSpec
import com.example.domain.model.KeyStandards
import com.example.domain.model.KeyTipStyle
import com.example.domain.model.Point2D
import com.example.engine.BittingCalculator
import com.example.engine.BittingDecodeResult
import com.example.engine.KeyPhotoAnalyzer
import com.example.engine.PerspectiveCalibrator
import com.example.export.Mesh3D
import com.example.export.StlMeshGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StudioMode {
    PHOTO_CALIBRATE,
    SCREEN_RULER,
    BITTING_CAD,
    PREVIEW_3D
}

data class StudioUiState(
    val editingId: Long = 0L,
    val mode: StudioMode = StudioMode.BITTING_CAD,
    val keyName: String = "Front Door Key",
    val category: KeyCategory = KeyCategory.HOME,
    val notes: String = "",
    val tags: String = "",
    val standard: KeyStandardSpec = KeyStandards.ALL.first(),
    val pinCount: Int = 5,
    val bladeLengthMm: Float = 28.50f,
    val bladeWidthMm: Float = 8.46f,
    val bladeThicknessMm: Float = 2.03f,
    val spacingsMm: List<Float> = KeyStandards.defaultSpacings(KeyStandards.ALL.first(), 5),
    val depthsMm: List<Float> = KeyStandards.defaultDepths(KeyStandards.ALL.first(), 5),
    val flatWidthsMm: List<Float> = List(5) { 1.15f },
    val leftSlopesDeg: List<Float> = List(5) { 45f },
    val rightSlopesDeg: List<Float> = List(5) { 45f },
    val isDoubleSided: Boolean = false,
    val tipStyle: KeyTipStyle = KeyTipStyle.STANDARD_TAPER,
    val tipLengthMm: Float = 2.6f,
    val tipOffsetMm: Float = 0f,
    val bowScale: Float = 1.0f,
    val frameZoom: Float = 1.0f,
    val stepPrecisionMm: Float = 0.25f,
    val customContourMm: List<Point2D> = emptyList(),
    val useExactPhotoContour: Boolean = false,
    val showLongitudinalPad: Boolean = false,
    val selectedPinIndex: Int = 0,
    val bittingResult: BittingDecodeResult = BittingCalculator.decode(
        KeyStandards.ALL.first(),
        KeyStandards.defaultDepths(KeyStandards.ALL.first(), 5),
        KeyStandards.defaultSpacings(KeyStandards.ALL.first(), 5),
        8.46f
    ),
    val sourceBitmap: Bitmap? = null,
    val rectifiedBitmap: Bitmap? = null,
    val photoOverlayBitmap: Bitmap? = null,
    val photoConfidencePercent: Int = 0,
    val isAnalyzingPhoto: Boolean = false,
    val cardCorners: List<Point2D> = defaultCorners(),
    val isRectifying: Boolean = false,
    val showCameraPreview: Boolean = false,
    val mesh3D: Mesh3D? = null,
    val isSaving: Boolean = false
) {
    fun toKeyRecord(): KeyRecord = KeyRecord(
        id = editingId,
        name = keyName.ifBlank { "Untitled Key" },
        category = category,
        keyStandardId = standard.id,
        bittingCode = bittingResult.bittingCode,
        bladeLengthMm = bladeLengthMm,
        bladeWidthMm = bladeWidthMm,
        bladeThicknessMm = bladeThicknessMm,
        pinCount = pinCount,
        depthsMm = depthsMm,
        spacingsMm = spacingsMm,
        isDoubleSided = isDoubleSided,
        flatWidthsMm = flatWidthsMm,
        leftSlopesDeg = leftSlopesDeg,
        rightSlopesDeg = rightSlopesDeg,
        tipStyle = tipStyle,
        tipLengthMm = tipLengthMm,
        tipOffsetMm = tipOffsetMm,
        bowScale = bowScale,
        customContourMm = customContourMm,
        useExactPhotoContour = useExactPhotoContour,
        notes = notes,
        tags = tags
    )

    companion object {
        fun defaultCorners(): List<Point2D> = listOf(
            Point2D(0.14f, 0.18f),
            Point2D(0.86f, 0.18f),
            Point2D(0.86f, 0.82f),
            Point2D(0.14f, 0.82f)
        )
    }
}

class StudioViewModel(
    private val repository: KeyVaultRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    init {
        rebuildMesh()
    }

    fun setMode(mode: StudioMode) {
        _state.value = _state.value.copy(mode = mode)
        if (mode == StudioMode.PREVIEW_3D) rebuildMesh()
    }

    fun loadRecordForEditing(record: KeyRecord) {
        val spec = KeyStandards.getById(record.keyStandardId)
        val result = BittingCalculator.decode(spec, record.depthsMm, record.spacingsMm, record.bladeWidthMm)
        val n = record.pinCount
        _state.value = StudioUiState(
            editingId = record.id,
            mode = StudioMode.BITTING_CAD,
            keyName = record.name,
            category = record.category,
            notes = record.notes,
            tags = record.tags,
            standard = spec,
            pinCount = n,
            bladeLengthMm = record.bladeLengthMm,
            bladeWidthMm = record.bladeWidthMm,
            bladeThicknessMm = record.bladeThicknessMm,
            spacingsMm = record.spacingsMm,
            depthsMm = record.depthsMm,
            flatWidthsMm = normalizeList(record.flatWidthsMm, n, 1.15f),
            leftSlopesDeg = normalizeList(record.leftSlopesDeg, n, 45f),
            rightSlopesDeg = normalizeList(record.rightSlopesDeg, n, 45f),
            isDoubleSided = record.isDoubleSided,
            tipStyle = record.tipStyle,
            tipLengthMm = record.tipLengthMm,
            tipOffsetMm = record.tipOffsetMm,
            bowScale = record.bowScale,
            customContourMm = record.customContourMm,
            useExactPhotoContour = record.useExactPhotoContour,
            bittingResult = result
        )
        rebuildMesh()
    }

    fun startNewKey(initialMode: StudioMode = StudioMode.BITTING_CAD) {
        val spec = KeyStandards.ALL.first()
        val spacings = KeyStandards.defaultSpacings(spec, spec.pinCount)
        val depths = KeyStandards.defaultDepths(spec, spec.pinCount)
        _state.value = StudioUiState(
            mode = initialMode,
            standard = spec,
            pinCount = spec.pinCount,
            bladeLengthMm = spec.bladeLengthMm,
            bladeWidthMm = spec.bladeWidthMm,
            bladeThicknessMm = spec.bladeThicknessMm,
            spacingsMm = spacings,
            depthsMm = depths,
            flatWidthsMm = List(spec.pinCount) { 1.15f },
            leftSlopesDeg = List(spec.pinCount) { 45f },
            rightSlopesDeg = List(spec.pinCount) { 45f },
            bittingResult = BittingCalculator.decode(spec, depths, spacings, spec.bladeWidthMm)
        )
        rebuildMesh()
    }

    fun selectStandard(spec: KeyStandardSpec) {
        val pinCount = spec.pinCount
        val spacings = KeyStandards.defaultSpacings(spec, pinCount)
        val depths = KeyStandards.defaultDepths(spec, pinCount)
        updateGeometry(
            standard = spec,
            pinCount = pinCount,
            bladeLengthMm = spec.bladeLengthMm,
            bladeWidthMm = spec.bladeWidthMm,
            bladeThicknessMm = spec.bladeThicknessMm,
            spacingsMm = spacings,
            depthsMm = depths,
            flatWidthsMm = List(pinCount) { 1.15f },
            leftSlopesDeg = List(pinCount) { 45f },
            rightSlopesDeg = List(pinCount) { 45f },
            useExactPhotoContour = false
        )
    }

    fun setPinCount(newCount: Int) {
        val clamped = newCount.coerceIn(3, 8)
        val cur = _state.value
        val spec = cur.standard
        val spacings = List(clamped) { idx ->
            cur.spacingsMm.getOrElse(idx) {
                spec.shoulderToFirstPinMm + idx * spec.pinSpacingMm
            }
        }
        val depths = List(clamped) { idx ->
            cur.depthsMm.getOrElse(idx) { spec.cutDepthForCode(spec.minCode + 2) }
        }
        val flatWidths = normalizeList(cur.flatWidthsMm, clamped, 1.15f)
        val leftSlopes = normalizeList(cur.leftSlopesDeg, clamped, 45f)
        val rightSlopes = normalizeList(cur.rightSlopesDeg, clamped, 45f)
        val minBladeLen = (spacings.lastOrNull() ?: 20f) + 4.0f
        updateGeometry(
            pinCount = clamped,
            bladeLengthMm = maxOf(cur.bladeLengthMm, minBladeLen),
            spacingsMm = spacings,
            depthsMm = depths,
            flatWidthsMm = flatWidths,
            leftSlopesDeg = leftSlopes,
            rightSlopesDeg = rightSlopes,
            useExactPhotoContour = false,
            selectedPinIndex = cur.selectedPinIndex.coerceIn(0, clamped - 1)
        )
    }

    fun selectPin(index: Int) {
        _state.value = _state.value.copy(
            selectedPinIndex = index.coerceIn(0, _state.value.pinCount - 1)
        )
    }

    fun setStepPrecision(stepMm: Float) {
        _state.value = _state.value.copy(stepPrecisionMm = stepMm.coerceIn(0.05f, 1.0f))
    }

    fun updateFrameZoom(zoom: Float) {
        _state.value = _state.value.copy(frameZoom = zoom.coerceIn(0.50f, 2.20f))
    }

    fun resetFrameZoom() {
        _state.value = _state.value.copy(frameZoom = 1.0f)
    }

    fun updateBowScale(scale: Float) {
        updateGeometry(
            bowScale = BittingCalculator.roundToPrecision(scale.coerceIn(0.60f, 1.55f), 0.05f),
            useExactPhotoContour = false
        )
    }

    fun setDoubleSided(doubleSided: Boolean) {
        val cur = _state.value
        val maxDepth = maxAllowedCutDepth(cur.bladeWidthMm, doubleSided)
        val clampedDepths = cur.depthsMm.map {
            BittingCalculator.roundToPrecision(it.coerceIn(0f, maxDepth), 0.05f)
        }
        updateGeometry(
            depthsMm = clampedDepths,
            isDoubleSided = doubleSided,
            useExactPhotoContour = false
        )
    }

    fun setShowLongitudinalPad(show: Boolean) {
        _state.value = _state.value.copy(showLongitudinalPad = show)
    }

    fun setUseExactPhotoContour(enabled: Boolean) {
        val cur = _state.value
        if (enabled && cur.customContourMm.size < 12) return
        _state.value = cur.copy(useExactPhotoContour = enabled)
        if (_state.value.mode == StudioMode.PREVIEW_3D) rebuildMesh()
    }

    fun depthForQuickLevel(level: Int): Float {
        val cur = _state.value
        val spec = cur.standard
        val maxAllowed = maxAllowedCutDepth(cur.bladeWidthMm, cur.isDoubleSided)
        val rawDepth = when {
            level <= 0 -> 0.00f
            level in spec.minCode..spec.maxCode -> spec.cutDepthForCode(level)
            else -> {
                val maxTable = spec.depthTableMm.lastOrNull() ?: maxAllowed
                (level.coerceIn(1, 9) / 9f) * maxTable
            }
        }
        return BittingCalculator.roundToPrecision(rawDepth.coerceIn(0f, maxAllowed), 0.05f)
    }

    fun nearestQuickLevel(depthMm: Float): Int {
        var bestLevel = 0
        var bestDiff = Float.MAX_VALUE
        for (lvl in 0..9) {
            val d = kotlin.math.abs(depthForQuickLevel(lvl) - depthMm)
            if (d < bestDiff) {
                bestDiff = d
                bestLevel = lvl
            }
        }
        return bestLevel
    }

    fun setPinLevelCode(index: Int, level: Int) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        val newDepth = depthForQuickLevel(level.coerceIn(0, 9))
        val updated = cur.depthsMm.toMutableList().apply { this[index] = newDepth }
        updateGeometry(depthsMm = updated, useExactPhotoContour = false, selectedPinIndex = index)
    }

    fun stepPinLevel(index: Int, delta: Int) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount || delta == 0) return
        val currentDepth = cur.depthsMm.getOrElse(index) { 0f }
        val nearest = nearestQuickLevel(currentDepth)
        val nearestDepth = depthForQuickLevel(nearest)
        val targetLevel = when {
            delta > 0 && nearestDepth > currentDepth + 0.08f -> nearest
            delta < 0 && nearestDepth < currentDepth - 0.08f -> nearest
            else -> (nearest + delta).coerceIn(0, 9)
        }
        setPinLevelCode(index, targetLevel)
    }

    /**
     * Arrow-only motion of the selected tooth point in X (spacing) and Y (cut depth).
     */
    fun movePinByArrow(index: Int, dxMm: Float, dyDepthMm: Float) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        var newSpacings = cur.spacingsMm
        var newDepths = cur.depthsMm

        if (dxMm != 0f) {
            val minS = if (index == 0) 2.2f else cur.spacingsMm[index - 1] + 1.6f
            val maxS = if (index == cur.pinCount - 1) cur.bladeLengthMm - 2.0f else cur.spacingsMm[index + 1] - 1.6f
            val clampedS = BittingCalculator.roundToPrecision(
                (cur.spacingsMm[index] + dxMm).coerceIn(minS, maxOf(minS, maxS)),
                0.05f
            )
            newSpacings = cur.spacingsMm.toMutableList().apply { this[index] = clampedS }
        }
        if (dyDepthMm != 0f) {
            val maxAllowed = maxAllowedCutDepth(cur.bladeWidthMm, cur.isDoubleSided)
            val clampedD = BittingCalculator.roundToPrecision(
                (cur.depthsMm[index] + dyDepthMm).coerceIn(0f, maxAllowed),
                0.05f
            )
            newDepths = cur.depthsMm.toMutableList().apply { this[index] = clampedD }
        }

        updateGeometry(
            spacingsMm = newSpacings,
            depthsMm = newDepths,
            useExactPhotoContour = false,
            selectedPinIndex = index
        )
    }

    /**
     * Adjusts individual tooth geometry (left/right diagonal flank slope angle & flat bottom width)
     * for sculpting exact V-cuts, wide U-cuts, or asymmetric slanted teeth.
     */
    fun adjustPinShapeSlope(
        index: Int,
        leftSlopeDeltaDeg: Float = 0f,
        rightSlopeDeltaDeg: Float = 0f,
        flatWidthDeltaMm: Float = 0f,
        depthDeltaMm: Float = 0f
    ) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return

        val flats = normalizeList(cur.flatWidthsMm, cur.pinCount, 1.15f).toMutableList()
        val lefts = normalizeList(cur.leftSlopesDeg, cur.pinCount, 45f).toMutableList()
        val rights = normalizeList(cur.rightSlopesDeg, cur.pinCount, 45f).toMutableList()
        val depths = cur.depthsMm.toMutableList()

        if (flatWidthDeltaMm != 0f) {
            flats[index] = BittingCalculator.roundToPrecision(
                (flats[index] + flatWidthDeltaMm).coerceIn(0.15f, 3.20f),
                0.05f
            )
        }
        if (leftSlopeDeltaDeg != 0f) {
            lefts[index] = (lefts[index] + leftSlopeDeltaDeg).coerceIn(20f, 84f)
        }
        if (rightSlopeDeltaDeg != 0f) {
            rights[index] = (rights[index] + rightSlopeDeltaDeg).coerceIn(20f, 84f)
        }
        if (depthDeltaMm != 0f) {
            val maxAllowed = maxAllowedCutDepth(cur.bladeWidthMm, cur.isDoubleSided)
            depths[index] = BittingCalculator.roundToPrecision(
                (depths[index] + depthDeltaMm).coerceIn(0f, maxAllowed),
                0.05f
            )
        }

        updateGeometry(
            depthsMm = depths,
            flatWidthsMm = flats,
            leftSlopesDeg = lefts,
            rightSlopesDeg = rights,
            useExactPhotoContour = false,
            selectedPinIndex = index
        )
    }

    fun updatePinDepth(index: Int, newDepthMm: Float) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        val maxAllowed = maxAllowedCutDepth(cur.bladeWidthMm, cur.isDoubleSided)
        val clamped = BittingCalculator.roundToPrecision(
            newDepthMm.coerceIn(0f, maxAllowed),
            0.05f
        )
        val updated = cur.depthsMm.toMutableList().apply { this[index] = clamped }
        updateGeometry(depthsMm = updated, useExactPhotoContour = false, selectedPinIndex = index)
    }

    fun updatePinSpacing(index: Int, newSpacingMm: Float) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        val minS = if (index == 0) 2.2f else cur.spacingsMm[index - 1] + 1.6f
        val maxS = if (index == cur.pinCount - 1) cur.bladeLengthMm - 2.0f else cur.spacingsMm[index + 1] - 1.6f
        val clamped = BittingCalculator.roundToPrecision(
            newSpacingMm.coerceIn(minS, maxOf(minS, maxS)),
            0.05f
        )
        val updated = cur.spacingsMm.toMutableList().apply { this[index] = clamped }
        updateGeometry(spacingsMm = updated, useExactPhotoContour = false, selectedPinIndex = index)
    }

    fun applyBittingCodeInput(codeText: String) {
        val cur = _state.value
        val digitsOnly = codeText.filter { it.isDigit() }.take(cur.pinCount)
        if (digitsOnly.isEmpty()) return
        val newDepths = BittingCalculator.depthsFromBittingCode(cur.standard, digitsOnly, cur.pinCount)
        updateGeometry(depthsMm = newDepths, useExactPhotoContour = false)
    }

    fun updateBladeDimensions(lengthMm: Float? = null, widthMm: Float? = null, thicknessMm: Float? = null) {
        val cur = _state.value
        val l = BittingCalculator.roundToPrecision((lengthMm ?: cur.bladeLengthMm).coerceIn(16f, 55f), 0.1f)
        val w = BittingCalculator.roundToPrecision((widthMm ?: cur.bladeWidthMm).coerceIn(5.0f, 15.0f), 0.1f)
        val t = BittingCalculator.roundToPrecision((thicknessMm ?: cur.bladeThicknessMm).coerceIn(1.2f, 4.0f), 0.05f)
        val maxAllowed = maxAllowedCutDepth(w, cur.isDoubleSided)
        val clampedDepths = cur.depthsMm.map { it.coerceIn(0f, maxAllowed) }
        updateGeometry(
            bladeLengthMm = l,
            bladeWidthMm = w,
            bladeThicknessMm = t,
            depthsMm = clampedDepths,
            useExactPhotoContour = false
        )
    }

    fun updateTipCustomization(
        style: KeyTipStyle? = null,
        lengthMm: Float? = null,
        offsetMm: Float? = null
    ) {
        val cur = _state.value
        val newStyle = style ?: cur.tipStyle
        val newLen = BittingCalculator.roundToPrecision((lengthMm ?: cur.tipLengthMm).coerceIn(0.8f, 6.5f), 0.1f)
        val newOff = BittingCalculator.roundToPrecision((offsetMm ?: cur.tipOffsetMm).coerceIn(-1.8f, 1.8f), 0.1f)
        updateGeometry(
            tipStyle = newStyle,
            tipLengthMm = newLen,
            tipOffsetMm = newOff,
            useExactPhotoContour = false
        )
    }

    fun updateMetadata(name: String? = null, category: KeyCategory? = null, notes: String? = null, tags: String? = null) {
        val cur = _state.value
        _state.value = cur.copy(
            keyName = name ?: cur.keyName,
            category = category ?: cur.category,
            notes = notes ?: cur.notes,
            tags = tags ?: cur.tags
        )
    }

    // --- Direct Photo Recognition & Optional Reference Card Calibration ---

    fun setShowCameraPreview(show: Boolean) {
        _state.value = _state.value.copy(showCameraPreview = show)
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            sourceBitmap = bitmap,
            rectifiedBitmap = null,
            photoOverlayBitmap = null,
            cardCorners = StudioUiState.defaultCorners(),
            showCameraPreview = false
        )
        analyzePhotoToKeyAnd3D(bitmap, pxPerMmHint = null)
    }

    fun updateCorner(index: Int, normalizedPoint: Point2D) {
        val corners = _state.value.cardCorners.toMutableList()
        if (index in corners.indices) {
            corners[index] = Point2D(
                normalizedPoint.x.coerceIn(0.02f, 0.98f),
                normalizedPoint.y.coerceIn(0.02f, 0.98f)
            )
            _state.value = _state.value.copy(cardCorners = corners)
        }
    }

    fun resetCorners() {
        _state.value = _state.value.copy(cardCorners = StudioUiState.defaultCorners())
    }

    fun rectifyReferenceCard() {
        val src = _state.value.sourceBitmap ?: return
        val corners = _state.value.cardCorners
        _state.value = _state.value.copy(isRectifying = true)
        viewModelScope.launch {
            val rectified = withContext(Dispatchers.Default) {
                PerspectiveCalibrator.rectifyCardImage(src, corners, pxPerMm = 16f)
            }
            _state.value = _state.value.copy(
                rectifiedBitmap = rectified,
                isRectifying = false
            )
            if (rectified != null) {
                analyzePhotoToKeyAnd3D(rectified, pxPerMmHint = 16f)
            }
        }
    }

    fun analyzePhotoToKeyAnd3D(bitmap: Bitmap? = null, pxPerMmHint: Float? = null) {
        val target = bitmap ?: _state.value.rectifiedBitmap ?: _state.value.sourceBitmap ?: return
        val hint = pxPerMmHint ?: if (_state.value.rectifiedBitmap != null) 16f else null
        val spec = _state.value.standard
        _state.value = _state.value.copy(isAnalyzingPhoto = true)
        viewModelScope.launch {
            val extraction = withContext(Dispatchers.Default) {
                KeyPhotoAnalyzer.analyzeKeyPhoto(target, spec, hint)
            }
            if (extraction.detected) {
                val decoded = BittingCalculator.decode(
                    spec,
                    extraction.depthsMm,
                    extraction.spacingsMm,
                    extraction.bladeWidthMm
                )
                _state.value = _state.value.copy(
                    pinCount = extraction.pinCount,
                    bladeLengthMm = extraction.bladeLengthMm,
                    bladeWidthMm = extraction.bladeWidthMm,
                    bowScale = extraction.bowScale,
                    isDoubleSided = extraction.isDoubleSided,
                    spacingsMm = extraction.spacingsMm,
                    depthsMm = extraction.depthsMm,
                    flatWidthsMm = extraction.flatWidthsMm,
                    leftSlopesDeg = extraction.leftSlopesDeg,
                    rightSlopesDeg = extraction.rightSlopesDeg,
                    tipStyle = extraction.tipStyle,
                    tipLengthMm = extraction.tipLengthMm,
                    customContourMm = extraction.customContourMm,
                    useExactPhotoContour = extraction.customContourMm.size >= 12,
                    photoOverlayBitmap = extraction.overlayPreview,
                    photoConfidencePercent = extraction.confidencePercent,
                    selectedPinIndex = 0,
                    bittingResult = decoded,
                    isAnalyzingPhoto = false
                )
                rebuildMesh()
            } else {
                _state.value = _state.value.copy(
                    photoOverlayBitmap = extraction.overlayPreview,
                    photoConfidencePercent = 0,
                    isAnalyzingPhoto = false
                )
            }
        }
    }

    fun saveKeyToVault(onSaved: (Long) -> Unit) {
        val cur = _state.value
        _state.value = cur.copy(isSaving = true)
        viewModelScope.launch {
            val recordId = repository.saveKey(cur.toKeyRecord())
            cur.sourceBitmap?.let { bmp ->
                repository.saveEncryptedPhotoAttachment(recordId, bmp, AttachmentType.ORIGINAL_PHOTO, isPrimary = cur.rectifiedBitmap == null)
            }
            cur.rectifiedBitmap?.let { bmp ->
                repository.saveEncryptedPhotoAttachment(recordId, bmp, AttachmentType.CALIBRATED_IMAGE, isPrimary = true)
            }
            _state.value = _state.value.copy(editingId = recordId, isSaving = false)
            onSaved(recordId)
        }
    }

    private fun maxAllowedCutDepth(bladeWidthMm: Float, isDoubleSided: Boolean): Float =
        if (isDoubleSided) {
            maxOf(0f, (bladeWidthMm - 1.4f) * 0.5f)
        } else {
            maxOf(0f, bladeWidthMm - 1.5f)
        }

    private fun normalizeList(list: List<Float>, targetSize: Int, defaultVal: Float): List<Float> =
        List(targetSize) { idx -> list.getOrElse(idx) { defaultVal } }

    private fun updateGeometry(
        standard: KeyStandardSpec = _state.value.standard,
        pinCount: Int = _state.value.pinCount,
        bladeLengthMm: Float = _state.value.bladeLengthMm,
        bladeWidthMm: Float = _state.value.bladeWidthMm,
        bladeThicknessMm: Float = _state.value.bladeThicknessMm,
        spacingsMm: List<Float> = _state.value.spacingsMm,
        depthsMm: List<Float> = _state.value.depthsMm,
        flatWidthsMm: List<Float> = _state.value.flatWidthsMm,
        leftSlopesDeg: List<Float> = _state.value.leftSlopesDeg,
        rightSlopesDeg: List<Float> = _state.value.rightSlopesDeg,
        isDoubleSided: Boolean = _state.value.isDoubleSided,
        tipStyle: KeyTipStyle = _state.value.tipStyle,
        tipLengthMm: Float = _state.value.tipLengthMm,
        tipOffsetMm: Float = _state.value.tipOffsetMm,
        bowScale: Float = _state.value.bowScale,
        useExactPhotoContour: Boolean = _state.value.useExactPhotoContour,
        selectedPinIndex: Int = _state.value.selectedPinIndex
    ) {
        val decoded = BittingCalculator.decode(standard, depthsMm, spacingsMm, bladeWidthMm)
        _state.value = _state.value.copy(
            standard = standard,
            pinCount = pinCount,
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm,
            bladeThicknessMm = bladeThicknessMm,
            spacingsMm = spacingsMm,
            depthsMm = depthsMm,
            flatWidthsMm = flatWidthsMm,
            leftSlopesDeg = leftSlopesDeg,
            rightSlopesDeg = rightSlopesDeg,
            isDoubleSided = isDoubleSided,
            tipStyle = tipStyle,
            tipLengthMm = tipLengthMm,
            tipOffsetMm = tipOffsetMm,
            bowScale = bowScale,
            useExactPhotoContour = useExactPhotoContour,
            selectedPinIndex = selectedPinIndex,
            bittingResult = decoded
        )
        if (_state.value.mode == StudioMode.PREVIEW_3D) {
            rebuildMesh()
        }
    }

    private fun rebuildMesh() {
        val record = _state.value.toKeyRecord()
        viewModelScope.launch(Dispatchers.Default) {
            val mesh = StlMeshGenerator.generateMesh(record)
            _state.value = _state.value.copy(mesh3D = mesh)
        }
    }

    class Factory(private val repository: KeyVaultRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(repository) as T
        }
    }
}
