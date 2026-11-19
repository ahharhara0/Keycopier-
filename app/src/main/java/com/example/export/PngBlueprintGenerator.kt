package com.example.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.engine.KeyPolygonBuilder
import java.io.ByteArrayOutputStream
import java.util.Locale

object PngBlueprintGenerator {

    fun generateBlueprintPngBytes(record: KeyRecord): ByteArray {
        val width = 1600
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Blueprint dark navy background
        canvas.drawColor(Color.parseColor("#0F172A"))

        val gridPaint = Paint().apply {
            color = Color.parseColor("#1E3A5F")
            strokeWidth = 1.5f
        }
        val step = 40f
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }

        val spec = KeyStandards.getById(record.keyStandardId)
        val polygon = KeyPolygonBuilder.buildFromRecord(record)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            typeface = Typeface.MONOSPACE
        }
        val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 26f
            typeface = Typeface.MONOSPACE
        }

        canvas.drawText("KEYCOPIER PRO — CAD BLUEPRINT", 64f, 82f, titlePaint)
        canvas.drawText(
            "${record.name}  |  ${spec.displayName}  |  BITTING: ${record.bittingCode}",
            64f,
            124f,
            accentTextPaint
        )

        val scale = 22f // 22 px per mm
        val originX = 640f
        val baseY = 470f

        val keyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            alpha = 110
            style = Paint.Style.FILL
        }
        val keyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        val holeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }

        val outerPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val px = originX + p.x * scale
            val py = baseY - p.y * scale
            if (idx == 0) outerPath.moveTo(px, py) else outerPath.lineTo(px, py)
        }
        outerPath.close()

        val holePath = Path()
        polygon.bowHoleContour.forEachIndexed { idx, p ->
            val px = originX + p.x * scale
            val py = baseY - p.y * scale
            if (idx == 0) holePath.moveTo(px, py) else holePath.lineTo(px, py)
        }
        holePath.close()

        canvas.drawPath(outerPath, keyFill)
        canvas.drawPath(outerPath, keyStroke)
        canvas.drawPath(holePath, holeFill)
        canvas.drawPath(holePath, keyStroke)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14B8A6")
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        }
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            style = Paint.Style.FILL
        }

        record.spacingsMm.forEachIndexed { idx, spacing ->
            val depth = record.depthsMm.getOrElse(idx) { 0f }
            val px = originX + spacing * scale
            val pyCut = baseY - (record.bladeWidthMm - depth) * scale
            canvas.drawLine(px, pyCut, px, baseY - record.bladeWidthMm * scale - 60f, pinPaint)
            canvas.drawCircle(px, pyCut, 8f, nodePaint)
            canvas.drawText(
                String.format(Locale.US, "P%d: %.2f", idx + 1, depth),
                px - 40f,
                baseY - record.bladeWidthMm * scale - 74f,
                subPaint
            )
        }

        // Bottom engineering spec bar
        val specSummary = String.format(
            Locale.US,
            "BLADE: %.2f × %.2f × %.2f mm   |   PINS: %d   |   100%% OFFLINE VAULT",
            record.bladeLengthMm, record.bladeWidthMm, record.bladeThicknessMm, record.pinCount
        )
        canvas.drawText(specSummary, 64f, 920f, subPaint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
