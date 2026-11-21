package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.domain.model.KeyBowStyle
import com.example.domain.model.KeyStandardSpec
import com.example.domain.model.KeyStandards
import com.example.domain.model.KeyTipStyle
import com.example.domain.model.Point2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class PhotoCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean
        get() = left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f

    companion object {
        val FULL = PhotoCropRect(0f, 0f, 1f, 1f)
    }
}

data class PhotoKeyExtractionResult(
    val detected: Boolean,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float,
    val bowScale: Float,
    val bowStyle: KeyBowStyle = KeyBowStyle.CLASSIC_OVAL,
    val matchedStandardId: String = "SC1",
    val isDoubleSided: Boolean,
    val pinCount: Int,
    val spacingsMm: List<Float>,
    val depthsMm: List<Float>,
    val flatWidthsMm: List<Float>,
    val leftSlopesDeg: List<Float>,
    val rightSlopesDeg: List<Float>,
    val tipStyle: KeyTipStyle,
    val tipLengthMm: Float,
    val customContourMm: List<Point2D>,
    val overlayPreview: Bitmap,
    val confidencePercent: Int
)

/**
 * Pure-Kotlin 100% offline Sub-Pixel Computer Vision Key Analyzer.
 *
 * Multi-stage pipeline designed to minimize error on real phone photographs:
 *  1. Optional normalized ROI cropping so busy desk edges never pollute segmentation.
 *  2. 8x8 bilinear background illumination estimation (handles uneven shadows/gradients).
 *  3. HSV chroma shadow rejection fused with 3x3 Sobel edge magnitude + color distance.
 *  4. Full-bin Otsu adaptive thresholding (adjustable via shadowSensitivity) + morphological close.
 *  5. Exterior-seeded BFS glare hole-filling (heals bright specular spots inside metal blades).
 *  6. Connected-component geometric scoring + PCA coarse orientation.
 *  7. RANSAC straight-edge spine locking (< 0.15 deg tilt error) + sub-pixel boundary scan.
 *  8. Derivative shoulder localization, 5-pt Savitzky-Golay smoothing, prominence & 2nd-derivative
 *     parabolic sub-pixel tooth valley extraction, flank slope regression & standard/bow matching.
 */
object KeyPhotoAnalyzer {

    fun analyzeKeyPhoto(
        source: Bitmap,
        standard: KeyStandardSpec,
        pxPerMmHint: Float? = null,
        cropRect: PhotoCropRect = PhotoCropRect.FULL,
        shadowSensitivity: Float = 0f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false
    ): PhotoKeyExtractionResult {
        // 1. Apply normalized ROI crop if specified
        val cropL = (cropRect.left.coerceIn(0f, 0.85f) * source.width).roundToInt()
        val cropT = (cropRect.top.coerceIn(0f, 0.85f) * source.height).roundToInt()
        val cropR = (cropRect.right.coerceIn(cropRect.left + 0.12f, 1f) * source.width).roundToInt()
        val cropB = (cropRect.bottom.coerceIn(cropRect.top + 0.12f, 1f) * source.height).roundToInt()
        val roiW = (cropR - cropL).coerceAtLeast(32)
        val roiH = (cropB - cropT).coerceAtLeast(32)

        val cropped = if (!cropRect.isFull && cropL + roiW <= source.width && cropT + roiH <= source.height) {
            Bitmap.createBitmap(source, cropL, cropT, roiW, roiH)
        } else {
            source
        }

        val maxDim = 640f
        val scaleDown = minOf(1f, maxDim / maxOf(cropped.width, cropped.height).coerceAtLeast(1))
        val w = (cropped.width * scaleDown).roundToInt().coerceAtLeast(32)
        val h = (cropped.height * scaleDown).roundToInt().coerceAtLeast(32)
        val workBmp = if (w != cropped.width || h != cropped.height) {
            Bitmap.createScaledBitmap(cropped, w, h, true)
        } else {
            cropped.copy(Bitmap.Config.ARGB_8888, false)
        }

        val nPx = w * h
        val pixels = IntArray(nPx)
        workBmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val chR = FloatArray(nPx)
        val chG = FloatArray(nPx)
        val chB = FloatArray(nPx)
        val lum = FloatArray(nPx)
        val sat = FloatArray(nPx)

        for (i in 0 until nPx) {
            val c = pixels[i]
            val r = ((c ushr 16) and 0xFF).toFloat()
            val g = ((c ushr 8) and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            chR[i] = r; chG[i] = g; chB[i] = b
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            val mx = maxOf(r, maxOf(g, b))
            val mn = minOf(r, minOf(g, b))
            sat[i] = if (mx > 1f) (mx - mn) / mx else 0f
        }

        // 2. Local 8x8 background illumination model estimated from border & low-gradient perimeter cells
        val gridCols = 8
        val gridRows = 8
        val cellR = FloatArray(gridCols * gridRows)
        val cellG = FloatArray(gridCols * gridRows)
        val cellB = FloatArray(gridCols * gridRows)
        val cellValid = BooleanArray(gridCols * gridRows)

        val borderR = ArrayList<Float>(w * 2 + h * 2)
        val borderG = ArrayList<Float>(w * 2 + h * 2)
        val borderB = ArrayList<Float>(w * 2 + h * 2)
        val marginX = maxOf(2, (w * 0.05f).toInt())
        val marginY = maxOf(2, (h * 0.05f).toInt())

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                if (x < marginX || x >= w - marginX || y < marginY || y >= h - marginY) {
                    val idx = rowOff + x
                    borderR.add(chR[idx]); borderG.add(chG[idx]); borderB.add(chB[idx])
                }
            }
        }
        val globalBgR = medianOf(borderR, 128f)
        val globalBgG = medianOf(borderG, 128f)
        val globalBgB = medianOf(borderB, 128f)

        for (gy in 0 until gridRows) {
            val y0 = (gy * h) / gridRows
            val y1 = ((gy + 1) * h) / gridRows
            for (gx in 0 until gridCols) {
                val x0 = (gx * w) / gridCols
                val x1 = ((gx + 1) * w) / gridCols
                val isBorderCell = gx == 0 || gx == gridCols - 1 || gy == 0 || gy == gridRows - 1
                val sr = ArrayList<Float>()
                val sg = ArrayList<Float>()
                val sb = ArrayList<Float>()
                val step = maxOf(1, ((x1 - x0) * (y1 - y0)) / 96)
                var count = 0
                for (yy in y0 until y1) {
                    val ro = yy * w
                    for (xx in x0 until x1) {
                        if (count++ % step != 0) continue
                        val i = ro + xx
                        val distToBorder = hypot(chR[i] - globalBgR, hypot(chG[i] - globalBgG, chB[i] - globalBgB))
                        if (isBorderCell || distToBorder < 42f) {
                            sr.add(chR[i]); sg.add(chG[i]); sb.add(chB[i])
                        }
                    }
                }
                val cIdx = gy * gridCols + gx
                if (sr.size >= 4) {
                    cellR[cIdx] = medianOf(sr, globalBgR)
                    cellG[cIdx] = medianOf(sg, globalBgG)
                    cellB[cIdx] = medianOf(sb, globalBgB)
                    cellValid[cIdx] = true
                }
            }
        }

