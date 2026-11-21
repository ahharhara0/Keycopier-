package com.example.engine

import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyTipStyle
import com.example.domain.model.Point2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

data class KeyPolygon2D(
    val outerContour: List<Point2D>, // Closed perimeter in mm (origin at shoulder bottom-left: x=0 shoulder, y=0 bottom spine)
    val bowHoleContour: List<Point2D>, // Inner keyring hole perimeter in mm
    val totalLengthMm: Float,
    val bowLengthMm: Float,
    val bowHeightMm: Float,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float
)

/**
 * Generates an accurate 2D CAD polygon of a physical key in millimeter coordinates.
 * Supports single/double-sided blades, per-tooth asymmetric slopes & flat widths,
 * customizable rear tip taper shapes, scalable bow/frame, and 100% exact photo-extracted contours.
 */
object KeyPolygonBuilder {

    fun buildFromRecord(record: KeyRecord): KeyPolygon2D = buildKeyPolygon(
        bladeLengthMm = record.bladeLengthMm,
        bladeWidthMm = record.bladeWidthMm,
        spacingsMm = record.spacingsMm,
        depthsMm = record.depthsMm,
        isDoubleSided = record.isDoubleSided,
        flatWidthsMm = record.flatWidthsMm,
        leftSlopesDeg = record.leftSlopesDeg,
        rightSlopesDeg = record.rightSlopesDeg,
        tipStyle = record.tipStyle,
        tipLengthMm = record.tipLengthMm,
        tipOffsetMm = record.tipOffsetMm,
        bowStyle = record.bowStyle,
        bowScale = record.bowScale,
        customContourMm = record.customContourMm,
        useExactPhotoContour = record.useExactPhotoContour
    )

