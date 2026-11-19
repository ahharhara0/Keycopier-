package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.engine.KeyPolygonBuilder
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Generates an exact physical 1:1 scale PDF blueprint (72 PostScript points per inch -> 1 mm = 2.83464567 pt)
 * including a 50.00 x 50.00 mm printer verification square and pin depth table.
 */
object PdfScaleTemplateGenerator {

    private const val MM_TO_PT = 72f / 25.4f
    private const val PAGE_WIDTH_PT = 595 // A4 width (210mm)
    private const val PAGE_HEIGHT_PT = 842 // A4 height (297mm)

    fun generate1To1PdfBytes(record: KeyRecord): ByteArray {
        return runCatching {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
            val page = doc.startPage(pageInfo)
            drawBlueprintPage(page.canvas, record)
            doc.finishPage(page)

            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }.getOrElse {
            generateVectorPdf14Fallback(record)
        }
    }

    private fun generateVectorPdf14Fallback(record: KeyRecord): ByteArray {
        val polygon = KeyPolygonBuilder.buildFromRecord(record)
        val stream = buildString {
            val originX = 42f + polygon.bowLengthMm * MM_TO_PT + 30f
            val baseY = 580f
            append("0.14 0.39 0.92 RG 1.0 w\n")
            polygon.outerContour.forEachIndexed { idx, p ->
                val x = originX + p.x * MM_TO_PT
                val y = baseY + p.y * MM_TO_PT
                append(String.format(Locale.US, "%.2f %.2f %s\n", x, y, if (idx == 0) "m" else "l"))
            }
            append("h S\n")
            polygon.bowHoleContour.forEachIndexed { idx, p ->
                val x = originX + p.x * MM_TO_PT
                val y = baseY + p.y * MM_TO_PT
                append(String.format(Locale.US, "%.2f %.2f %s\n", x, y, if (idx == 0) "m" else "l"))
            }
            append("h S\n")
            // 50x50mm calibration square
            val sq = 50f * MM_TO_PT
            append(String.format(Locale.US, "410.00 540.00 %.2f %.2f re S\n", sq, sq))
            append("BT /F1 14 Tf 42 780 Td (KEYCOPIER PRO 1:1 TEMPLATE - ${record.keyStandardId} ${record.bittingCode}) Tj ET\n")
        }
        val objs = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH_PT $PAGE_HEIGHT_PT] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
            "5 0 obj\n<< /Length ${stream.length} >>\nstream\n$stream\nendstream\nendobj\n"
        )
        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (obj in objs) {
            offsets.add(pdf.length)
            pdf.append(obj)
        }
        val xrefPos = pdf.length
        pdf.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        for (off in offsets) {
            pdf.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        pdf.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        return pdf.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun drawBlueprintPage(canvas: Canvas, record: KeyRecord) {
        val spec = KeyStandards.getById(record.keyStandardId)
        val polygon = KeyPolygonBuilder.buildFromRecord(record)

        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#172033")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#728096")
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#172033")
            strokeWidth = 1.1f
            style = Paint.Style.STROKE
        }
        val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.FILL
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            strokeWidth = 0.7f
            style = Paint.Style.STROKE
        }
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0EA5A4")
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
        }

        val leftMargin = 42f
        canvas.drawText("KEYCOPIER PRO — 1:1 PHYSICAL FABRICATION TEMPLATE", leftMargin, 48f, titlePaint)
        canvas.drawText(
            "PRINT AT 100% / ACTUAL SIZE (DISABLE 'FIT TO PAGE') • VERIFY 50x50 MM CALIBRATION SQUARE",
            leftMargin,
            64f,
            subtitlePaint
        )
        canvas.drawLine(leftMargin, 74f, PAGE_WIDTH_PT - leftMargin, 74f, linePaint)

        // Key Metadata Header
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#172033")
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("PROFILE : ${record.name}", leftMargin, 96f, monoPaint)
        canvas.drawText("STANDARD: ${spec.displayName}", leftMargin, 112f, monoPaint)
        canvas.drawText("BITTING : ${record.bittingCode}", leftMargin, 128f, monoPaint)
        canvas.drawText(
            String.format(
                Locale.US,
                "DIMS    : Blade %.2f mm × %.2f mm × %.2f mm (%d Pins)",
                record.bladeLengthMm, record.bladeWidthMm, record.bladeThicknessMm, record.pinCount
            ),
            leftMargin,
            144f,
            monoPaint
        )

        // 1:1 Key Silhouette Section
        val keyOriginX = leftMargin + polygon.bowLengthMm * MM_TO_PT + 30f
        val keyBaselineY = 260f

        canvas.drawText("1:1 ACTUAL SIZE CUTTING TEMPLATE", leftMargin, 185f, subtitlePaint)

        val outerPath = Path()
        polygon.outerContour.forEachIndexed { idx, p ->
            val px = keyOriginX + p.x * MM_TO_PT
            val py = keyBaselineY - p.y * MM_TO_PT
            if (idx == 0) outerPath.moveTo(px, py) else outerPath.lineTo(px, py)
        }
        outerPath.close()

        val holePath = Path()
        polygon.bowHoleContour.forEachIndexed { idx, p ->
            val px = keyOriginX + p.x * MM_TO_PT
            val py = keyBaselineY - p.y * MM_TO_PT
            if (idx == 0) holePath.moveTo(px, py) else holePath.lineTo(px, py)
        }
        holePath.close()

        canvas.drawPath(outerPath, keyFillPaint)
        canvas.drawPath(outerPath, keyStrokePaint)
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(holePath, whiteFill)
        canvas.drawPath(holePath, keyStrokePaint)

        // Shoulder crosshair & Pin cut markers
        canvas.drawLine(keyOriginX, keyBaselineY + 18f, keyOriginX, keyBaselineY - record.bladeWidthMm * MM_TO_PT - 22f, dashPaint)
        record.spacingsMm.forEachIndexed { idx, spacing ->
            val depth = record.depthsMm.getOrElse(idx) { 0f }
            val px = keyOriginX + spacing * MM_TO_PT
            val pyCut = keyBaselineY - (record.bladeWidthMm - depth) * MM_TO_PT
            canvas.drawLine(px, pyCut, px, keyBaselineY - record.bladeWidthMm * MM_TO_PT - 14f, dashPaint)
            canvas.drawText("P${idx + 1}", px - 5f, keyBaselineY - record.bladeWidthMm * MM_TO_PT - 18f, subtitlePaint)
        }

        // 50mm x 50mm Printer Scale Calibration Square (on right side)
        val calSizePt = 50f * MM_TO_PT
        val calX = PAGE_WIDTH_PT - leftMargin - calSizePt
        val calY = 185f
        canvas.drawRect(calX, calY, calX + calSizePt, calY + calSizePt, accentPaint)
        canvas.drawLine(calX, calY + calSizePt * 0.5f, calX + calSizePt, calY + calSizePt * 0.5f, dashPaint)
        canvas.drawLine(calX + calSizePt * 0.5f, calY, calX + calSizePt * 0.5f, calY + calSizePt, dashPaint)
        canvas.drawText("50.00 × 50.00 mm", calX + 18f, calY + calSizePt * 0.5f - 6f, subtitlePaint)
        canvas.drawText("SCALE CHECK", calX + 34f, calY + calSizePt * 0.5f + 12f, subtitlePaint)

        // 1:1 Pin Cut Table
        var tableY = 365f
        canvas.drawLine(leftMargin, tableY - 14f, PAGE_WIDTH_PT - leftMargin, tableY - 14f, linePaint)
        canvas.drawText("PIN", leftMargin, tableY, monoPaint)
        canvas.drawText("SPACING (FROM SHOULDER)", leftMargin + 50f, tableY, monoPaint)
        canvas.drawText("CUT DEPTH", leftMargin + 225f, tableY, monoPaint)
        canvas.drawText("REMAINING BLADE", leftMargin + 325f, tableY, monoPaint)
        canvas.drawText("CODE", leftMargin + 460f, tableY, monoPaint)
        tableY += 8f
        canvas.drawLine(leftMargin, tableY, PAGE_WIDTH_PT - leftMargin, tableY, linePaint)
        tableY += 18f

        val digits = record.bittingCode.filter { it.isDigit() }
        record.spacingsMm.forEachIndexed { idx, spacing ->
            val depth = record.depthsMm.getOrElse(idx) { 0f }
            val remaining = (record.bladeWidthMm - depth).coerceAtLeast(0f)
            val codeChar = digits.getOrNull(idx)?.toString() ?: "-"
            canvas.drawText("#${idx + 1}", leftMargin, tableY, monoPaint)
            canvas.drawText(String.format(Locale.US, "%.2f mm", spacing), leftMargin + 50f, tableY, monoPaint)
            canvas.drawText(String.format(Locale.US, "%.2f mm", depth), leftMargin + 225f, tableY, monoPaint)
            canvas.drawText(String.format(Locale.US, "%.2f mm", remaining), leftMargin + 325f, tableY, monoPaint)
            canvas.drawText(codeChar, leftMargin + 460f, tableY, monoPaint)
            tableY += 18f
        }

        // Millimeter Reference Ruler at bottom (0..80mm)
        val rulerY = 680f
        canvas.drawText("80 mm CALIBRATION RULER (1:1)", leftMargin, rulerY - 14f, subtitlePaint)
        canvas.drawLine(leftMargin, rulerY, leftMargin + 80f * MM_TO_PT, rulerY, keyStrokePaint)
        for (mm in 0..80) {
            val rx = leftMargin + mm * MM_TO_PT
            val tickH = when {
                mm % 10 == 0 -> 14f
                mm % 5 == 0 -> 9f
                else -> 5f
            }
            canvas.drawLine(rx, rulerY, rx, rulerY + tickH, keyStrokePaint)
            if (mm % 10 == 0) {
                canvas.drawText("$mm", rx - 4f, rulerY + 26f, subtitlePaint)
            }
        }
    }
}
