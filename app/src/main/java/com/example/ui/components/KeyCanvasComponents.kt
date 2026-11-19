package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.domain.model.KeyRecord
import com.example.domain.model.Point2D
import com.example.engine.KeyPolygonBuilder
import com.example.export.Mesh3D
import com.example.export.Vec3
import com.example.ui.theme.BlueprintGridLine
import com.example.ui.theme.BlueprintNavy
import com.example.ui.theme.BrassGold
import com.example.ui.theme.BrassHighlight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Compact 2D key silhouette preview used in Library cards & Home recent items.
 */
@Composable
fun KeySilhouetteCanvas(
    record: KeyRecord,
    modifier: Modifier = Modifier,
    strokeColor: Color = PrimaryBlue,
    fillColor: Color = PrimaryBlue.copy(alpha = 0.16f)
) {
    val polygon = remember(
        record.bladeLengthMm,
        record.bladeWidthMm,
        record.spacingsMm,
        record.depthsMm,
        record.isDoubleSided
    ) {
        KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = record.bladeLengthMm,
            bladeWidthMm = record.bladeWidthMm,
            spacingsMm = record.spacingsMm,
            depthsMm = record.depthsMm,
            isDoubleSided = record.isDoubleSided
        )
    }

    Canvas(modifier = modifier) {
        val totalW = polygon.bowLengthMm + polygon.bladeLengthMm + 4f
        val totalH = polygon.bowHeightMm + 4f
        val scale = minOf(size.width / totalW, size.height / totalH) * 0.92f
        val originX = (size.width - totalW * scale) * 0.5f + polygon.bowLengthMm * scale
        val originY = size.height * 0.5f + polygon.bladeWidthMm * 0.5f * scale

        val outerPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val x = originX + p.x * scale
            val y = originY - p.y * scale
            if (idx == 0) outerPath.moveTo(x, y) else outerPath.lineTo(x, y)
        }
        outerPath.close()

        val holePath = Path()
        polygon.bowHoleContour.forEachIndexed { idx, p ->
            val x = originX + p.x * scale
            val y = originY - p.y * scale
            if (idx == 0) holePath.moveTo(x, y) else holePath.lineTo(x, y)
        }
        holePath.close()

        drawPath(outerPath, color = fillColor)
        drawPath(outerPath, color = strokeColor, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        drawPath(holePath, color = Color.Transparent)
        drawPath(holePath, color = strokeColor, style = Stroke(width = 1.8f))
    }
}

/**
 * Interactive 360-degree 3D Triangle Mesh viewer for STL preview before export.
 */
