package com.example.engine

import com.example.domain.model.KeyStandardSpec
import com.example.domain.model.MeasurementUnit
import java.util.Locale
import kotlin.math.roundToInt

data class DecodedPin(
    val pinNumber: Int,
    val spacingMm: Float,
    val cutDepthMm: Float,
    val rootHeightMm: Float,
    val codeDigit: Int,
    val errorDeltaMm: Float
)

data class BittingDecodeResult(
    val bittingCode: String,
    val pins: List<DecodedPin>,
    val maxErrorMm: Float
)

object BittingCalculator {

    private const val MM_TO_MIL = 39.3700787f

    fun decode(
        spec: KeyStandardSpec,
        depthsMm: List<Float>,
        spacingsMm: List<Float>,
        bladeWidthMm: Float
    ): BittingDecodeResult {
        val count = minOf(depthsMm.size, spacingsMm.size)
        val decodedPins = ArrayList<DecodedPin>(count)
        var maxErr = 0f
        val codeBuilder = StringBuilder(count)

        for (i in 0 until count) {
            val depth = depthsMm[i].coerceIn(0f, maxOf(0f, bladeWidthMm - 1.5f))
            val (code, delta) = spec.nearestCodeForCutDepth(depth)
            val absDelta = kotlin.math.abs(delta)
            if (absDelta > maxErr) maxErr = absDelta

            codeBuilder.append(code)
            decodedPins.add(
                DecodedPin(
                    pinNumber = i + 1,
                    spacingMm = spacingsMm[i],
                    cutDepthMm = depth,
                    rootHeightMm = (bladeWidthMm - depth).coerceAtLeast(1.5f),
                    codeDigit = code,
                    errorDeltaMm = delta
                )
            )
        }

        return BittingDecodeResult(
            bittingCode = codeBuilder.toString(),
            pins = decodedPins,
            maxErrorMm = maxErr
        )
    }

    fun depthsFromBittingCode(
        spec: KeyStandardSpec,
        bittingCode: String,
        pinCount: Int
    ): List<Float> {
        val digits = bittingCode.filter { it.isDigit() }.map { it.digitToInt() }
        return List(pinCount) { idx ->
            val digit = digits.getOrElse(idx) { spec.minCode }
                .coerceIn(spec.minCode, spec.maxCode)
            spec.cutDepthForCode(digit)
        }
    }

    fun formatDimension(mm: Float, unit: MeasurementUnit): String = when (unit) {
        MeasurementUnit.MM -> String.format(Locale.US, "%.2f mm", mm)
        MeasurementUnit.MIL -> String.format(Locale.US, "%d mil", (mm * MM_TO_MIL).roundToInt())
    }

    fun roundToPrecision(valueMm: Float, stepMm: Float = 0.05f): Float {
        return (valueMm / stepMm).roundToInt() * stepMm
    }
}