    fun buildKeyPolygon(
        bladeLengthMm: Float,
        bladeWidthMm: Float,
        spacingsMm: List<Float>,
        depthsMm: List<Float>,
        isDoubleSided: Boolean = false,
        flatLandWidthMm: Float = 1.15f,
        rampSlopeAngleDeg: Float = 45f,
        flatWidthsMm: List<Float> = emptyList(),
        leftSlopesDeg: List<Float> = emptyList(),
        rightSlopesDeg: List<Float> = emptyList(),
        tipStyle: KeyTipStyle = KeyTipStyle.STANDARD_TAPER,
        tipLengthMm: Float = 2.6f,
        tipOffsetMm: Float = 0f,
        bowStyle: com.example.domain.model.KeyBowStyle = com.example.domain.model.KeyBowStyle.CLASSIC_OVAL,
        bowScale: Float = 1.0f,
        customContourMm: List<Point2D> = emptyList(),
        useExactPhotoContour: Boolean = false
    ): KeyPolygon2D {
        if (useExactPhotoContour && customContourMm.size >= 12) {
            return buildFromExactPhotoContour(
                contour = customContourMm,
                bladeLengthMm = bladeLengthMm,
                bladeWidthMm = bladeWidthMm,
                bowScale = bowScale
            )
        }

        val sBow = bowScale.coerceIn(0.6f, 1.55f)
        val bowLength = when (bowStyle) {
            com.example.domain.model.KeyBowStyle.AUTO_FOB -> 28.0f * sBow
            com.example.domain.model.KeyBowStyle.SLIM_PADLOCK -> 18.5f * sBow
            com.example.domain.model.KeyBowStyle.SQUARE_HEAVY -> 23.0f * sBow
            else -> 24.5f * sBow
        }
        val bowHeight = when (bowStyle) {
            com.example.domain.model.KeyBowStyle.SLIM_PADLOCK -> 18.0f * sBow
            com.example.domain.model.KeyBowStyle.AUTO_FOB -> 25.0f * sBow
            else -> 23.0f * sBow
        }
        val spineCenterY = bladeWidthMm * 0.5f

        val outer = mutableListOf<Point2D>()

        // 1. Start at bottom shoulder stop (0, 0), step into neck & styled bow
        outer.add(Point2D(0f, 0f))
        outer.add(Point2D(-0.8f * sBow, 0f))
        outer.add(Point2D(-1.4f * sBow, -2.0f * sBow))
        outer.add(Point2D(-4.0f * sBow, -2.0f * sBow))

        val bowBackPoints: List<Point2D> = when (bowStyle) {
            com.example.domain.model.KeyBowStyle.HEX_SCHLAGE -> listOf(
                Point2D(-6.2f * sBow, spineCenterY - bowHeight * 0.44f),
                Point2D(-14.0f * sBow, spineCenterY - bowHeight * 0.50f),
                Point2D(-21.8f * sBow, spineCenterY - bowHeight * 0.36f),
                Point2D(-bowLength, spineCenterY - 2.6f * sBow),
                Point2D(-bowLength, spineCenterY + 2.6f * sBow),
                Point2D(-21.8f * sBow, spineCenterY + bowHeight * 0.36f),
                Point2D(-14.0f * sBow, spineCenterY + bowHeight * 0.50f),
                Point2D(-6.2f * sBow, spineCenterY + bowHeight * 0.44f)
            )
            com.example.domain.model.KeyBowStyle.SQUARE_HEAVY -> listOf(
                Point2D(-5.0f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-bowLength + 1.5f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-bowLength, spineCenterY - bowHeight * 0.40f),
                Point2D(-bowLength, spineCenterY + bowHeight * 0.40f),
                Point2D(-bowLength + 1.5f * sBow, spineCenterY + bowHeight * 0.48f),
                Point2D(-5.0f * sBow, spineCenterY + bowHeight * 0.48f)
            )
            com.example.domain.model.KeyBowStyle.TULIP_CROWN -> listOf(
                Point2D(-6.5f * sBow, spineCenterY - bowHeight * 0.42f),
                Point2D(-11.5f * sBow, spineCenterY - bowHeight * 0.54f),
                Point2D(-16.5f * sBow, spineCenterY - bowHeight * 0.44f),
                Point2D(-21.5f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-bowLength, spineCenterY - 4.0f * sBow),
                Point2D(-bowLength + 1.5f * sBow, spineCenterY),
                Point2D(-bowLength, spineCenterY + 4.0f * sBow),
                Point2D(-21.5f * sBow, spineCenterY + bowHeight * 0.48f),
                Point2D(-16.5f * sBow, spineCenterY + bowHeight * 0.44f),
                Point2D(-11.5f * sBow, spineCenterY + bowHeight * 0.54f),
                Point2D(-6.5f * sBow, spineCenterY + bowHeight * 0.42f)
            )
            com.example.domain.model.KeyBowStyle.AUTO_FOB -> listOf(
                Point2D(-5.5f * sBow, spineCenterY - bowHeight * 0.34f),
                Point2D(-9.0f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-23.0f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-bowLength, spineCenterY - bowHeight * 0.32f),
                Point2D(-bowLength, spineCenterY + bowHeight * 0.32f),
                Point2D(-23.0f * sBow, spineCenterY + bowHeight * 0.48f),
                Point2D(-9.0f * sBow, spineCenterY + bowHeight * 0.48f),
                Point2D(-5.5f * sBow, spineCenterY + bowHeight * 0.34f)
            )
            com.example.domain.model.KeyBowStyle.SLIM_PADLOCK -> listOf(
                Point2D(-5.2f * sBow, spineCenterY - bowHeight * 0.42f),
                Point2D(-12.5f * sBow, spineCenterY - bowHeight * 0.46f),
                Point2D(-bowLength, spineCenterY - 3.6f * sBow),
                Point2D(-bowLength, spineCenterY + 3.6f * sBow),
                Point2D(-12.5f * sBow, spineCenterY + bowHeight * 0.46f),
                Point2D(-5.2f * sBow, spineCenterY + bowHeight * 0.42f)
            )
            com.example.domain.model.KeyBowStyle.ROUND_COIN -> {
                val cx = -13.8f * sBow
                val r = bowHeight * 0.50f
                val steps = 11
                val pts = mutableListOf<Point2D>()
                for (k in 0..steps) {
                    val theta = (-PI * 0.44 + (k.toFloat() / steps) * (PI * 0.88)) + PI
                    pts.add(
                        Point2D(
                            x = (cx + cos(theta).toFloat() * r).coerceAtMost(-4.5f * sBow),
                            y = spineCenterY - sin(theta).toFloat() * r
                        )
                    )
                }
                pts
            }
            com.example.domain.model.KeyBowStyle.CLASSIC_OVAL -> listOf(
                Point2D(-7.5f * sBow, spineCenterY - bowHeight * 0.48f),
                Point2D(-15.5f * sBow, spineCenterY - bowHeight * 0.50f),
                Point2D(-21.5f * sBow, spineCenterY - bowHeight * 0.36f),
                Point2D(-bowLength, spineCenterY - 4.2f * sBow),
                Point2D(-bowLength, spineCenterY + 4.2f * sBow),
                Point2D(-21.5f * sBow, spineCenterY + bowHeight * 0.36f),
                Point2D(-15.5f * sBow, spineCenterY + bowHeight * 0.50f),
                Point2D(-7.5f * sBow, spineCenterY + bowHeight * 0.48f)
            )
        }
        outer.addAll(bowBackPoints)

        outer.add(Point2D(-4.0f * sBow, bladeWidthMm + 2.0f * sBow))
        outer.add(Point2D(-1.4f * sBow, bladeWidthMm + 2.0f * sBow))
        outer.add(Point2D(-0.8f * sBow, bladeWidthMm))

        // 2. Top shoulder & uncut lead-in
        outer.add(Point2D(0f, bladeWidthMm))

        val maxCutDepth = if (isDoubleSided) {
            maxOf(0f, (bladeWidthMm - 1.4f) * 0.5f)
        } else {
            maxOf(0f, bladeWidthMm - 1.5f)
        }

        val pinCount = minOf(spacingsMm.size, depthsMm.size)

        // 3. Top edge pin bitting cuts (shoulder -> tip) with per-pin shape
        var lastX = 0.8f
        for (i in 0 until pinCount) {
            val minPinX = lastX + 0.5f
            val maxPinX = maxOf(minPinX, bladeLengthMm - 1.8f)
            val pinX = spacingsMm[i].coerceIn(minPinX, maxPinX)
            val cutDepth = depthsMm[i].coerceIn(0f, maxCutDepth)
            val cutY = bladeWidthMm - cutDepth

            val flatW = flatWidthsMm.getOrElse(i) { flatLandWidthMm }.coerceIn(0.15f, 3.2f)
            val halfFlat = flatW * 0.5f
            val lAngle = leftSlopesDeg.getOrElse(i) { rampSlopeAngleDeg }.coerceIn(20f, 84f)
            val rAngle = rightSlopesDeg.getOrElse(i) { rampSlopeAngleDeg }.coerceIn(20f, 84f)
            val lTan = tan(Math.toRadians(lAngle.toDouble())).toFloat().coerceAtLeast(0.32f)
            val rTan = tan(Math.toRadians(rAngle.toDouble())).toFloat().coerceAtLeast(0.32f)

            if (cutDepth > 0.08f) {
                val leftRun = cutDepth / lTan
                val rightRun = cutDepth / rTan
                val cutStartX = (pinX - halfFlat - leftRun).coerceAtLeast(lastX)
                val flatStartX = (pinX - halfFlat).coerceAtLeast(cutStartX + 0.04f)
                val flatEndX = (pinX + halfFlat).coerceIn(
                    flatStartX + 0.04f,
                    maxOf(flatStartX + 0.04f, bladeLengthMm - 1.3f)
                )
                val cutEndX = (pinX + halfFlat + rightRun).coerceIn(
                    flatEndX + 0.04f,
                    maxOf(flatEndX + 0.04f, bladeLengthMm - 1.0f)
                )

                if (cutStartX > lastX + 0.04f) {
                    outer.add(Point2D(cutStartX, bladeWidthMm))
                }
                outer.add(Point2D(flatStartX, cutY))
                outer.add(Point2D(flatEndX, cutY))
                outer.add(Point2D(cutEndX, bladeWidthMm))
                lastX = cutEndX
            } else {
                outer.add(Point2D(pinX, bladeWidthMm))
                lastX = pinX + halfFlat
            }
        }

        // 4. Customizable Rear Tip Taper (المدبب الخلفي)
        val effectiveTipX = maxOf(bladeLengthMm, lastX + 1.4f)
        val desiredTaper = tipLengthMm.coerceIn(0.8f, 6.5f)
        val maxAvailTaper = (effectiveTipX - lastX - 0.15f).coerceAtLeast(0.5f)
        val actualTaper = minOf(desiredTaper, maxAvailTaper)
        val tipBevelStart = (effectiveTipX - actualTaper).coerceAtLeast(lastX + 0.1f)
        val tipCenterY = (bladeWidthMm * 0.5f + tipOffsetMm).coerceIn(0.5f, maxOf(0.6f, bladeWidthMm - 0.5f))

        if (tipBevelStart > lastX + 0.04f) {
            outer.add(Point2D(tipBevelStart, bladeWidthMm))
        }

        when (tipStyle) {
            KeyTipStyle.SHARP_POINT -> {
                outer.add(Point2D(effectiveTipX, tipCenterY))
            }
            KeyTipStyle.CHISEL_FLAT -> {
                val topNoseY = (tipCenterY + bladeWidthMm * 0.28f).coerceAtMost(bladeWidthMm - 0.2f)
                val botNoseY = (tipCenterY - bladeWidthMm * 0.28f).coerceAtLeast(0.2f)
                outer.add(Point2D(effectiveTipX, maxOf(topNoseY, botNoseY + 0.2f)))
                outer.add(Point2D(effectiveTipX, botNoseY))
            }
            KeyTipStyle.ROUNDED -> {
                val radiusX = (effectiveTipX - tipBevelStart).coerceAtLeast(0.6f)
                val topSpan = (bladeWidthMm - tipCenterY).coerceAtLeast(0.5f)
                val botSpan = tipCenterY.coerceAtLeast(0.5f)
                val arcSteps = 7
                for (s in 1 until arcSteps) {
                    val t = s.toFloat() / arcSteps.toFloat()
                    val theta = (PI * 0.5 - t * PI).toFloat() // +90deg to -90deg
                    val px = tipBevelStart + cos(theta) * radiusX
                    val py = if (sin(theta) >= 0f) {
                        tipCenterY + sin(theta) * topSpan
                    } else {
                        tipCenterY + sin(theta) * botSpan
                    }
                    outer.add(Point2D(px, py.coerceIn(0f, bladeWidthMm)))
                }
            }
            KeyTipStyle.SLANT_TOP -> {
                val slantNoseY = (tipCenterY - bladeWidthMm * 0.22f).coerceIn(0.3f, bladeWidthMm * 0.45f)
                outer.add(Point2D(effectiveTipX, slantNoseY))
                outer.add(Point2D(effectiveTipX - minOf(0.7f, actualTaper * 0.35f), 0f))
            }
            KeyTipStyle.STANDARD_TAPER -> {
                val upperTipY = (tipCenterY + bladeWidthMm * 0.14f).coerceAtMost(bladeWidthMm - 0.3f)
                val lowerTipY = (tipCenterY - bladeWidthMm * 0.16f).coerceAtLeast(0.3f)
                outer.add(Point2D(effectiveTipX, maxOf(upperTipY, lowerTipY + 0.2f)))
                outer.add(Point2D(effectiveTipX, lowerTipY))
            }
        }

        // Bottom tip closure + bottom edge (mirrored if double-sided)
        if (isDoubleSided) {
            if (tipStyle != KeyTipStyle.SLANT_TOP && tipBevelStart > lastX + 0.04f) {
                outer.add(Point2D(tipBevelStart, 0f))
            } else if (tipBevelStart > 1.0f && outer.last().y > 0.05f) {
                outer.add(Point2D(tipBevelStart, 0f))
            }

            var prevBottomX = tipBevelStart
            for (i in pinCount - 1 downTo 0) {
                val minPinX = 0.8f
                val maxPinX = maxOf(minPinX, prevBottomX - 0.5f)
                val pinX = spacingsMm[i].coerceIn(minPinX, maxPinX)
                val cutDepth = depthsMm[i].coerceIn(0f, maxCutDepth)

                val flatW = flatWidthsMm.getOrElse(i) { flatLandWidthMm }.coerceIn(0.15f, 3.2f)
                val halfFlat = flatW * 0.5f
                val lAngle = leftSlopesDeg.getOrElse(i) { rampSlopeAngleDeg }.coerceIn(20f, 84f)
                val rAngle = rightSlopesDeg.getOrElse(i) { rampSlopeAngleDeg }.coerceIn(20f, 84f)
                val lTan = tan(Math.toRadians(lAngle.toDouble())).toFloat().coerceAtLeast(0.32f)
                val rTan = tan(Math.toRadians(rAngle.toDouble())).toFloat().coerceAtLeast(0.32f)

                if (cutDepth > 0.08f) {
                    val leftRun = cutDepth / lTan
                    val rightRun = cutDepth / rTan
                    val cutEndX = (pinX + halfFlat + rightRun).coerceAtMost(prevBottomX)
                    val flatEndX = (pinX + halfFlat).coerceAtMost(cutEndX - 0.04f)
                    val flatStartX = (pinX - halfFlat).coerceIn(
                        0.6f,
                        maxOf(0.6f, flatEndX - 0.04f)
                    )
                    val cutStartX = (pinX - halfFlat - leftRun).coerceIn(
                        0.5f,
                        maxOf(0.5f, flatStartX - 0.04f)
                    )

                    if (cutEndX < prevBottomX - 0.04f) {
                        outer.add(Point2D(cutEndX, 0f))
                    }
                    outer.add(Point2D(flatEndX, cutDepth))
                    outer.add(Point2D(flatStartX, cutDepth))
                    outer.add(Point2D(cutStartX, 0f))
                    prevBottomX = cutStartX
                }
            }
        } else if (tipStyle != KeyTipStyle.SLANT_TOP) {
            val botBevelX = when (tipStyle) {
                KeyTipStyle.SHARP_POINT -> tipBevelStart
                KeyTipStyle.CHISEL_FLAT -> (effectiveTipX - minOf(1.0f, actualTaper * 0.5f))
                else -> (effectiveTipX - minOf(1.8f, actualTaper * 0.7f))
            }.coerceAtLeast(lastX + 0.05f)
            outer.add(Point2D(botBevelX, 0f))
        }

        // 6. Keyring hole inside scaled bow
        val holeCenterX = -(bowLength * 0.56f).coerceIn(4.8f, bowLength - 4.2f)
        val holeCenterY = spineCenterY
        val holeRadius = minOf(3.1f * sBow, bowHeight * 0.24f).coerceIn(1.5f, 4.8f)
        val holeContour = buildCircleContour(holeCenterX, holeCenterY, holeRadius)

        return KeyPolygon2D(
            outerContour = outer,
            bowHoleContour = holeContour,
            totalLengthMm = bowLength + effectiveTipX,
            bowLengthMm = bowLength,
            bowHeightMm = bowHeight,
            bladeLengthMm = effectiveTipX,
            bladeWidthMm = bladeWidthMm
        )
    }

