package com.example.export

import com.example.domain.model.KeyRecord
import com.example.engine.KeyPolygonBuilder
import java.util.Locale

/**
 * Generates standard AutoCAD DXF (millimeter units) with closed POLYLINE profiles
 * for CNC milling and laser cutting.
 */
object DxfTemplateGenerator {

    fun generateDxf(record: KeyRecord): String {
        val polygon = KeyPolygonBuilder.buildFromRecord(record)

        val sb = StringBuilder(2048)
        // Minimal compliant DXF HEADER
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$INSUNITS\n70\n4\n") // 4 = Millimeters
        sb.append("0\nENDSEC\n")

        // ENTITIES Section
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Outer Key Profile as closed POLYLINE (R12 compatible)
        sb.append("0\nPOLYLINE\n8\nKEY_OUTLINE\n66\n1\n70\n1\n")
        for (p in polygon.outerContour) {
            sb.append("0\nVERTEX\n8\nKEY_OUTLINE\n")
            sb.append(String.format(Locale.US, "10\n%.4f\n20\n%.4f\n30\n0.0\n", p.x, p.y))
        }
        sb.append("0\nSEQEND\n8\nKEY_OUTLINE\n")

        // Bow Keyring Hole as closed POLYLINE
        sb.append("0\nPOLYLINE\n8\nBOW_HOLE\n66\n1\n70\n1\n")
        for (p in polygon.bowHoleContour) {
            sb.append("0\nVERTEX\n8\nBOW_HOLE\n")
            sb.append(String.format(Locale.US, "10\n%.4f\n20\n%.4f\n30\n0.0\n", p.x, p.y))
        }
        sb.append("0\nSEQEND\n8\nBOW_HOLE\n")

        // Pin Centerlines
        record.spacingsMm.forEachIndexed { idx, x ->
            val depth = record.depthsMm.getOrElse(idx) { 0f }
            val yCut = record.bladeWidthMm - depth
            sb.append("0\nLINE\n8\nPIN_CENTERS\n")
            sb.append(String.format(Locale.US, "10\n%.4f\n20\n%.4f\n30\n0.0\n", x, yCut))
            sb.append(String.format(Locale.US, "11\n%.4f\n21\n%.4f\n31\n0.0\n", x, record.bladeWidthMm + 2f))
        }

        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }
}