@Composable
fun Key3DPreviewCanvas(
    mesh: Mesh3D?,
    wireframe: Boolean,
    autoRotate: Boolean,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableFloatStateOf(-0.45f) }
    var pitch by remember { mutableFloatStateOf(0.52f) }

    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            yaw += 0.025f
            delay(16L)
        }
    }

    Canvas(
        modifier = modifier
            .background(BlueprintNavy)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw += dragAmount.x * 0.01f
                    pitch = (pitch + dragAmount.y * 0.01f).coerceIn(-1.45f, 1.45f)
                }
            }
    ) {
        // Subtle CAD grid backdrop
        val gridStep = 36f
        var gx = 0f
        while (gx < size.width) {
            drawLine(BlueprintGridLine.copy(alpha = 0.45f), Offset(gx, 0f), Offset(gx, size.height), 1f)
            gx += gridStep
        }
        var gy = 0f
        while (gy < size.height) {
            drawLine(BlueprintGridLine.copy(alpha = 0.45f), Offset(0f, gy), Offset(size.width, gy), 1f)
            gy += gridStep
        }

        if (mesh == null || mesh.triangles.isEmpty()) return@Canvas

        val cosY = cos(yaw)
        val sinY = sin(yaw)
        val cosX = cos(pitch)
        val sinX = sin(pitch)

        fun rotatePoint(p: Vec3): Vec3 {
            val dx = p.x - mesh.center.x
            val dy = p.y - mesh.center.y
            val dz = p.z - mesh.center.z
            // Rotate around Y
            val x1 = dx * cosY + dz * sinY
            val z1 = -dx * sinY + dz * cosY
            // Rotate around X
            val y2 = dy * cosX - z1 * sinX
            val z2 = dy * sinX + z1 * cosX
            return Vec3(x1, y2, z2)
        }

        val scale = (minOf(size.width, size.height) * 0.44f) / mesh.boundingRadius
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val lightDir = Vec3(0.35f, 0.55f, 0.75f).normalized()
        val cameraDist = mesh.boundingRadius * 4.2f

        data class ProjectedTri(
            val p1: Offset,
            val p2: Offset,
            val p3: Offset,
            val depth: Float,
            val shade: Float
        )

        val projected = ArrayList<ProjectedTri>(mesh.triangles.size)
        for (tri in mesh.triangles) {
            val rv1 = rotatePoint(tri.v1)
            val rv2 = rotatePoint(tri.v2)
            val rv3 = rotatePoint(tri.v3)

            // Backface culling in view space (except wireframe)
            val e1 = rv2 - rv1
            val e2 = rv3 - rv1
            val normal = (e1 cross e2).normalized()
            if (!wireframe && normal.z <= 0f) continue

            val intensity = (0.28f + 0.72f * kotlin.math.abs(normal dot lightDir)).coerceIn(0.2f, 1f)

            fun project(v: Vec3): Offset {
                val factor = cameraDist / (cameraDist - v.z).coerceAtLeast(1f)
                return Offset(cx + v.x * scale * factor, cy - v.y * scale * factor)
            }

            projected.add(
                ProjectedTri(
                    p1 = project(rv1),
                    p2 = project(rv2),
                    p3 = project(rv3),
                    depth = (rv1.z + rv2.z + rv3.z) / 3f,
                    shade = intensity
                )
            )
        }

        projected.sortBy { it.depth }

        val path = Path()
        for (pt in projected) {
            path.reset()
            path.moveTo(pt.p1.x, pt.p1.y)
            path.lineTo(pt.p2.x, pt.p2.y)
            path.lineTo(pt.p3.x, pt.p3.y)
            path.close()

            if (wireframe) {
                drawPath(path, color = SecondaryTeal.copy(alpha = 0.85f), style = Stroke(width = 1.4f))
            } else {
                val baseColor = Color(
                    red = (BrassGold.red * pt.shade + BrassHighlight.red * 0.15f).coerceIn(0f, 1f),
                    green = (BrassGold.green * pt.shade + BrassHighlight.green * 0.15f).coerceIn(0f, 1f),
                    blue = (BrassGold.blue * pt.shade + BrassHighlight.blue * 0.15f).coerceIn(0f, 1f),
                    alpha = 1f
                )
                drawPath(path, color = baseColor)
                drawPath(path, color = Color.Black.copy(alpha = 0.16f), style = Stroke(width = 0.7f))
            }
        }
    }
}

/**
 * Interactive CAD Bitting Canvas with millimeter grid and draggable Pin Cut Depth/Spacing nodes.
 */
