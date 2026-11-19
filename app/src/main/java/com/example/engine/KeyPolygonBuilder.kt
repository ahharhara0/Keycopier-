package com.example.engine

import com.example.domain.model.Point2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class KeyPolygon2D(
    val outerContour: List<Point2D>, // Clockwise/CCW closed perimeter in mm (origin at shoulder bottom-left: x=0 is shoulder, y=0 is bottom spine)
    val bowHoleContour: List<Point2D>, // Inner keyring hole perimeter in mm
    val totalLengthMm: Float,
    val bowLengthMm: Float,
    val bowHeightMm: Float,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float
)

/**
 * Generates an accurate 2D CAD polygon of a physical key in millimeter coordinates.
 * Supports both single-sided (flat bottom spine) and double-sided (symmetric top & bottom cuts).
 */
object KeyPolygonBuilder {

    fun buildKeyPolygon(
        bladeLengthMm: Float,
        bladeWidthMm: Float,
        spacingsMm: List<Float>,
        depthsMm: List<Float>,
        isDoubleSided: Boolean = false,
        flatLandWidthMm: Float = 1.15f,
        rampSlopeAngleDeg: Float = 45f
    ): KeyPolygon2D {
        val bowLength = 24.5f
        val bowHeight = 23.0f
        val spineCenterY = bladeWidthMm * 0.5f

        val outer = mutableListOf<Point2D>()

        // 1. Start at bottom shoulder stop (0, 0), step down into neck & bow
        outer.add(Point2D(0f, 0f))
        outer.add(Point2D(-0.8f, 0f))
        outer.add(Point2D(-1.4f, -2.2f))
        outer.add(Point2D(-4.2f, -2.2f))
        outer.add(Point2D(-7.5f, spineCenterY - bowHeight * 0.48f))
        outer.add(Point2D(-15.5f, spineCenterY - bowHeight * 0.50f))
        outer.add(Point2D(-21.5f, spineCenterY - bowHeight * 0.36f))
        outer.add(Point2D(-bowLength, spineCenterY - 4.2f))
        outer.add(Point2D(-bowLength, spineCenterY + 4.2f))
        outer.add(Point2D(-21.5f, spineCenterY + bowHeight * 0.36f))
        outer.add(Point2D(-15.5f, spineCenterY + bowHeight * 0.50f))
        outer.add(Point2D(-7.5f, spineCenterY + bowHeight * 0.48f))
        outer.add(Point2D(-4.2f, bladeWidthMm + 2.2f))
        outer.add(Point2D(-1.4f, bladeWidthMm + 2.2f))
        outer.add(Point2D(-0.8f, bladeWidthMm))

        // 2. Top shoulder & uncut lead-in
        outer.add(Point2D(0f, bladeWidthMm))

        // Compute safe max depth (if double-sided, leave >= 1.6mm core thickness between top & bottom cuts)
        val maxCutDepth = if (isDoubleSided) {
            maxOf(0f, (bladeWidthMm - 1.6f) * 0.5f)
        } else {
            maxOf(0f, bladeWidthMm - 1.8f)
        }

        val pinCount = minOf(spacingsMm.size, depthsMm.size)
        val halfFlat = flatLandWidthMm * 0.5f
        val slopeTan = kotlin.math.tan(Math.toRadians(rampSlopeAngleDeg.toDouble())).toFloat().coerceAtLeast(0.4f)

        // 3. Top edge pin bitting cuts (shoulder -> tip)
        val topPins = mutableListOf<Triple<Float, Float, Float>>() // (cutStartX, flatStartX..flatEndX, cutEndX, cutDepth)
        var lastX = 0.8f
        for (i in 0 until pinCount) {
            val minPinX = lastX + 0.6f
            val maxPinX = maxOf(minPinX, bladeLengthMm - 2.2f)
            val pinX = spacingsMm[i].coerceIn(minPinX, maxPinX)
            val cutDepth = depthsMm[i].coerceIn(0f, maxCutDepth)
            val cutY = bladeWidthMm - cutDepth

            if (cutDepth > 0.08f) {
                val rampRun = cutDepth / slopeTan
                val cutStartX = (pinX - halfFlat - rampRun).coerceAtLeast(lastX)
                val flatStartX = (pinX - halfFlat).coerceAtLeast(cutStartX + 0.05f)
                val flatEndX = (pinX + halfFlat).coerceIn(
                    flatStartX + 0.05f,
                    maxOf(flatStartX + 0.05f, bladeLengthMm - 1.6f)
                )
                val cutEndX = (pinX + halfFlat + rampRun).coerceIn(
                    flatEndX + 0.05f,
                    maxOf(flatEndX + 0.05f, bladeLengthMm - 1.2f)
                )

                if (cutStartX > lastX + 0.05f) {
                    outer.add(Point2D(cutStartX, bladeWidthMm))
                }
                outer.add(Point2D(flatStartX, cutY))
                outer.add(Point2D(flatEndX, cutY))
                outer.add(Point2D(cutEndX, bladeWidthMm))
                topPins.add(Triple(cutStartX, cutEndX, cutDepth))
                lastX = cutEndX
            } else {
                outer.add(Point2D(pinX, bladeWidthMm))
                lastX = pinX + halfFlat
            }
        }

        // 4. Tip bevel
        val effectiveTipX = maxOf(bladeLengthMm, lastX + 1.8f)
        val tipBevelStart = (effectiveTipX - 2.6f).coerceAtLeast(lastX + 0.2f)
        if (tipBevelStart > lastX) {
            outer.add(Point2D(tipBevelStart, bladeWidthMm))
        }
        if (isDoubleSided) {
            outer.add(Point2D(effectiveTipX, bladeWidthMm * 0.62f))
            outer.add(Point2D(effectiveTipX, bladeWidthMm * 0.38f))
            if (tipBevelStart > lastX) {
                outer.add(Point2D(tipBevelStart, 0f))
            }

            // 5. Bottom edge mirrored bitting cuts (tip -> shoulder)
            var prevBottomX = tipBevelStart
            for (i in pinCount - 1 downTo 0) {
                val minPinX = 0.8f
                val maxPinX = maxOf(minPinX, prevBottomX - 0.6f)
                val pinX = spacingsMm[i].coerceIn(minPinX, maxPinX)
                val cutDepth = depthsMm[i].coerceIn(0f, maxCutDepth)

                if (cutDepth > 0.08f) {
                    val rampRun = cutDepth / slopeTan
                    val cutEndX = (pinX + halfFlat + rampRun).coerceAtMost(prevBottomX)
                    val flatEndX = (pinX + halfFlat).coerceAtMost(cutEndX - 0.05f)
                    val flatStartX = (pinX - halfFlat).coerceIn(
                        0.6f,
                        maxOf(0.6f, flatEndX - 0.05f)
                    )
                    val cutStartX = (pinX - halfFlat - rampRun).coerceIn(
                        0.5f,
                        maxOf(0.5f, flatStartX - 0.05f)
                    )

                    if (cutEndX < prevBottomX - 0.05f) {
                        outer.add(Point2D(cutEndX, 0f))
                    }
                    outer.add(Point2D(flatEndX, cutDepth))
                    outer.add(Point2D(flatStartX, cutDepth))
                    outer.add(Point2D(cutStartX, 0f))
                    prevBottomX = cutStartX
                }
            }
        } else {
            outer.add(Point2D(effectiveTipX, bladeWidthMm * 0.45f))
            outer.add(Point2D(effectiveTipX, bladeWidthMm * 0.25f))
            outer.add(Point2D(effectiveTipX - 1.8f, 0f))
        }

        // 6. Keyring hole inside the bow (16-segment circle)
        val holeCenterX = -15.2f
        val holeCenterY = spineCenterY
        val holeRadius = 3.1f
        val holeSegments = 16
        val holeContour = List(holeSegments) { idx ->
            val angle = (2.0 * PI * idx / holeSegments).toFloat()
            Point2D(
                x = holeCenterX + holeRadius * cos(angle),
                y = holeCenterY + holeRadius * sin(angle)
            )
        }

        return KeyPolygon2D(
            outerContour = outer,
            bowHoleContour = holeContour,
            totalLengthMm = bowLength + bladeLengthMm,
            bowLengthMm = bowLength,
            bowHeightMm = bowHeight,
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm
        )
    }
}
