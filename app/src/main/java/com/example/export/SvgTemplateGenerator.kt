package com.example.export

import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.engine.KeyPolygonBuilder
import java.util.Locale

object SvgTemplateGenerator {

    fun generateSvg(record: KeyRecord): String {
        val polygon = KeyPolygonBuilder.buildFromRecord(record)
        val spec = KeyStandards.getById(record.keyStandardId)

        val margin = 8.0f
        val minX = -polygon.bowLengthMm - margin
        val maxX = polygon.bladeLengthMm + margin
        val minY = -12f - margin
        val maxY = polygon.bladeWidthMm + 12f + margin
        val widthMm = maxX - minX
        val heightMm = maxY - minY

        // Flip Y for SVG top-down coordinates
        fun sx(x: Float) = String.format(Locale.US, "%.3f", x - minX)
        fun sy(y: Float) = String.format(Locale.US, "%.3f", maxY - y)

        val outerPath = buildString {
            polygon.outerContour.forEachIndexed { idx, p ->
                append(if (idx == 0) "M " else " L ")
                append("${sx(p.x)} ${sy(p.y)}")
            }
            append(" Z")
        }

        val holePath = buildString {
            polygon.bowHoleContour.forEachIndexed { idx, p ->
                append(if (idx == 0) "M " else " L ")
                append("${sx(p.x)} ${sy(p.y)}")
            }
            append(" Z")
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                String.format(
                    Locale.US,
                    """<svg xmlns="http://www.w3.org/2000/svg" width="%.2fmm" height="%.2fmm" viewBox="0 0 %.2f %.2f">""",
                    widthMm, heightMm, widthMm, heightMm
                )
            )
            appendLine("""  <title>${escapeXml(record.name)} (${record.bittingCode}) - 1:1 Scale</title>""")
            appendLine("""  <!-- 1:1 Millimeter Vector Cut Path -->""")
            appendLine("""  <g id="key_cut_profile" fill="none" stroke="#2563EB" stroke-width="0.25">""")
            appendLine("""    <path d="$outerPath" fill="#EFF6FF" fill-opacity="0.35" />""")
            appendLine("""    <path d="$holePath" fill="#FFFFFF" />""")
            appendLine("""  </g>""")
            appendLine("""  <!-- Pin Cut Centerlines -->""")
            appendLine("""  <g id="pin_markers" stroke="#0EA5A4" stroke-width="0.15" stroke-dasharray="0.6,0.4">""")
            record.spacingsMm.forEachIndexed { idx, spacing ->
                val depth = record.depthsMm.getOrElse(idx) { 0f }
                val cutY = record.bladeWidthMm - depth
                appendLine("""    <line x1="${sx(spacing)}" y1="${sy(record.bladeWidthMm + 2.5f)}" x2="${sx(spacing)}" y2="${sy(cutY)}" />""")
                appendLine("""    <circle cx="${sx(spacing)}" cy="${sy(cutY)}" r="0.35" fill="#0EA5A4" />""")
            }
            appendLine("""  </g>""")
            appendLine("""  <!-- Engraving / Spec Metadata -->""")
            appendLine("""  <text x="${sx(-polygon.bowLengthMm + 2f)}" y="${sy(-10.5f)}" font-family="monospace" font-size="2.4" fill="#172033">${escapeXml(record.name)} | ${spec.id} | Bitting: ${record.bittingCode} | ${String.format(Locale.US, "%.2f", record.bladeLengthMm)}x${String.format(Locale.US, "%.2f", record.bladeWidthMm)}mm</text>""")
            appendLine("""</svg>""")
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