@Composable
fun BittingCadCanvas(
    bladeLengthMm: Float,
    bladeWidthMm: Float,
    spacingsMm: List<Float>,
    depthsMm: List<Float>,
    selectedPinIndex: Int,
    isDoubleSided: Boolean = false,
    rectifiedBitmap: Bitmap? = null,
    onPinDrag: (pinIndex: Int, newDepthMm: Float, newSpacingMm: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8)
        )
    }

    var activeDragPin by remember { mutableIntStateOf(-1) }
    var activeDragBottomSide by remember { mutableIntStateOf(0) } // 0 = top edge, 1 = bottom edge
    val imageBitmap: ImageBitmap? = remember(rectifiedBitmap) {
        rectifiedBitmap?.asImageBitmap()
    }

    val polygon = remember(bladeLengthMm, bladeWidthMm, spacingsMm, depthsMm, isDoubleSided) {
        KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm,
            spacingsMm = spacingsMm,
            depthsMm = depthsMm,
            isDoubleSided = isDoubleSided
        )
    }

    Canvas(
        modifier = modifier
            .background(BlueprintNavy)
            .pointerInput(bladeLengthMm, bladeWidthMm, spacingsMm, depthsMm, isDoubleSided) {
                val totalW = polygon.bowLengthMm + bladeLengthMm + 6f
                val pxPerMm = (size.width / totalW) * 0.92f
                val originX = (size.width - totalW * pxPerMm) * 0.5f + polygon.bowLengthMm * pxPerMm
                val originY = if (isDoubleSided) size.height * 0.62f else size.height * 0.68f

                detectDragGestures(
                    onDragStart = { startOffset ->
                        var nearestIdx = -1
                        var bestDist = 72f
                        var matchedBottom = 0
                        spacingsMm.forEachIndexed { idx, spacing ->
                            val depth = depthsMm.getOrElse(idx) { 0f }
                            val px = originX + spacing * pxPerMm
                            val pyTop = originY - (bladeWidthMm - depth) * pxPerMm
                            val dTop = hypot(startOffset.x - px, startOffset.y - pyTop)
                            if (dTop < bestDist) {
                                bestDist = dTop
                                nearestIdx = idx
                                matchedBottom = 0
                            }
                            if (isDoubleSided) {
                                val pyBot = originY - depth * pxPerMm
                                val dBot = hypot(startOffset.x - px, startOffset.y - pyBot)
                                if (dBot < bestDist) {
                                    bestDist = dBot
                                    nearestIdx = idx
                                    matchedBottom = 1
                                }
                            }
                        }
                        activeDragPin = nearestIdx
                        activeDragBottomSide = matchedBottom
                    },
                    onDragEnd = { activeDragPin = -1 },
                    onDragCancel = { activeDragPin = -1 },
                    onDrag = { change, _ ->
                        if (activeDragPin >= 0) {
                            change.consume()
                            val newSpacingMm = (change.position.x - originX) / pxPerMm
                            val cutYFromSpineMm = (originY - change.position.y) / pxPerMm
                            val newDepthMm = if (activeDragBottomSide == 1) {
                                cutYFromSpineMm
                            } else {
                                bladeWidthMm - cutYFromSpineMm
                            }
                            onPinDrag(activeDragPin, newDepthMm, newSpacingMm)
                        }
                    }
                )
            }
    ) {
        val totalW = polygon.bowLengthMm + bladeLengthMm + 6f
        val pxPerMm = (size.width / totalW) * 0.92f
        val originX = (size.width - totalW * pxPerMm) * 0.5f + polygon.bowLengthMm * pxPerMm
        val originY = if (isDoubleSided) size.height * 0.62f else size.height * 0.68f

        // Optional calibrated reference photo backdrop
        if (imageBitmap != null) {
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = 0.28f
            )
        }

        // Millimeter CAD grid (1mm & 5mm lines)
        val minMmX = -26
        val maxMmX = (bladeLengthMm + 6).toInt()
        for (mm in minMmX..maxMmX) {
            val x = originX + mm * pxPerMm
            if (x < 0f || x > size.width) continue
            val isMajor = mm % 5 == 0
            drawLine(
                color = if (isMajor) BlueprintGridLine else BlueprintGridLine.copy(alpha = 0.45f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.5f else 0.7f
            )
        }
        for (mm in -12..18) {
            val y = originY - mm * pxPerMm
            if (y < 0f || y > size.height) continue
            val isMajor = mm % 5 == 0
            drawLine(
                color = if (isMajor) BlueprintGridLine else BlueprintGridLine.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (isMajor) 1.5f else 0.7f
            )
        }

        // Key Polygon Fill + Stroke
        val outerPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val x = originX + p.x * pxPerMm
            val y = originY - p.y * pxPerMm
            if (idx == 0) outerPath.moveTo(x, y) else outerPath.lineTo(x, y)
        }
        outerPath.close()

        val holePath = Path()
        polygon.bowHoleContour.forEachIndexed { idx, p ->
            val x = originX + p.x * pxPerMm
            val y = originY - p.y * pxPerMm
            if (idx == 0) holePath.moveTo(x, y) else holePath.lineTo(x, y)
        }
        holePath.close()

        drawPath(outerPath, color = PrimaryBlue.copy(alpha = 0.24f))
        drawPath(outerPath, color = Color(0xFF38BDF8), style = Stroke(width = 3.2f, join = StrokeJoin.Round))
        drawPath(holePath, color = BlueprintNavy)
        drawPath(holePath, color = Color(0xFF38BDF8), style = Stroke(width = 2.4f))

        // Shoulder datum line (X = 0)
        drawLine(
            color = SecondaryTeal,
            start = Offset(originX, originY + 12f * pxPerMm),
            end = Offset(originX, originY - (bladeWidthMm + 4f) * pxPerMm),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
        )

        // Draggable Pin Nodes
        spacingsMm.forEachIndexed { idx, spacing ->
            val depth = depthsMm.getOrElse(idx) { 0f }
            val px = originX + spacing * pxPerMm
            val py = originY - (bladeWidthMm - depth) * pxPerMm
            val isSelected = idx == selectedPinIndex

            drawLine(
                color = if (isSelected) StatusWarning else SecondaryTeal.copy(alpha = 0.75f),
                start = Offset(px, py),
                end = Offset(px, originY - (bladeWidthMm + 3.2f) * pxPerMm),
                strokeWidth = if (isSelected) 2.2f else 1.3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )

            // Outer halo + core target
            drawCircle(
                color = if (isSelected) StatusWarning.copy(alpha = 0.35f) else SecondaryTeal.copy(alpha = 0.28f),
                radius = if (isSelected) 24f else 18f,
                center = Offset(px, py)
            )
            drawCircle(
                color = if (isSelected) StatusWarning else SecondaryTeal,
                radius = if (isSelected) 11f else 8.5f,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = Offset(px, py)
            )

            val txt = String.format(Locale.US, "P%d", idx + 1)
            val measured = textMeasurer.measure(txt, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(px - measured.size.width * 0.5f, originY - (bladeWidthMm + 5.2f) * pxPerMm)
            )

            if (isDoubleSided) {
                val pyBot = originY - depth * pxPerMm
                drawCircle(
                    color = if (isSelected) StatusWarning.copy(alpha = 0.28f) else SecondaryTeal.copy(alpha = 0.22f),
                    radius = if (isSelected) 18f else 14f,
                    center = Offset(px, pyBot)
                )
                drawCircle(
                    color = if (isSelected) StatusWarning else SecondaryTeal,
                    radius = if (isSelected) 9f else 7f,
                    center = Offset(px, pyBot)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.8f,
                    center = Offset(px, pyBot)
                )
            }
        }
    }
}