    private fun buildFromExactPhotoContour(
        contour: List<Point2D>,
        bladeLengthMm: Float,
        bladeWidthMm: Float,
        bowScale: Float
    ): KeyPolygon2D {
        // Deduplicate near-identical points to guarantee clean ear-clipping triangulation
        val cleaned = ArrayList<Point2D>(contour.size)
        for (p in contour) {
            val prev = cleaned.lastOrNull()
            if (prev == null || hypot(p.x - prev.x, p.y - prev.y) > 0.04f) {
                cleaned.add(p)
            }
        }
        if (cleaned.size >= 2 && hypot(cleaned.first().x - cleaned.last().x, cleaned.first().y - cleaned.last().y) < 0.04f) {
            cleaned.removeAt(cleaned.lastIndex)
        }

        val minX = cleaned.minOfOrNull { it.x } ?: -24f
        val maxX = cleaned.maxOfOrNull { it.x } ?: bladeLengthMm
        val bowPts = cleaned.filter { it.x < -1.0f }
        val bowMinY = bowPts.minOfOrNull { it.y } ?: -6f
        val bowMaxY = bowPts.maxOfOrNull { it.y } ?: (bladeWidthMm + 6f)
        val bowCenterY = (bowMinY + bowMaxY) * 0.5f
        val bowHeight = (bowMaxY - bowMinY).coerceAtLeast(bladeWidthMm + 4f)
        val bowLength = abs(minX).coerceAtLeast(10f)

        val holeCenterX = (minX * 0.60f).coerceIn(minX + 4.5f, -4.5f)
        val maxSafeRadius = minOf(
            (bowHeight * 0.24f),
            (bowLength * 0.22f),
            3.1f * bowScale.coerceIn(0.6f, 1.5f)
        ).coerceIn(1.2f, 4.5f)
        val holeContour = buildCircleContour(holeCenterX, bowCenterY, maxSafeRadius)

        return KeyPolygon2D(
            outerContour = cleaned,
            bowHoleContour = holeContour,
            totalLengthMm = bowLength + maxOf(bladeLengthMm, maxX),
            bowLengthMm = bowLength,
            bowHeightMm = bowHeight,
            bladeLengthMm = maxOf(bladeLengthMm, maxX),
            bladeWidthMm = bladeWidthMm
        )
    }

    private fun buildCircleContour(cx: Float, cy: Float, radius: Float, segments: Int = 16): List<Point2D> =
        List(segments) { idx ->
            val angle = (2.0 * PI * idx / segments).toFloat()
            Point2D(
                x = cx + radius * cos(angle),
                y = cy + radius * sin(angle)
            )
        }
}