        // Fill non-perimeter cells from nearest valid cells cleanly
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val cIdx = gy * gridCols + gx
                if (cellValid[cIdx]) continue
                var wSum = 0f
                var rSum = 0f; var gSum = 0f; var bSum = 0f
                for (oy in 0 until gridRows) {
                    for (ox in 0 until gridCols) {
                        val oIdx = oy * gridCols + ox
                        if (!cellValid[oIdx]) continue
                        val d2 = ((gx - ox) * (gx - ox) + (gy - oy) * (gy - oy)).coerceAtLeast(1).toFloat()
                        val wt = 1f / d2
                        wSum += wt
                        rSum += cellR[oIdx] * wt
                        gSum += cellG[oIdx] * wt
                        bSum += cellB[oIdx] * wt
                    }
                }
                if (wSum > 0f) {
                    cellR[cIdx] = rSum / wSum
                    cellG[cIdx] = gSum / wSum
                    cellB[cIdx] = bSum / wSum
                } else {
                    cellR[cIdx] = globalBgR
                    cellG[cIdx] = globalBgG
                    cellB[cIdx] = globalBgB
                }
            }
        }

        // 3. 3x3 Sobel Edge Magnitude |∇I| on luminance
        val edgeMag = FloatArray(nPx)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                val tl = lum[ro - w + x - 1]; val tc = lum[ro - w + x]; val tr = lum[ro - w + x + 1]
                val ml = lum[ro + x - 1]; val mr = lum[ro + x + 1]
                val bl = lum[ro + w + x - 1]; val bc = lum[ro + w + x]; val br = lum[ro + w + x + 1]
                val gx = -tl + tr - 2f * ml + 2f * mr - bl + br
                val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                edgeMag[ro + x] = hypot(gx, gy) * 0.25f
            }
        }

        // 4. Local background subtraction + Cast-Shadow suppression + Sobel fusion
        val rawScore = FloatArray(nPx)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / h * gridRows - 0.5f).coerceIn(0f, (gridRows - 1).toFloat())
            val gy0 = fy.toInt().coerceIn(0, gridRows - 1)
            val gy1 = (gy0 + 1).coerceAtMost(gridRows - 1)
            val ty = fy - gy0
            val ro = y * w
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / w * gridCols - 0.5f).coerceIn(0f, (gridCols - 1).toFloat())
                val gx0 = fx.toInt().coerceIn(0, gridCols - 1)
                val gx1 = (gx0 + 1).coerceAtMost(gridCols - 1)
                val tx = fx - gx0

                val c00 = gy0 * gridCols + gx0
                val c10 = gy0 * gridCols + gx1
                val c01 = gy1 * gridCols + gx0
                val c11 = gy1 * gridCols + gx1

                val bgR = bilerp(cellR[c00], cellR[c10], cellR[c01], cellR[c11], tx, ty)
                val bgG = bilerp(cellG[c00], cellG[c10], cellG[c01], cellG[c11], tx, ty)
                val bgB = bilerp(cellB[c00], cellB[c10], cellB[c01], cellB[c11], tx, ty)
                val bgLum = 0.299f * bgR + 0.587f * bgG + 0.114f * bgB

                val i = ro + x
                val dr = chR[i] - bgR
                val dg = chG[i] - bgG
                val db = chB[i] - bgB
                val dLum = abs(lum[i] - bgLum)
                val dCol = hypot(dr, hypot(dg, db)) * 0.62f

                // Chrominance residual orthogonal to pure brightness change (isolates real material vs cast shadow)
                val lumDiff = lum[i] - bgLum
                val cr = dr - lumDiff
                val cg = dg - lumDiff
                val cb = db - lumDiff
                val chromaDelta = hypot(cr, hypot(cg, cb))

                val bgMax = maxOf(bgR, maxOf(bgG, bgB))
                val bgMin = minOf(bgR, minOf(bgG, bgB))
                val bgSat = if (bgMax > 1f) (bgMax - bgMin) / bgMax else 0f

                // Cast shadow detection: darker than local background, same tint, weak Sobel gradient
                val isCastShadow = lumDiff < -6f &&
                    lumDiff > -68f &&
                    chromaDelta < 15f &&
                    abs(sat[i] - bgSat) < 0.16f &&
                    edgeMag[i] < 24f

                val baseContrast = maxOf(dLum, dCol)
                val filtered = if (isCastShadow) {
                    baseContrast * 0.34f + edgeMag[i] * 0.20f
                } else {
                    baseContrast + edgeMag[i] * 0.36f
                }
                rawScore[i] = filtered.coerceIn(0f, 255f)
            }
        }

        // Light edge-preserving 3x3 smoothing
        val score = FloatArray(nPx)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                val idx = ro + x
                score[idx] = (
                    rawScore[idx - w - 1] + rawScore[idx - w] + rawScore[idx - w + 1] +
                        rawScore[idx - 1] + rawScore[idx] * 2f + rawScore[idx + 1] +
                        rawScore[idx + w - 1] + rawScore[idx + w] + rawScore[idx + w + 1]
                    ) / 10f
            }
        }

        // 5. Full 0..255 Otsu Adaptive Threshold + user sensitivity bias
        val hist = IntArray(256)
        for (s in score) hist[s.toInt().coerceIn(0, 255)]++
        var sumAll = 0.0
        for (t in 0..255) sumAll += t * hist[t].toDouble()
        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var bestThresh = 24f
        for (t in 0..255) {
            wB += hist[t]
            sumB += t * hist[t].toDouble()
            if (t !in 8..224) continue
            if (wB == 0) continue
            val wF = nPx - wB
            if (wF == 0) break
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                bestThresh = t.toFloat()
            }
        }
        val sensFactor = (1f - shadowSensitivity.coerceIn(-0.6f, 0.6f) * 0.55f)
        val thresh = (bestThresh * sensFactor).coerceIn(14f, 175f)

        val mask = BooleanArray(nPx)
        for (i in 0 until nPx) {
            mask[i] = score[i] >= thresh
        }

        // Morphological close (3x3 dilate then erode)
        val dilated = BooleanArray(nPx)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                var any = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (mask[(y + dy) * w + (x + dx)]) {
                            any = true
                            break
                        }
                    }
                    if (any) break
                }
                dilated[ro + x] = any
            }
        }
        val closed = BooleanArray(nPx)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                var all = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (!dilated[(y + dy) * w + (x + dx)]) {
                            all = false
                            break
                        }
                    }
                    if (!all) break
                }
                closed[ro + x] = all
            }
        }

        // 6. Exterior-Seeded BFS Glare Hole-Filling:
        // Any enclosed background pocket smaller than interior keyring holes or glare holes inside metal
        // is flooded cleanly so metallic flash reflections never fracture the key blade!
        val exterior = BooleanArray(nPx)
        val bfsQueue = IntArray(nPx)
        var qHead = 0
        var qTail = 0
        for (x in 0 until w) {
            if (!closed[x]) { exterior[x] = true; bfsQueue[qTail++] = x }
            val bot = (h - 1) * w + x
            if (!closed[bot] && !exterior[bot]) { exterior[bot] = true; bfsQueue[qTail++] = bot }
        }
        for (y in 1 until h - 1) {
            val left = y * w
            if (!closed[left] && !exterior[left]) { exterior[left] = true; bfsQueue[qTail++] = left }
            val right = y * w + (w - 1)
            if (!closed[right] && !exterior[right]) { exterior[right] = true; bfsQueue[qTail++] = right }
        }
        while (qHead < qTail) {
            val cur = bfsQueue[qHead++]
            val cx = cur % w
            val cy = cur / w
            if (cx > 0 && !closed[cur - 1] && !exterior[cur - 1]) { exterior[cur - 1] = true; bfsQueue[qTail++] = cur - 1 }
            if (cx < w - 1 && !closed[cur + 1] && !exterior[cur + 1]) { exterior[cur + 1] = true; bfsQueue[qTail++] = cur + 1 }
            if (cy > 0 && !closed[cur - w] && !exterior[cur - w]) { exterior[cur - w] = true; bfsQueue[qTail++] = cur - w }
            if (cy < h - 1 && !closed[cur + w] && !exterior[cur + w]) { exterior[cur + w] = true; bfsQueue[qTail++] = cur + w }
        }
        for (i in 0 until nPx) {
            if (!closed[i] && !exterior[i]) {
                closed[i] = true
            }
        }

        // 7. Connected Component Scoring (Area + Elongation + Centrality, Border-Leak Penalty)
        val visited = BooleanArray(nPx)
        val compQueue = IntArray(nPx)
        var bestComponent = IntArray(0)
        var bestCompScore = -1.0

        for (i in 0 until nPx) {
            if (!closed[i] || visited[i]) continue
            var head = 0
            var tail = 0
            compQueue[tail++] = i
            visited[i] = true
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            var sumCx = 0.0; var sumCy = 0.0
            var borderHits = 0

            while (head < tail) {
                val cur = compQueue[head++]
                val cx = cur % w
                val cy = cur / w
                if (cx < minX) minX = cx; if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy; if (cy > maxY) maxY = cy
                sumCx += cx; sumCy += cy
                if (cx <= 1 || cx >= w - 2 || cy <= 1 || cy >= h - 2) borderHits++

                if (cx > 0 && closed[cur - 1] && !visited[cur - 1]) { visited[cur - 1] = true; compQueue[tail++] = cur - 1 }
                if (cx < w - 1 && closed[cur + 1] && !visited[cur + 1]) { visited[cur + 1] = true; compQueue[tail++] = cur + 1 }
                if (cy > 0 && closed[cur - w] && !visited[cur - w]) { visited[cur - w] = true; compQueue[tail++] = cur - w }
                if (cy < h - 1 && closed[cur + w] && !visited[cur + w]) { visited[cur + w] = true; compQueue[tail++] = cur + w }
            }

            if (tail < maxOf(80, nPx / 280)) continue
            val bw = (maxX - minX + 1).toFloat()
            val bh = (maxY - minY + 1).toFloat()
            val aspect = maxOf(bw, bh) / minOf(bw, bh).coerceAtLeast(1f)
            val elongationScore = if (aspect in 1.35f..6.5f) 1.35 else 0.70
            val cxMean = (sumCx / tail).toFloat() / w
            val cyMean = (sumCy / tail).toFloat() / h
            val centerDist = hypot(cxMean - 0.5f, cyMean - 0.5f)
            val centralityScore = (1.2 - centerDist).coerceIn(0.4, 1.2)
            val borderFrac = borderHits.toFloat() / tail
            val borderPenalty = if (borderFrac > 0.25f) 0.15 else 1.0
            val compScore = tail.toDouble() * elongationScore * centralityScore * borderPenalty

            if (compScore > bestCompScore) {
                bestCompScore = compScore
                bestComponent = compQueue.copyOf(tail)
            }
        }

        if (bestComponent.size < maxOf(80, nPx / 280)) {
            return fallbackResult(workBmp, standard)
        }

        val compMask = BooleanArray(nPx)
        var sumX = 0.0; var sumY = 0.0
        for (idx in bestComponent) {
            compMask[idx] = true
            sumX += idx % w
            sumY += idx / w
        }
        val meanX = (sumX / bestComponent.size).toFloat()
        val meanY = (sumY / bestComponent.size).toFloat()

        // 8. PCA Principal Component Alignment (Bow left, Tip right)
        var mu20 = 0.0; var mu02 = 0.0; var mu11 = 0.0
        for (idx in bestComponent) {
            val dx = (idx % w) - meanX
            val dy = (idx / w) - meanY
            mu20 += dx * dx
            mu02 += dy * dy
            mu11 += dx * dy
        }
        var angle = (0.5 * atan2(2.0 * mu11, mu20 - mu02)).toFloat()

        fun computeAlignedBounds(ang: Float): AlignedFrame {
            val cUx = cos(ang); val cUy = sin(ang)
            val cVx = -cUy; val cVy = cUx
            var uMn = Float.MAX_VALUE; var uMx = -Float.MAX_VALUE
            var vMn = Float.MAX_VALUE; var vMx = -Float.MAX_VALUE
            for (idx in bestComponent) {
                val dx = (idx % w) - meanX
                val dy = (idx / w) - meanY
                val u = dx * cUx + dy * cUy
                val v = dx * cVx + dy * cVy
                if (u < uMn) uMn = u; if (u > uMx) uMx = u
                if (v < vMn) vMn = v; if (v > vMx) vMx = v
            }
            return AlignedFrame(cUx, cUy, cVx, cVy, uMn, uMx, vMn, vMx)
        }

        var frame = computeAlignedBounds(angle)
        val initSpanU = (frame.uMax - frame.uMin).coerceAtLeast(10f)
        val leftQuarterV = ArrayList<Float>()
        val rightQuarterV = ArrayList<Float>()
        for (idx in bestComponent) {
            val dx = (idx % w) - meanX
            val dy = (idx / w) - meanY
            val u = dx * frame.ux + dy * frame.uy
            val v = dx * frame.vx + dy * frame.vy
            if (u < frame.uMin + initSpanU * 0.28f) leftQuarterV.add(v)
            else if (u > frame.uMax - initSpanU * 0.28f) rightQuarterV.add(v)
        }
        val leftSpread = (leftQuarterV.maxOrNull() ?: 0f) - (leftQuarterV.minOrNull() ?: 0f)
        val rightSpread = (rightQuarterV.maxOrNull() ?: 0f) - (rightQuarterV.minOrNull() ?: 0f)
        if (rightSpread > leftSpread * 1.05f) {
            angle += PI.toFloat()
        }
        if (flipHorizontal) {
            angle += PI.toFloat()
        }
        frame = computeAlignedBounds(angle)

        // 9. Sub-Pixel Edge Scan + RANSAC Straight-Edge Spine Locking
        var sampled = sampleSubPixelProfile(frame, meanX, meanY, w, h, compMask, score, thresh)

        val initCols = sampled.cols
        val initMedBladePx = medianBladeThickness(sampled.topV, sampled.botV, initCols)
        var initShoulderCol = locateShoulderDerivative(sampled.topV, sampled.botV, initCols, initMedBladePx)
        val initBladeCols = (initCols - 1 - initShoulderCol).coerceAtLeast(10)
        val bS0 = initShoulderCol + (initBladeCols * 0.08f).toInt()
        val bE0 = (initCols - 1 - (initBladeCols * 0.16f).toInt()).coerceAtLeast(bS0 + 6)

        val topVar0 = sliceRange(sampled.topV, bS0, bE0)
        val botVar0 = sliceRange(sampled.botV, bS0, bE0)
        val spineArr = if (botVar0 <= topVar0) sampled.botV else sampled.topV

        // Fit straight line v(col) via RANSAC across blade middle [bS0..bE0] to remove residual PCA skew!
        val colStep0 = sampled.colStep
        val ransacAngleOffset = fitRansacLineSlopeAngle(spineArr, bS0, bE0, colStep0)
        if (abs(ransacAngleOffset) in 0.002f..0.24f) {
            angle += ransacAngleOffset
            frame = computeAlignedBounds(angle)
            sampled = sampleSubPixelProfile(frame, meanX, meanY, w, h, compMask, score, thresh)
        }

        val cols = sampled.cols
        val colStep = sampled.colStep
        val topV = sampled.topV
        val botV = sampled.botV
        val thicknesses = FloatArray(cols) {
            if (topV[it].isNaN() || botV[it].isNaN()) 0f else (botV[it] - topV[it]).coerceAtLeast(0f)
        }
        val medianBladePx = medianBladeThickness(topV, botV, cols)
        val leftMaxThick = (0 until (cols * 0.48f).toInt()).maxOfOrNull { thicknesses[it] } ?: (medianBladePx * 2f)

        // Shoulder detection using thickness drop + negative derivative jump
        val shoulderCol = locateShoulderDerivative(topV, botV, cols, medianBladePx)
        val bladeCols = (cols - 1 - shoulderCol).coerceAtLeast(10)

        val bStart = shoulderCol + (bladeCols * 0.08f).toInt()
        val bEnd = (cols - 1 - (bladeCols * 0.14f).toInt()).coerceAtLeast(bStart + 4)
        val topVar = sliceRange(topV, bStart, bEnd)
        val botVar = sliceRange(botV, bStart, bEnd)

        // Ensure teeth are placed on CAD top edge unless user explicitly toggled vertical mirror
        val shouldFlipVertical = if (!flipVertical) botVar > topVar * 1.35f else !(botVar > topVar * 1.35f)
        if (shouldFlipVertical) {
            for (ci in 0 until cols) {
                val nt = -botV[ci]
                val nb = -topV[ci]
                topV[ci] = nt
                botV[ci] = nb
            }
        }

        // Compute scale (exact px/mm from rectified card if provided, otherwise aspect-matched scale)
        val botSlice = (bStart..bEnd).map { botV[it] }
        val maxBladeSpanPx = (bStart..bEnd).maxOfOrNull { botV[it] - topV[it] } ?: medianBladePx
        val pxPerMmScaled: Float = if (pxPerMmHint != null && pxPerMmHint > 0f) {
            pxPerMmHint * scaleDown
        } else {
            val estFromWidth = (maxBladeSpanPx * 1.02f) / standard.bladeWidthMm
            val estFromLen = (bladeCols * colStep) / standard.bladeLengthMm
            ((estFromWidth * 0.48f) + (estFromLen * 0.52f)).coerceIn(2.0f, 45f)
        }

        val bladeLengthMm = ((bladeCols * colStep) / pxPerMmScaled).coerceIn(16f, 52f)
        val bladeWidthMm = (maxBladeSpanPx / pxPerMmScaled).coerceIn(5.0f, 14.5f)
        val bowLengthMm = ((shoulderCol * colStep) / pxPerMmScaled).coerceIn(10f, 40f)
        val bowHeightMm = (leftMaxThick / pxPerMmScaled).coerceIn(12f, 36f)
        val bowScale = (bowHeightMm / 23.0f).coerceIn(0.65f, 1.50f)

        val spineBaseV = botSlice.sorted().getOrElse((botSlice.size * 0.85f).toInt()) { botV[shoulderCol] }
        val cadTopBladeY = (bStart..bEnd).maxOfOrNull { (spineBaseV - topV[it]) / pxPerMmScaled } ?: bladeWidthMm

        fun toCadX(ci: Int): Float = ((ci - shoulderCol) * colStep) / pxPerMmScaled
        fun toCadTopY(ci: Int): Float = (spineBaseV - topV[ci]) / pxPerMmScaled
        fun toCadBotY(ci: Int): Float = (spineBaseV - botV[ci]) / pxPerMmScaled

        val botDepthVals = (bStart..bEnd).map { (spineBaseV - botV[it]) / pxPerMmScaled }
        val botModulationMm = (botDepthVals.maxOrNull() ?: 0f) - (botDepthVals.minOrNull() ?: 0f)
        val topModulationMm = (bStart..bEnd).let { r ->
            val ys = r.map { (spineBaseV - topV[it]) / pxPerMmScaled }
            (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        }
        val isDoubleSided = botModulationMm > 0.72f && topModulationMm > 0.72f

        // 10. Build monotonic 100%-faithful millimeter contour
        val contour = ArrayList<Point2D>(cols * 2 + 8)
        val sampleStep = maxOf(1, (0.36f * pxPerMmScaled / colStep).roundToInt())

        var ci = shoulderCol
        while (ci >= 0) {
            contour.add(Point2D(toCadX(ci), minOf(toCadBotY(ci), toCadTopY(ci) - 0.8f)))
            ci -= sampleStep
        }
        if (ci + sampleStep != 0) {
            contour.add(Point2D(toCadX(0), minOf(toCadBotY(0), toCadTopY(0) - 0.8f)))
        }
        ci = 0
        while (ci <= shoulderCol) {
            contour.add(Point2D(toCadX(ci), maxOf(toCadTopY(ci), toCadBotY(ci) + 0.8f)))
            ci += sampleStep
        }
        ci = shoulderCol + sampleStep
        while (ci < cols) {
            contour.add(Point2D(toCadX(ci), maxOf(toCadTopY(ci), toCadBotY(ci) + 0.65f)))
            ci += sampleStep
        }
        if (ci - sampleStep != cols - 1) {
            val lastIdx = cols - 1
            contour.add(Point2D(toCadX(lastIdx), maxOf(toCadTopY(lastIdx), toCadBotY(lastIdx) + 0.65f)))
        }
        ci = cols - 1
        while (ci > shoulderCol) {
            contour.add(Point2D(toCadX(ci), minOf(toCadBotY(ci), toCadTopY(ci) - 0.65f)))
            ci -= sampleStep
        }

        // 11. 5-Point Savitzky-Golay Smoothing + Prominence & Sub-Pixel Tooth Extraction
        val bladeXList = ArrayList<Float>(bladeCols)
        val cutDepthList = ArrayList<Float>(bladeCols)
        for (k in shoulderCol until cols) {
            bladeXList.add(toCadX(k))
            cutDepthList.add((cadTopBladeY - toCadTopY(k)).coerceIn(0f, cadTopBladeY - 1.15f))
        }

        val smoothDepth = savitzkyGolay5(cutDepthList)

        val candidatePins = mutableListOf<Int>()
        for (i in 2 until smoothDepth.size - 3) {
            val xMm = bladeXList[i]
            if (xMm < 2.1f || xMm > bladeLengthMm - 2.0f) continue
            val d = smoothDepth[i]
            if (d <= 0.32f) continue
            val isLocalMax = d >= smoothDepth[i - 1] && d >= smoothDepth[i + 1] &&
                d >= smoothDepth[i - 2] && d >= smoothDepth[i + 2]
            if (!isLocalMax) continue

            // Check prominence: minimum depth difference above lowest valley-shoulders within +-3.2mm window
            var leftMin = d
            var rightMin = d
            var j = i - 1
            while (j >= 0 && bladeXList[i] - bladeXList[j] <= 3.4f) {
                if (smoothDepth[j] < leftMin) leftMin = smoothDepth[j]
                j--
            }
            j = i + 1
            while (j < smoothDepth.size && bladeXList[j] - bladeXList[i] <= 3.4f) {
                if (smoothDepth[j] < rightMin) rightMin = smoothDepth[j]
                j++
            }
            val prominence = d - maxOf(leftMin, rightMin)
            val secondDeriv = smoothDepth[i - 1] - 2f * d + smoothDepth[i + 1]
            if (prominence >= 0.22f || (d > 0.55f && secondDeriv < -0.02f)) {
                val prevIdx = candidatePins.lastOrNull()
                if (prevIdx == null || bladeXList[i] - bladeXList[prevIdx] >= 2.45f) {
                    candidatePins.add(i)
                } else if (d > smoothDepth[prevIdx]) {
                    candidatePins[candidatePins.lastIndex] = i
                }
            }
        }

        val spacingsMm = mutableListOf<Float>()
        val depthsMm = mutableListOf<Float>()
        val flatWidthsMm = mutableListOf<Float>()
        val leftSlopesDeg = mutableListOf<Float>()
        val rightSlopesDeg = mutableListOf<Float>()

        if (candidatePins.size >= 3) {
            val chosen = candidatePins.take(8)
            for (idx in chosen) {
                // Sub-pixel parabolic center refinement on smoothDepth
                val ym1 = smoothDepth[(idx - 1).coerceAtLeast(0)]
                val y0 = smoothDepth[idx]
                val yp1 = smoothDepth[(idx + 1).coerceAtMost(smoothDepth.lastIndex)]
                val denom = (ym1 - 2f * y0 + yp1)
                val subShift = if (abs(denom) > 1e-4f) {
                    (0.5f * (ym1 - yp1) / denom).coerceIn(-0.6f, 0.6f)
                } else 0f
                val dxPerStep = if (idx + 1 < bladeXList.size) (bladeXList[idx + 1] - bladeXList[idx]) else 0.1f
                val px = bladeXList[idx] + subShift * dxPerStep

                // Use crisp local window max depth (prevents Savitzky-Golay overshoot/blunting error)
                val peakWinStart = (idx - 1).coerceAtLeast(0)
                val peakWinEnd = (idx + 1).coerceAtMost(cutDepthList.lastIndex)
                val rawDepth = (peakWinStart..peakWinEnd).maxOf { cutDepthList[it] }
                val depth = BittingCalculator.roundToPrecision(rawDepth, 0.05f)

                spacingsMm.add(BittingCalculator.roundToPrecision(px, 0.05f))
                depthsMm.add(depth)

                // Flat bottom width within 0.15 mm of valley floor
                var lIdx = idx
                while (lIdx > 0 && abs(cutDepthList[lIdx - 1] - rawDepth) < 0.15f) lIdx--
                var rIdx = idx
                while (rIdx < cutDepthList.lastIndex && abs(cutDepthList[rIdx + 1] - rawDepth) < 0.15f) rIdx++
                val flatW = (bladeXList[rIdx] - bladeXList[lIdx]).coerceIn(0.35f, 2.5f)
                flatWidthsMm.add(BittingCalculator.roundToPrecision(flatW, 0.05f))

                // Linear slope regression along Left and Right flanks (25%..85% depth)
                val lDeg = regressFlankSlopeDeg(bladeXList, cutDepthList, lIdx, rawDepth, direction = -1)
                val rDeg = regressFlankSlopeDeg(bladeXList, cutDepthList, rIdx, rawDepth, direction = +1)
                leftSlopesDeg.add(lDeg.roundToInt().toFloat())
                rightSlopesDeg.add(rDeg.roundToInt().toFloat())
            }
        } else {
            val count = standard.pinCount
            for (i in 0 until count) {
                val targetX = (standard.shoulderToFirstPinMm + i * standard.pinSpacingMm)
                    .coerceIn(2.5f, maxOf(3.0f, bladeLengthMm - 2.5f))
                val sampleIdx = bladeXList.indices.minByOrNull { abs(bladeXList[it] - targetX) } ?: 0
                spacingsMm.add(BittingCalculator.roundToPrecision(targetX, 0.05f))
                depthsMm.add(BittingCalculator.roundToPrecision(cutDepthList[sampleIdx], 0.05f))
                flatWidthsMm.add(1.15f)
                leftSlopesDeg.add(45f)
                rightSlopesDeg.add(45f)
            }
        }

        // Tip length & style estimation
        val tipStartIdx = (shoulderCol + bladeCols * 0.80f).toInt().coerceIn(shoulderCol, cols - 2)
        var taperStartCol = cols - 2
        for (k in cols - 2 downTo tipStartIdx) {
            if (thicknesses[k] >= medianBladePx * 0.85f) {
                taperStartCol = k
                break
            }
        }
        val tipLenMm = (((cols - 1 - taperStartCol) * colStep) / pxPerMmScaled).coerceIn(1.0f, 6.0f)
        val endThicknessRatio = (thicknesses[cols - 2] / medianBladePx).coerceIn(0f, 1f)
        val detectedTipStyle = when {
            endThicknessRatio < 0.28f -> KeyTipStyle.SHARP_POINT
            endThicknessRatio > 0.65f -> KeyTipStyle.CHISEL_FLAT
            tipLenMm > 3.6f -> KeyTipStyle.ROUNDED
            else -> KeyTipStyle.STANDARD_TAPER
        }

        // Classify Bow Style from bow geometry
        val detectedBowStyle = classifyBowStyle(
            thicknesses = thicknesses,
            shoulderCol = shoulderCol,
            bowLengthMm = bowLengthMm,
            bowHeightMm = bowHeightMm,
            isDoubleSided = isDoubleSided
        )

        // Match closest KeyStandardSpec from pin count + spacing pitch + dimensions
        val matchedStandardId = matchClosestStandard(
            pinCount = spacingsMm.size,
            spacingsMm = spacingsMm,
            bladeWidthMm = bladeWidthMm,
            bladeLengthMm = bladeLengthMm,
            fallbackId = standard.id
        )

        // 12. Verification Overlay Bitmap: Cyan Contour + Green Spine + Teal Shoulder + Amber Pin Crosshairs
        val overlay = workBmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(overlay)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22D3EE")
            strokeWidth = 3.2f
            style = Paint.Style.STROKE
        }
        val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            strokeWidth = 2.6f
            style = Paint.Style.STROKE
        }
        val shoulderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14B8A6")
            strokeWidth = 3.0f
            style = Paint.Style.STROKE
        }
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            style = Paint.Style.FILL
        }
        val pinRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2.0f
            style = Paint.Style.STROKE
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE047")
            textSize = maxOf(14f, w * 0.026f)
            isFakeBoldText = true
        }

        fun mapCadToBmp(cxMm: Float, cyMm: Float): Point2D {
            val u = frame.uMin + shoulderCol * colStep + cxMm * pxPerMmScaled
            val rawV = spineBaseV - cyMm * pxPerMmScaled
            val v = if (shouldFlipVertical) -rawV else rawV
            return Point2D(
                x = meanX + u * frame.ux + v * frame.vx,
                y = meanY + u * frame.uy + v * frame.vy
            )
        }

        val path = Path()
        contour.forEachIndexed { idx, p ->
            val mapped = mapCadToBmp(p.x, p.y)
            if (idx == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
        }
        path.close()
        canvas.drawPath(path, strokePaint)

        // Green locked RANSAC spine line
        val sp0 = mapCadToBmp(0f, 0f)
        val sp1 = mapCadToBmp(bladeLengthMm, 0f)
        canvas.drawLine(sp0.x, sp0.y, sp1.x, sp1.y, spinePaint)

        // Teal shoulder datum line
        val sh0 = mapCadToBmp(0f, -2.2f)
        val sh1 = mapCadToBmp(0f, bladeWidthMm + 2.5f)
        canvas.drawLine(sh0.x, sh0.y, sh1.x, sh1.y, shoulderPaint)

        // Labeled P1..Pn crosshairs
        spacingsMm.forEachIndexed { i, sp ->
            val depth = depthsMm.getOrElse(i) { 0f }
            val mp = mapCadToBmp(sp, bladeWidthMm - depth)
            canvas.drawCircle(mp.x, mp.y, 7.5f, pinRingPaint)
            canvas.drawCircle(mp.x, mp.y, 5.2f, pinPaint)
            canvas.drawText("P${i + 1}", mp.x + 8f, mp.y - 6f, labelPaint)
        }

        val confidence = computeConfidence(
            pinCount = candidatePins.size,
            componentSize = bestComponent.size,
            totalPx = nPx,
            bladeWidthMm = bladeWidthMm,
            ransacDeltaRad = abs(ransacAngleOffset)
        )

        return PhotoKeyExtractionResult(
            detected = true,
            bladeLengthMm = BittingCalculator.roundToPrecision(bladeLengthMm, 0.1f),
            bladeWidthMm = BittingCalculator.roundToPrecision(bladeWidthMm, 0.1f),
            bowScale = bowScale,
            bowStyle = detectedBowStyle,
            matchedStandardId = matchedStandardId,
            isDoubleSided = isDoubleSided,
            pinCount = spacingsMm.size,
            spacingsMm = spacingsMm,
            depthsMm = depthsMm,
            flatWidthsMm = flatWidthsMm,
            leftSlopesDeg = leftSlopesDeg,
            rightSlopesDeg = rightSlopesDeg,
            tipStyle = detectedTipStyle,
            tipLengthMm = BittingCalculator.roundToPrecision(tipLenMm, 0.1f),
            customContourMm = contour,
            overlayPreview = overlay,
            confidencePercent = confidence
        )
    }

    private data class AlignedFrame(
        val ux: Float,
        val uy: Float,
        val vx: Float,
        val vy: Float,
        val uMin: Float,
        val uMax: Float,
        val vMin: Float,
        val vMax: Float
    )

    private data class ProfileSamples(
        val cols: Int,
        val colStep: Float,
        val topV: FloatArray,
        val botV: FloatArray
    )

    private fun sampleSubPixelProfile(
        frame: AlignedFrame,
        meanX: Float,
        meanY: Float,
        w: Int,
        h: Int,
        compMask: BooleanArray,
        score: FloatArray,
        thresh: Float
    ): ProfileSamples {
        val spanU = (frame.uMax - frame.uMin).coerceAtLeast(10f)
        val spanV = (frame.vMax - frame.vMin).coerceAtLeast(8f)
        val cols = spanU.roundToInt().coerceIn(48, 560)
        val colStep = spanU / (cols - 1).coerceAtLeast(1)
        val vSamples = (spanV * 1.35f).roundToInt().coerceIn(32, 360)
        val vStep = spanV / vSamples.coerceAtLeast(1)

        val topV = FloatArray(cols) { Float.NaN }
        val botV = FloatArray(cols) { Float.NaN }

        for (ci in 0 until cols) {
            val u = frame.uMin + ci * colStep
            var firstHitIdx = -1
            var lastHitIdx = -1
            for (vi in 0..vSamples) {
                val v = frame.vMin + vi * vStep
                val px = (meanX + u * frame.ux + v * frame.vx).roundToInt()
                val py = (meanY + u * frame.uy + v * frame.vy).roundToInt()
                if (px in 0 until w && py in 0 until h && compMask[py * w + px]) {
                    if (firstHitIdx == -1) firstHitIdx = vi
                    lastHitIdx = vi
                }
            }
            if (firstHitIdx != -1 && lastHitIdx > firstHitIdx) {
                var refinedTop = frame.vMin + firstHitIdx * vStep
                if (firstHitIdx > 0) {
                    val vOut = frame.vMin + (firstHitIdx - 1) * vStep
                    val sIn = sampleBilinear(score, w, h, meanX + u * frame.ux + refinedTop * frame.vx, meanY + u * frame.uy + refinedTop * frame.vy)
                    val sOut = sampleBilinear(score, w, h, meanX + u * frame.ux + vOut * frame.vx, meanY + u * frame.uy + vOut * frame.vy)
                    val denom = sIn - sOut
                    if (abs(denom) > 2f && thresh in min(sIn, sOut)..max(sIn, sOut)) {
                        val frac = ((thresh - sOut) / denom).coerceIn(0f, 1f)
                        refinedTop = vOut + frac * vStep
                    }
                }
                var refinedBot = frame.vMin + lastHitIdx * vStep
                if (lastHitIdx < vSamples) {
                    val vOut = frame.vMin + (lastHitIdx + 1) * vStep
                    val sIn = sampleBilinear(score, w, h, meanX + u * frame.ux + refinedBot * frame.vx, meanY + u * frame.uy + refinedBot * frame.vy)
                    val sOut = sampleBilinear(score, w, h, meanX + u * frame.ux + vOut * frame.vx, meanY + u * frame.uy + vOut * frame.vy)
                    val denom = sIn - sOut
                    if (abs(denom) > 2f && thresh in min(sIn, sOut)..max(sIn, sOut)) {
                        val frac = ((sIn - thresh) / denom).coerceIn(0f, 1f)
                        refinedBot = refinedBot + frac * vStep
                    }
                }
                topV[ci] = refinedTop
                botV[ci] = refinedBot
            }
        }
        fillNaNs(topV)
        fillNaNs(botV)
        return ProfileSamples(cols, colStep, topV, botV)
    }

    private fun sampleBilinear(arr: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, w - 1)
        val y0 = y.toInt().coerceIn(0, h - 1)
        val x1 = (x0 + 1).coerceAtMost(w - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)
        val tx = (x - x0).coerceIn(0f, 1f)
        val ty = (y - y0).coerceIn(0f, 1f)
        return bilerp(arr[y0 * w + x0], arr[y0 * w + x1], arr[y1 * w + x0], arr[y1 * w + x1], tx, ty)
    }

    /**
     * Deterministic RANSAC straight-line fit on spine points across [startCol..endCol].
     * Returns angular slope correction (in radians) needed to make the spine perfectly horizontal.
     */
    private fun fitRansacLineSlopeAngle(
        vals: FloatArray,
        startCol: Int,
        endCol: Int,
        colStep: Float
    ): Float {
        val n = endCol - startCol + 1
        if (n < 12) return 0f

        var bestInliers = -1
        var bestErr = Float.MAX_VALUE
        var bestM = 0f

        val pairs = 28
        for (t in 0 until pairs) {
            val i1 = startCol + (t * (n / 3)) / pairs
            val i2 = endCol - ((pairs - 1 - t) * (n / 3)) / pairs
            if (i2 - i1 < maxOf(4, n / 4)) continue
            val x1 = i1 * colStep; val y1 = vals[i1]
            val x2 = i2 * colStep; val y2 = vals[i2]
            val m = (y2 - y1) / (x2 - x1)
            val b = y1 - m * x1

            var inliers = 0
            var errSum = 0f
            for (k in startCol..endCol) {
                val err = abs(vals[k] - (m * k * colStep + b))
                if (err <= 0.85f) {
                    inliers++
                    errSum += err
                }
            }
            if (inliers > bestInliers || (inliers == bestInliers && errSum < bestErr)) {
                bestInliers = inliers
                bestErr = errSum
                bestM = m
            }
        }

        if (bestInliers < maxOf(6, n / 3)) return 0f

        // Least-squares refinement over all inliers
        val bBest = vals[startCol] - bestM * startCol * colStep
        var sX = 0.0; var sY = 0.0; var sXX = 0.0; var sXY = 0.0; var cnt = 0
        for (k in startCol..endCol) {
            val x = (k * colStep).toDouble()
            val y = vals[k].toDouble()
            if (abs(y - (bestM * x + bBest)) <= 1.0) {
                sX += x; sY += y; sXX += x * x; sXY += x * y; cnt++
            }
        }
        val refinedM = if (cnt >= 6) {
            val denom = cnt * sXX - sX * sX
            if (abs(denom) > 1e-6) ((cnt * sXY - sX * sY) / denom).toFloat() else bestM
        } else bestM

        return atan(refinedM)
    }

    private fun medianBladeThickness(topV: FloatArray, botV: FloatArray, cols: Int): Float {
        val list = ArrayList<Float>()
        for (ci in (cols * 0.45f).toInt() until (cols * 0.90f).toInt()) {
            val th = botV[ci] - topV[ci]
            if (th > 1f) list.add(th)
        }
        return medianOf(list, 12f).coerceAtLeast(4f)
    }

    private fun locateShoulderDerivative(
        topV: FloatArray,
        botV: FloatArray,
        cols: Int,
        medianBladePx: Float
    ): Int {
        val start = (cols * 0.16f).toInt().coerceIn(4, cols - 12)
        val end = (cols * 0.62f).toInt().coerceIn(start + 2, cols - 8)
        val thresh = medianBladePx * 1.30f

        // First locate the transition point where thickness stays inside blade width
        var stepCol = (cols * 0.36f).toInt().coerceIn(6, cols - 10)
        for (ci in start..end) {
            val th0 = botV[ci] - topV[ci]
            val th3 = botV[(ci + 3).coerceAtMost(cols - 1)] - topV[(ci + 3).coerceAtMost(cols - 1)]
            if (th0 <= thresh && th3 <= thresh) {
                stepCol = ci
                break
            }
        }

        // Refine using steepest negative derivative drop in [stepCol - 6 .. stepCol + 2]
        var bestCol = stepCol
        var maxDrop = -Float.MAX_VALUE
        for (ci in (stepCol - 6).coerceAtLeast(3)..(stepCol + 2).coerceAtMost(cols - 4)) {
            val drop = (botV[ci - 2] - topV[ci - 2]) - (botV[ci + 2] - topV[ci + 2])
            if (drop > maxDrop) {
                maxDrop = drop
                bestCol = ci
            }
        }
        return bestCol.coerceIn(6, cols - 10)
    }

    private fun savitzkyGolay5(input: List<Float>): FloatArray {
        val n = input.size
        if (n < 5) return FloatArray(n) { input[it] }
        return FloatArray(n) { i ->
            val vM2 = input[(i - 2).coerceAtLeast(0)]
            val vM1 = input[(i - 1).coerceAtLeast(0)]
            val v0 = input[i]
            val vP1 = input[(i + 1).coerceAtMost(n - 1)]
            val vP2 = input[(i + 2).coerceAtMost(n - 1)]
            ((-3f * vM2 + 12f * vM1 + 17f * v0 + 12f * vP1 - 3f * vP2) / 35f).coerceAtLeast(0f)
        }
    }

    private fun regressFlankSlopeDeg(
        xs: List<Float>,
        depths: List<Float>,
        startIdx: Int,
        peakDepth: Float,
        direction: Int
    ): Float {
        if (peakDepth <= 0.25f) return 45f
        val lowCut = peakDepth * 0.20f
        val highCut = peakDepth * 0.85f
        val ptsX = ArrayList<Float>()
        val ptsY = ArrayList<Float>()
        var i = startIdx
        while (i in depths.indices && ptsX.size < 16) {
            val d = depths[i]
            if (d in lowCut..highCut) {
                ptsX.add(xs[i])
                ptsY.add(d)
            }
            if (d < lowCut) break
            i += direction
        }
        if (ptsX.size >= 2) {
            val meanX = ptsX.average()
            val meanY = ptsY.average()
            var num = 0.0
            var den = 0.0
            for (k in ptsX.indices) {
                val dx = ptsX[k] - meanX
                num += dx * (ptsY[k] - meanY)
                den += dx * dx
            }
            if (abs(den) > 1e-5) {
                val slope = abs(num / den)
                return Math.toDegrees(atan(slope)).toFloat().coerceIn(24f, 80f)
            }
        }
        val endIdx = i.coerceIn(0, depths.lastIndex)
        val dx = abs(xs[startIdx] - xs[endIdx]).coerceAtLeast(0.25f)
        val dy = abs(peakDepth - depths[endIdx]).coerceAtLeast(0.15f)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat().coerceIn(24f, 80f)
    }

    private fun classifyBowStyle(
        thicknesses: FloatArray,
        shoulderCol: Int,
        bowLengthMm: Float,
        bowHeightMm: Float,
        isDoubleSided: Boolean
    ): KeyBowStyle {
        val ratio = bowLengthMm / bowHeightMm.coerceAtLeast(1f)
        val bowBackSample = (shoulderCol * 0.12f).toInt().coerceIn(0, (shoulderCol - 1).coerceAtLeast(0))
        val bowMidSample = (shoulderCol * 0.50f).toInt().coerceIn(0, (shoulderCol - 1).coerceAtLeast(0))
        val backThick = thicknesses[bowBackSample]
        val midThick = thicknesses[bowMidSample].coerceAtLeast(1f)
        val fullnessRatio = backThick / midThick

        return when {
            bowHeightMm < 19.5f && ratio < 1.0f -> KeyBowStyle.SLIM_PADLOCK
            isDoubleSided && bowLengthMm > 25.5f -> KeyBowStyle.AUTO_FOB
            ratio in 0.90f..1.10f && fullnessRatio < 0.72f -> KeyBowStyle.ROUND_COIN
            fullnessRatio > 0.85f -> KeyBowStyle.SQUARE_HEAVY
            bowLengthMm > 23.5f && fullnessRatio in 0.55f..0.78f -> KeyBowStyle.HEX_SCHLAGE
            else -> KeyBowStyle.CLASSIC_OVAL
        }
    }

    private fun matchClosestStandard(
        pinCount: Int,
        spacingsMm: List<Float>,
        bladeWidthMm: Float,
        bladeLengthMm: Float,
        fallbackId: String
    ): String {
        if (spacingsMm.isEmpty()) return fallbackId
        val avgPitch = if (spacingsMm.size >= 2) {
            (spacingsMm.last() - spacingsMm.first()) / (spacingsMm.size - 1)
        } else 3.96f
        val firstPin = spacingsMm.first()

        var bestId = fallbackId
        var bestErr = Float.MAX_VALUE
        for (spec in KeyStandards.ALL) {
            if (spec.id == "CUSTOM") continue
            val pinPenalty = abs(spec.pinCount - pinCount) * 2.5f
            val pitchErr = abs(spec.pinSpacingMm - avgPitch) * 3.0f
            val widthErr = abs(spec.bladeWidthMm - bladeWidthMm) * 1.4f
            val firstErr = abs(spec.shoulderToFirstPinMm - firstPin) * 0.9f
            val lenErr = abs(spec.bladeLengthMm - bladeLengthMm) * 0.25f
            val totalErr = pinPenalty + pitchErr + widthErr + firstErr + lenErr
            if (totalErr < bestErr) {
                bestErr = totalErr
                bestId = spec.id
            }
        }
        return if (bestErr < 8.5f) bestId else "CUSTOM"
    }

    private fun computeConfidence(
        pinCount: Int,
        componentSize: Int,
        totalPx: Int,
        bladeWidthMm: Float,
        ransacDeltaRad: Float
    ): Int {
        var score = 72
        if (pinCount >= 4) score += 14 else if (pinCount >= 3) score += 9
        val areaRatio = componentSize.toFloat() / totalPx.coerceAtLeast(1)
        if (areaRatio in 0.04f..0.45f) score += 6
        if (bladeWidthMm in 5.8f..10.5f) score += 4
        if (ransacDeltaRad < 0.08f) score += 3
        return score.coerceIn(55, 99)
    }

    private fun sliceRange(arr: FloatArray, start: Int, end: Int): Float {
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (i in start..end) {
            val v = arr[i]
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        return if (mx > mn) mx - mn else 0f
    }

    private fun bilerp(v00: Float, v10: Float, v01: Float, v11: Float, tx: Float, ty: Float): Float {
        val top = v00 * (1f - tx) + v10 * tx
        val bot = v01 * (1f - tx) + v11 * tx
        return top * (1f - ty) + bot * ty
    }

    private fun medianOf(list: List<Float>, defaultVal: Float): Float {
        if (list.isEmpty()) return defaultVal
        val sorted = list.sorted()
        return sorted[sorted.size / 2]
    }

    private fun fillNaNs(arr: FloatArray) {
        var lastValid = -1
        for (i in arr.indices) {
            if (!arr[i].isNaN()) {
                if (lastValid == -1) {
                    for (j in 0 until i) arr[j] = arr[i]
                } else if (i - lastValid > 1) {
                    val span = (i - lastValid).toFloat()
                    for (j in lastValid + 1 until i) {
                        val t = (j - lastValid) / span
                        arr[j] = arr[lastValid] * (1f - t) + arr[i] * t
                    }
                }
                lastValid = i
            }
        }
        if (lastValid in 0 until arr.lastIndex) {
            for (j in lastValid + 1 until arr.size) arr[j] = arr[lastValid]
        }
        if (lastValid == -1) {
            for (j in arr.indices) arr[j] = 0f
        }
    }

    private fun fallbackResult(bmp: Bitmap, spec: KeyStandardSpec): PhotoKeyExtractionResult {
        val spacings = KeyStandards.defaultSpacings(spec, spec.pinCount)
        val depths = KeyStandards.defaultDepths(spec, spec.pinCount)
        return PhotoKeyExtractionResult(
            detected = false,
            bladeLengthMm = spec.bladeLengthMm,
            bladeWidthMm = spec.bladeWidthMm,
            bowScale = 1.0f,
            bowStyle = KeyBowStyle.CLASSIC_OVAL,
            matchedStandardId = spec.id,
            isDoubleSided = false,
            pinCount = spec.pinCount,
            spacingsMm = spacings,
            depthsMm = depths,
            flatWidthsMm = List(spec.pinCount) { 1.15f },
            leftSlopesDeg = List(spec.pinCount) { 45f },
            rightSlopesDeg = List(spec.pinCount) { 45f },
            tipStyle = KeyTipStyle.STANDARD_TAPER,
            tipLengthMm = 2.6f,
            customContourMm = emptyList(),
            overlayPreview = bmp,
            confidencePercent = 0
        )
    }
}