/**
 * Physical 1:1 Screen Ruler (Flipper Zero Mode): uses real physical pixels-per-mm
 * so users can place a physical key directly on the screen glass and measure cuts.
 */
@Composable
fun ScreenRulerCanvas(
    screenDpiOverride: Float,
    bladeLengthMm: Float,
    bladeWidthMm: Float,
    spacingsMm: List<Float>,
    depthsMm: List<Float>,
    selectedPinIndex: Int,
    isDoubleSided: Boolean = false,
    onPinDrag: (pinIndex: Int, newDepthMm: Float) -> Unit,
    onBladeLengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val xdpi = if (screenDpiOverride > 50f) screenDpiOverride else metrics.xdpi.coerceAtLeast(160f)
    val pxPerMm = xdpi / 25.4f
    val textMeasurer = rememberTextMeasurer()
    val monoStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = Color(0xFFE2E8F0)
        )
    }

    val polygon = remember(bladeLengthMm, bladeWidthMm, spacingsMm, depthsMm, isDoubleSided) {
        KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm,
            spacingsMm = spacingsMm,
            depthsMm = depthsMm,
            isDoubleSided = isDoubleSided
        )
    }

    var draggingTarget by remember { mutableIntStateOf(-1) } // -2 = tip handle, >=0 = pin index
    var draggingBottom by remember { mutableIntStateOf(0) }

    Canvas(
        modifier = modifier
            .background(Color(0xFF060B16))
            .pointerInput(pxPerMm, bladeLengthMm, bladeWidthMm, spacingsMm, depthsMm, isDoubleSided) {
                val shoulderX = size.width * 0.16f
                val spineY = size.height * 0.62f

                detectDragGestures(
                    onDragStart = { start ->
                        val tipX = shoulderX + bladeLengthMm * pxPerMm
                        val tipY = spineY - bladeWidthMm * 0.5f * pxPerMm
                        if (hypot(start.x - tipX, start.y - tipY) < 64f) {
                            draggingTarget = -2
                            return@detectDragGestures
                        }
                        var best = -1
                        var bestD = 72f
                        var matchedBottom = 0
                        spacingsMm.forEachIndexed { idx, sp ->
                            val depth = depthsMm.getOrElse(idx) { 0f }
                            val px = shoulderX + sp * pxPerMm
                            val py = spineY - (bladeWidthMm - depth) * pxPerMm
                            val d = hypot(start.x - px, start.y - py)
                            if (d < bestD) {
                                bestD = d
                                best = idx
                                matchedBottom = 0
                            }
                            if (isDoubleSided) {
                                val pyBot = spineY - depth * pxPerMm
                                val dBot = hypot(start.x - px, start.y - pyBot)
                                if (dBot < bestD) {
                                    bestD = dBot
                                    best = idx
                                    matchedBottom = 1
                                }
                            }
                        }
                        draggingTarget = best
                        draggingBottom = matchedBottom
                    },
                    onDragEnd = { draggingTarget = -1 },
                    onDragCancel = { draggingTarget = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (draggingTarget == -2) {
                            val newLen = ((change.position.x - shoulderX) / pxPerMm).coerceIn(18f, 45f)
                            onBladeLengthChange(newLen)
                        } else if (draggingTarget >= 0) {
                            val cutYFromSpineMm = (spineY - change.position.y) / pxPerMm
                            val depthMm = if (draggingBottom == 1) cutYFromSpineMm else bladeWidthMm - cutYFromSpineMm
                            onPinDrag(draggingTarget, depthMm)
                        }
                    }
                )
            }
    ) {
        val shoulderX = size.width * 0.16f
        val spineY = size.height * 0.62f

        // Physical Millimeter Ruler Ticks along Spine
        val maxMm = ((size.width - shoulderX) / pxPerMm).toInt().coerceAtLeast(35)
        drawLine(Color(0xFF334155), Offset(shoulderX, spineY), Offset(size.width, spineY), 2f)
        for (mm in 0..maxMm) {
            val rx = shoulderX + mm * pxPerMm
            val tickLen = when {
                mm % 10 == 0 -> 26f
                mm % 5 == 0 -> 18f
                else -> 10f
            }
            drawLine(Color(0xFF64748B), Offset(rx, spineY), Offset(rx, spineY + tickLen), 1.5f)
            if (mm % 10 == 0) {
                val res = textMeasurer.measure("$mm", monoStyle)
                drawText(res, topLeft = Offset(rx - res.size.width * 0.5f, spineY + 30f))
            }
        }

        // Shoulder Stop Vertical Guide
        drawLine(
            color = SecondaryTeal,
            start = Offset(shoulderX, spineY - 14f * pxPerMm),
            end = Offset(shoulderX, spineY + 10f * pxPerMm),
            strokeWidth = 3f
        )

        // Blade Contour Overlay (1:1 physical size)
        val bladeW = bladeLengthMm * pxPerMm
        val bladeH = bladeWidthMm * pxPerMm
        val keyPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val x = shoulderX + p.x * pxPerMm
            val y = spineY - p.y * pxPerMm
            if (idx == 0) keyPath.moveTo(x, y) else keyPath.lineTo(x, y)
        }
        keyPath.close()
        drawPath(keyPath, color = PrimaryBlue.copy(alpha = 0.22f))
        drawPath(keyPath, color = PrimaryBlue, style = Stroke(width = 2.2f, join = StrokeJoin.Round))

        // Tip Length Drag Handle
        val tipX = shoulderX + bladeW
        val tipY = spineY - bladeH * 0.5f
        drawCircle(PrimaryBlue, radius = 14f, center = Offset(tipX, tipY))
        drawCircle(Color.White, radius = 5f, center = Offset(tipX, tipY))

        // Pin Cut Nodes
        spacingsMm.forEachIndexed { idx, sp ->
            val depth = depthsMm.getOrElse(idx) { 0f }
            val px = shoulderX + sp * pxPerMm
            val py = spineY - (bladeWidthMm - depth) * pxPerMm
            val selected = idx == selectedPinIndex
            drawLine(
                color = if (selected) StatusWarning else SecondaryTeal,
                start = Offset(px, spineY - bladeH),
                end = Offset(px, py),
                strokeWidth = 2.5f
            )
            drawCircle(
                color = if (selected) StatusWarning else SecondaryTeal,
                radius = if (selected) 13f else 10f,
                center = Offset(px, py)
            )
            drawCircle(Color.White, radius = 3.5f, center = Offset(px, py))

            if (isDoubleSided) {
                val pyBot = spineY - depth * pxPerMm
                drawLine(
                    color = if (selected) StatusWarning else SecondaryTeal,
                    start = Offset(px, spineY),
                    end = Offset(px, pyBot),
                    strokeWidth = 2.5f
                )
                drawCircle(
                    color = if (selected) StatusWarning else SecondaryTeal,
                    radius = if (selected) 11f else 8.5f,
                    center = Offset(px, pyBot)
                )
                drawCircle(Color.White, radius = 3f, center = Offset(px, pyBot))
            }
        }
    }
}

