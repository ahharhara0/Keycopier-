package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure Kotlin 8-DOF Projective Homography solver & bilinear image rectifier.
 * Maps a tilted quadrilateral of an ISO/IEC 7810 ID-1 reference card (85.60 x 53.98 mm)
 * to a flat orthographic plane at a fixed px/mm resolution.
 */
object PerspectiveCalibrator {

    const val ID1_WIDTH_MM = 85.60f
    const val ID1_HEIGHT_MM = 53.98f
    const val DEFAULT_PX_PER_MM = 20f // 1712 x 1080 px rectified canvas

    /**
     * Computes 3x3 Homography matrix H (row-major, length 9) mapping srcPoints[i] -> dstPoints[i].
     * Returns null if points are degenerate / collinear.
     */
    fun computeHomography(
        srcPoints: List<Point2D>,
        dstPoints: List<Point2D>
    ): DoubleArray? {
        if (srcPoints.size != 4 || dstPoints.size != 4) return null

        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val sx = srcPoints[i].x.toDouble()
            val sy = srcPoints[i].y.toDouble()
            val dx = dstPoints[i].x.toDouble()
            val dy = dstPoints[i].y.toDouble()

            val r1 = 2 * i
            a[r1][0] = sx
            a[r1][1] = sy
            a[r1][2] = 1.0
            a[r1][3] = 0.0
            a[r1][4] = 0.0
            a[r1][5] = 0.0
            a[r1][6] = -dx * sx
            a[r1][7] = -dx * sy
            a[r1][8] = dx

            val r2 = 2 * i + 1
            a[r2][0] = 0.0
            a[r2][1] = 0.0
            a[r2][2] = 0.0
            a[r2][3] = sx
            a[r2][4] = sy
            a[r2][5] = 1.0
            a[r2][6] = -dy * sx
            a[r2][7] = -dy * sy
            a[r2][8] = dy
        }

        // Gaussian elimination with partial pivoting
        for (col in 0 until 8) {
            var pivot = col
            var maxVal = abs(a[col][col])
            for (row in col + 1 until 8) {
                val v = abs(a[row][col])
                if (v > maxVal) {
                    maxVal = v
                    pivot = row
                }
            }
            if (maxVal < 1e-10) return null

            if (pivot != col) {
                val tmp = a[col]
                a[col] = a[pivot]
                a[pivot] = tmp
            }

            val diag = a[col][col]
            for (j in col until 9) {
                a[col][j] /= diag
            }

            for (row in 0 until 8) {
                if (row != col) {
                    val factor = a[row][col]
                    if (factor != 0.0) {
                        for (j in col until 9) {
                            a[row][j] -= factor * a[col][j]
                        }
                    }
                }
            }
        }

        return doubleArrayOf(
            a[0][8], a[1][8], a[2][8],
            a[3][8], a[4][8], a[5][8],
            a[6][8], a[7][8], 1.0
        )
    }

    fun applyHomography(h: DoubleArray, p: Point2D): Point2D {
        val x = p.x.toDouble()
        val y = p.y.toDouble()
        val w = h[6] * x + h[7] * y + h[8]
        if (abs(w) < 1e-12) return p
        val nx = (h[0] * x + h[1] * y + h[2]) / w
        val ny = (h[3] * x + h[4] * y + h[5]) / w
        return Point2D(nx.toFloat(), ny.toFloat())
    }

    /**
     * Warps source bitmap using normalized [0..1] corner coordinates of the ID-1 reference card
     * into an orthographic top-down bitmap with exact [pxPerMm] scale.
     *
     * @param source Source photo bitmap
     * @param normalizedCardCorners 4 corners in [0f..1f] image space: TL, TR, BR, BL
     * @param pxPerMm Output scale (default 20 px/mm -> 1712x1080)
     */
    fun rectifyCardImage(
        source: Bitmap,
        normalizedCardCorners: List<Point2D>,
        pxPerMm: Float = DEFAULT_PX_PER_MM
    ): Bitmap? {
        if (normalizedCardCorners.size != 4) return null
        val outW = (ID1_WIDTH_MM * pxPerMm).roundToInt().coerceIn(320, 2400)
        val outH = (ID1_HEIGHT_MM * pxPerMm).roundToInt().coerceIn(200, 1600)

        val srcW = source.width
        val srcH = source.height
        if (srcW <= 1 || srcH <= 1) return null

        val srcPts = normalizedCardCorners.map {
            Point2D(it.x * (srcW - 1), it.y * (srcH - 1))
        }
        val dstPts = listOf(
            Point2D(0f, 0f),
            Point2D(outW - 1f, 0f),
            Point2D(outW - 1f, outH - 1f),
            Point2D(0f, outH - 1f)
        )

        // Inverse homography: dst -> src for clean bilinear sampling
        val invH = computeHomography(dstPts, srcPts) ?: return null

        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outPixels = IntArray(outW * outH)
        val h0 = invH[0]; val h1 = invH[1]; val h2 = invH[2]
        val h3 = invH[3]; val h4 = invH[4]; val h5 = invH[5]
        val h6 = invH[6]; val h7 = invH[7]; val h8 = invH[8]

        for (dy in 0 until outH) {
            val rowOffset = dy * outW
            val y1 = h1 * dy + h2
            val y4 = h4 * dy + h5
            val y7 = h7 * dy + h8
            for (dx in 0 until outW) {
                val w = h6 * dx + y7
                if (abs(w) < 1e-9) {
                    outPixels[rowOffset + dx] = Color.BLACK
                    continue
                }
                val sx = (h0 * dx + y1) / w
                val sy = (h3 * dx + y4) / w

                val ix = floor(sx).toInt()
                val iy = floor(sy).toInt()
                if (ix < 0 || ix >= srcW - 1 || iy < 0 || iy >= srcH - 1) {
                    outPixels[rowOffset + dx] = Color.rgb(15, 23, 42)
                    continue
                }

                val fx = (sx - ix).toFloat()
                val fy = (sy - iy).toFloat()
                val idx00 = iy * srcW + ix
                val c00 = srcPixels[idx00]
                val c10 = srcPixels[idx00 + 1]
                val c01 = srcPixels[idx00 + srcW]
                val c11 = srcPixels[idx00 + srcW + 1]

                val w00 = (1f - fx) * (1f - fy)
                val w10 = fx * (1f - fy)
                val w01 = (1f - fx) * fy
                val w11 = fx * fy

                val r = (((c00 ushr 16) and 0xFF) * w00 +
                    ((c10 ushr 16) and 0xFF) * w10 +
                    ((c01 ushr 16) and 0xFF) * w01 +
                    ((c11 ushr 16) and 0xFF) * w11).toInt().coerceIn(0, 255)
                val g = (((c00 ushr 8) and 0xFF) * w00 +
                    ((c10 ushr 8) and 0xFF) * w10 +
                    ((c01 ushr 8) and 0xFF) * w01 +
                    ((c11 ushr 8) and 0xFF) * w11).toInt().coerceIn(0, 255)
                val b = ((c00 and 0xFF) * w00 +
                    (c10 and 0xFF) * w10 +
                    (c01 and 0xFF) * w01 +
                    (c11 and 0xFF) * w11).toInt().coerceIn(0, 255)

                outPixels[rowOffset + dx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(outPixels, outW, outH, Bitmap.Config.ARGB_8888)
    }
}
