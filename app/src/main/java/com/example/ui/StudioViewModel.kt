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
import com.example.domain.model.Point2D
import com.example.engine.BittingCalculator
import com.example.engine.BittingDecodeResult
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
    val isDoubleSided: Boolean = false,
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
        _state.value = StudioUiState(
            editingId = record.id,
            mode = StudioMode.BITTING_CAD,
            keyName = record.name,
            category = record.category,
            notes = record.notes,
            tags = record.tags,
            standard = spec,
            pinCount = record.pinCount,
            bladeLengthMm = record.bladeLengthMm,
            bladeWidthMm = record.bladeWidthMm,
            bladeThicknessMm = record.bladeThicknessMm,
            spacingsMm = record.spacingsMm,
            depthsMm = record.depthsMm,
            isDoubleSided = record.isDoubleSided,
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
            depthsMm = depths
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
        val minBladeLen = (spacings.lastOrNull() ?: 20f) + 4.0f
        updateGeometry(
            pinCount = clamped,
            bladeLengthMm = maxOf(cur.bladeLengthMm, minBladeLen),
            spacingsMm = spacings,
            depthsMm = depths,
            selectedPinIndex = cur.selectedPinIndex.coerceIn(0, clamped - 1)
        )
    }

    fun selectPin(index: Int) {
        _state.value = _state.value.copy(
            selectedPinIndex = index.coerceIn(0, _state.value.pinCount - 1)
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
            isDoubleSided = doubleSided
        )
    }

    fun setShowLongitudinalPad(show: Boolean) {
        _state.value = _state.value.copy(showLongitudinalPad = show)
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
        updateGeometry(depthsMm = updated, selectedPinIndex = index)
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

    fun updatePinDepth(index: Int, newDepthMm: Float) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        val maxAllowed = maxAllowedCutDepth(cur.bladeWidthMm, cur.isDoubleSided)
        val clamped = BittingCalculator.roundToPrecision(
            newDepthMm.coerceIn(0f, maxAllowed),
            0.05f
        )
        val updated = cur.depthsMm.toMutableList().apply { this[index] = clamped }
        updateGeometry(depthsMm = updated, selectedPinIndex = index)
    }

    fun updatePinSpacing(index: Int, newSpacingMm: Float) {
        val cur = _state.value
        if (index !in 0 until cur.pinCount) return
        val minS = if (index == 0) 2.5f else cur.spacingsMm[index - 1] + 2.0f
        val maxS = if (index == cur.pinCount - 1) cur.bladeLengthMm - 2.5f else cur.spacingsMm[index + 1] - 2.0f
        val clamped = BittingCalculator.roundToPrecision(
            newSpacingMm.coerceIn(minS, maxOf(minS, maxS)),
            0.05f
        )
        val updated = cur.spacingsMm.toMutableList().apply { this[index] = clamped }
        updateGeometry(spacingsMm = updated, selectedPinIndex = index)
    }

    fun applyBittingCodeInput(codeText: String) {
        val cur = _state.value
        val digitsOnly = codeText.filter { it.isDigit() }.take(cur.pinCount)
        if (digitsOnly.isEmpty()) return
        val newDepths = BittingCalculator.depthsFromBittingCode(cur.standard, digitsOnly, cur.pinCount)
        updateGeometry(depthsMm = newDepths)
    }

    fun updateBladeDimensions(lengthMm: Float? = null, widthMm: Float? = null, thicknessMm: Float? = null) {
        val cur = _state.value
        val l = (lengthMm ?: cur.bladeLengthMm).coerceIn(18f, 45f)
        val w = (widthMm ?: cur.bladeWidthMm).coerceIn(5.5f, 12f)
        val t = (thicknessMm ?: cur.bladeThicknessMm).coerceIn(1.4f, 3.5f)
        updateGeometry(bladeLengthMm = l, bladeWidthMm = w, bladeThicknessMm = t)
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

    // --- Photo Calibration & Camera ---

    fun setShowCameraPreview(show: Boolean) {
        _state.value = _state.value.copy(showCameraPreview = show)
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            sourceBitmap = bitmap,
            rectifiedBitmap = null,
            cardCorners = StudioUiState.defaultCorners(),
            showCameraPreview = false
        )
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
            maxOf(0f, (bladeWidthMm - 1.6f) * 0.5f)
        } else {
            maxOf(0f, bladeWidthMm - 1.8f)
        }

    private fun updateGeometry(
        standard: KeyStandardSpec = _state.value.standard,
        pinCount: Int = _state.value.pinCount,
        bladeLengthMm: Float = _state.value.bladeLengthMm,
        bladeWidthMm: Float = _state.value.bladeWidthMm,
        bladeThicknessMm: Float = _state.value.bladeThicknessMm,
        spacingsMm: List<Float> = _state.value.spacingsMm,
        depthsMm: List<Float> = _state.value.depthsMm,
        isDoubleSided: Boolean = _state.value.isDoubleSided,
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
            isDoubleSided = isDoubleSided,
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