/**
 * Full-height Longitudinal (Vertical) 1:1 Physical Key Placement Pad.
 * Users place their key vertically on the screen (Bow at top, Blade extending downward),
 * and adjust teeth via touch drag on either single or double-sided blades.
 */
@Composable
fun LongitudinalKeyPadCanvas(
    screenDpiOverride: Float,
    bladeLengthMm: Float,
    bladeWidthMm: Float,
    spacingsMm: List<Float>,
    depthsMm: List<Float>,
    bittingDigits: List<Int>,
    selectedPinIndex: Int,
    isDoubleSided: Boolean,
    lockSpacingOnDrag: Boolean,
    onSelectPin: (Int) -> Unit,
    onPinDrag: (pinIndex: Int, newDepthMm: Float, newSpacingMm: Float?) -> Unit,
    onBladeLengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val ydpi = if (screenDpiOverride > 50f) screenDpiOverride else metrics.ydpi.coerceAtLeast(160f)
    val pxPerMm = ydpi / 25.4f
    val textMeasurer = rememberTextMeasurer()
    val rulerStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = Color(0xFF94A3B8)
        )
    }
    val pinLabelStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }

    val polygon = remember(bladeLengthMm, bladeWidthMm, spacingsMm, depthsMm, isDoubleSided) {
        KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = bladeLengthMm,
            bladeWidthMm = bladeWidthMm,
            spacingsMm = spacingsMm,
            depthsMm = depthsMm,
            isDoubleSided = isDoubleSided
        )
    }

    var draggingTarget by remember { mutableIntStateOf(-1) } // -2 = tip handle, >=0 = pin index
    var draggingLeftSide by remember { mutableIntStateOf(0) } // 0 = right cut edge, 1 = left mirrored edge

    Canvas(
        modifier = modifier
            .background(Color(0xFF060D1C))
            .pointerInput(
                pxPerMm,
                bladeLengthMm,
                bladeWidthMm,
                spacingsMm,
                depthsMm,
                isDoubleSided,
                lockSpacingOnDrag
            ) {
                val centerX = size.width * 0.5f
                val maxShoulderForBlade = (size.height - (bladeLengthMm + 6f) * pxPerMm).coerceAtLeast(20f)
                val shoulderY = minOf(size.height * 0.28f, maxShoulderForBlade).coerceAtLeast(20f)

                detectDragGestures(
                    onDragStart = { start ->
                        val tipX = centerX
                        val tipY = shoulderY + bladeLengthMm * pxPerMm
                        if (hypot(start.x - tipX, start.y - tipY) < 64f) {
                            draggingTarget = -2
                            return@detectDragGestures
                        }
                        var best = -1
                        var bestDist = 80f
                        var matchedLeft = 0
                        spacingsMm.forEachIndexed { idx, sp ->
                            val depth = depthsMm.getOrElse(idx) { 0f }
                            val py = shoulderY + sp * pxPerMm
                            val pxRight = centerX + (bladeWidthMm * 0.5f - depth) * pxPerMm
                            val dRight = hypot(start.x - pxRight, start.y - py)
                            if (dRight < bestDist) {
                                bestDist = dRight
                                best = idx
                                matchedLeft = 0
                            }
                            if (isDoubleSided) {
                                val pxLeft = centerX + (depth - bladeWidthMm * 0.5f) * pxPerMm
                                val dLeft = hypot(start.x - pxLeft, start.y - py)
                                if (dLeft < bestDist) {
                                    bestDist = dLeft
                                    best = idx
                                    matchedLeft = 1
                                }
                            }
                        }
                        draggingTarget = best
                        draggingLeftSide = matchedLeft
                        if (best >= 0) {
                            onSelectPin(best)
                        }
                    },
                    onDragEnd = { draggingTarget = -1 },
                    onDragCancel = { draggingTarget = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (draggingTarget == -2) {
                            val newLen = ((change.position.y - shoulderY) / pxPerMm).coerceIn(18f, 45f)
                            onBladeLengthChange(newLen)
                        } else if (draggingTarget >= 0) {
                            val offsetFromCenterMm = (change.position.x - centerX) / pxPerMm
                            val halfW = bladeWidthMm * 0.5f
                            val newDepthMm = if (draggingLeftSide == 1) {
                                halfW + offsetFromCenterMm
                            } else {
                                halfW - offsetFromCenterMm
                            }
                            val newSpacingMm = if (lockSpacingOnDrag) {
                                null
                            } else {
                                (change.position.y - shoulderY) / pxPerMm
                            }
                            onPinDrag(draggingTarget, newDepthMm, newSpacingMm)
                        }
                    }
                )
            }
    ) {
        val centerX = size.width * 0.5f
        val maxShoulderForBlade = (size.height - (bladeLengthMm + 6f) * pxPerMm).coerceAtLeast(20f)
        val shoulderY = minOf(size.height * 0.28f, maxShoulderForBlade).coerceAtLeast(20f)
        val halfW = bladeWidthMm * 0.5f

        // Subtle millimeter vertical grid around blade
        val maxGridMm = ((size.height - shoulderY) / pxPerMm).toInt().coerceAtLeast(40)
        for (mm in -25..maxGridMm) {
            val gy = shoulderY + mm * pxPerMm
            if (gy < 0f || gy > size.height) continue
            val isMajor = mm % 5 == 0
            drawLine(
                color = if (isMajor) BlueprintGridLine.copy(alpha = 0.65f) else BlueprintGridLine.copy(alpha = 0.25f),
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = if (isMajor) 1.4f else 0.7f
            )
        }

        // Physical Millimeter Vertical Ruler along left margin
        val rulerX = (centerX - 18f * pxPerMm).coerceAtLeast(42f)
        drawLine(Color(0xFF334155), Offset(rulerX, shoulderY), Offset(rulerX, size.height), 2f)
        for (mm in 0..maxGridMm) {
            val ry = shoulderY + mm * pxPerMm
            if (ry > size.height) break
            val tickLen = when {
                mm % 10 == 0 -> 24f
                mm % 5 == 0 -> 16f
                else -> 9f
            }
            drawLine(Color(0xFF64748B), Offset(rulerX - tickLen, ry), Offset(rulerX, ry), 1.5f)
            if (mm % 5 == 0) {
                val res = textMeasurer.measure("$mm", rulerStyle)
                drawText(
                    res,
                    topLeft = Offset((rulerX - tickLen - res.size.width - 6f).coerceAtLeast(4f), ry - res.size.height * 0.5f)
                )
            }
        }

        // Horizontal Shoulder Stop Guide (0 mm)
        drawLine(
            color = SecondaryTeal,
            start = Offset(rulerX - 10f, shoulderY),
            end = Offset(size.width - 16f, shoulderY),
            strokeWidth = 2.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
        )

        // Center Spine Axis
        drawLine(
            color = Color(0xFF475569),
            start = Offset(centerX, shoulderY - 24f * pxPerMm),
            end = Offset(centerX, shoulderY + (bladeLengthMm + 4f) * pxPerMm),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        // Rotated 90deg clockwise 1:1 Key Polygon (Bow on top, Blade extending downward)
        val outerPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val sx = centerX + (p.y - halfW) * pxPerMm
            val sy = shoulderY + p.x * pxPerMm
            if (idx == 0) outerPath.moveTo(sx, sy) else outerPath.lineTo(sx, sy)
        }
        outerPath.close()

        val holePath = Path()
        polygon.bowHoleContour.forEachIndexed { idx, p ->
            val sx = centerX + (p.y - halfW) * pxPerMm
            val sy = shoulderY + p.x * pxPerMm
            if (idx == 0) holePath.moveTo(sx, sy) else holePath.lineTo(sx, sy)
        }
        holePath.close()

        drawPath(outerPath, color = PrimaryBlue.copy(alpha = 0.25f))
        drawPath(outerPath, color = Color(0xFF38BDF8), style = Stroke(width = 3f, join = StrokeJoin.Round))
        drawPath(holePath, color = Color(0xFF060D1C))
        drawPath(holePath, color = Color(0xFF38BDF8), style = Stroke(width = 2.2f))

        // Tip Length Drag Handle
        val tipY = shoulderY + bladeLengthMm * pxPerMm
        drawCircle(PrimaryBlue, radius = 14f, center = Offset(centerX, tipY))
        drawCircle(Color.White, radius = 5f, center = Offset(centerX, tipY))

        // Pin Cut Nodes & Callout Tags
        val rightOuterX = centerX + halfW * pxPerMm
        val leftOuterX = centerX - halfW * pxPerMm
        spacingsMm.forEachIndexed { idx, sp ->
            val depth = depthsMm.getOrElse(idx) { 0f }
            val digit = bittingDigits.getOrNull(idx) ?: 0
            val py = shoulderY + sp * pxPerMm
            val pxRight = centerX + (halfW - depth) * pxPerMm
            val isSelected = idx == selectedPinIndex

            // Depth indicator bar on right edge
            drawLine(
                color = if (isSelected) StatusWarning else SecondaryTeal,
                start = Offset(rightOuterX, py),
                end = Offset(pxRight, py),
                strokeWidth = if (isSelected) 3f else 2f
            )
            // Selection horizontal crosshair
            if (isSelected) {
                drawLine(
                    color = StatusWarning.copy(alpha = 0.45f),
                    start = Offset(rulerX, py),
                    end = Offset(size.width - 16f, py),
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }

            // Right draggable pin node
            drawCircle(
                color = if (isSelected) StatusWarning.copy(alpha = 0.32f) else SecondaryTeal.copy(alpha = 0.24f),
                radius = if (isSelected) 24f else 17f,
                center = Offset(pxRight, py)
            )
            drawCircle(
                color = if (isSelected) StatusWarning else SecondaryTeal,
                radius = if (isSelected) 12f else 9f,
                center = Offset(pxRight, py)
            )
            drawCircle(Color.White, radius = 3.5f, center = Offset(pxRight, py))

            // Symmetric left node if Double-Sided
            if (isDoubleSided) {
                val pxLeft = centerX + (depth - halfW) * pxPerMm
                drawLine(
                    color = if (isSelected) StatusWarning else SecondaryTeal,
                    start = Offset(leftOuterX, py),
                    end = Offset(pxLeft, py),
                    strokeWidth = if (isSelected) 3f else 2f
                )
                drawCircle(
                    color = if (isSelected) StatusWarning.copy(alpha = 0.28f) else SecondaryTeal.copy(alpha = 0.20f),
                    radius = if (isSelected) 20f else 15f,
                    center = Offset(pxLeft, py)
                )
                drawCircle(
                    color = if (isSelected) StatusWarning else SecondaryTeal,
                    radius = if (isSelected) 10f else 8f,
                    center = Offset(pxLeft, py)
                )
                drawCircle(Color.White, radius = 3f, center = Offset(pxLeft, py))
            }

            // Callout label on the right side
            val levelDisplay = if (depth <= 0.08f) "0" else "$digit"
            val labelText = String.format(Locale.US, "P%d [%s] %.2f", idx + 1, levelDisplay, depth)
            val measured = textMeasurer.measure(
                labelText,
                pinLabelStyle.copy(color = if (isSelected) StatusWarning else Color(0xFFCBD5E1))
            )
            val labelX = (rightOuterX + 20f).coerceAtMost(size.width - measured.size.width - 8f)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(labelX, py - measured.size.height * 0.5f)
            )
        }
    }
}

/**
 * 4-Corner Perspective Calibration Canvas over captured/selected photo for ISO ID-1 card rectification.
 */
@Composable
fun PerspectiveCornerCanvas(
    bitmap: Bitmap,
    corners: List<Point2D>,
    onCornerMoved: (Int, Point2D) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var activeCorner by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(corners) {
                detectDragGestures(
                    onDragStart = { start ->
                        var bestIdx = -1
                        var bestDist = 90f
                        corners.forEachIndexed { idx, pt ->
                            val cx = pt.x * size.width
                            val cy = pt.y * size.height
                            val d = hypot(start.x - cx, start.y - cy)
                            if (d < bestDist) {
                                bestDist = d
                                bestIdx = idx
                            }
                        }
                        activeCorner = bestIdx
                    },
                    onDragEnd = { activeCorner = -1 },
                    onDragCancel = { activeCorner = -1 },
                    onDrag = { change, _ ->
                        if (activeCorner in corners.indices) {
                            change.consume()
                            val nx = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                            val ny = (change.position.y / size.height).coerceIn(0.02f, 0.98f)
                            onCornerMoved(activeCorner, Point2D(nx, ny))
                        }
                    }
                )
            }
    ) {
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        )

        if (corners.size == 4) {
            val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
            val quadPath = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                lineTo(pts[1].x, pts[1].y)
                lineTo(pts[2].x, pts[2].y)
                lineTo(pts[3].x, pts[3].y)
                close()
            }
            drawPath(quadPath, color = SecondaryTeal.copy(alpha = 0.16f))
            drawPath(
                quadPath,
                color = SecondaryTeal,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            pts.forEachIndexed { idx, pt ->
                drawCircle(color = PrimaryBlue.copy(alpha = 0.35f), radius = 26f, center = pt)
                drawCircle(color = if (idx == activeCorner) StatusWarning else PrimaryBlue, radius = 14f, center = pt)
                drawCircle(color = Color.White, radius = 5f, center = pt)
            }
        }
    }
}
