package com.example

import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.domain.model.Point2D
import com.example.engine.BittingCalculator
import com.example.engine.KeyPolygonBuilder
import com.example.engine.PerspectiveCalibrator
import com.example.export.DxfTemplateGenerator
import com.example.export.StlMeshGenerator
import com.example.export.SvgTemplateGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEngineAndExportTest {

    @Test
    fun perspectiveHomography_mapsTiltedQuadToExactId1Dimensions() {
        val src = listOf(
            Point2D(120f, 90f),
            Point2D(520f, 110f),
            Point2D(560f, 360f),
            Point2D(90f, 340f)
        )
        val dst = listOf(
            Point2D(0f, 0f),
            Point2D(PerspectiveCalibrator.ID1_WIDTH_MM, 0f),
            Point2D(PerspectiveCalibrator.ID1_WIDTH_MM, PerspectiveCalibrator.ID1_HEIGHT_MM),
            Point2D(0f, PerspectiveCalibrator.ID1_HEIGHT_MM)
        )

        val h = PerspectiveCalibrator.computeHomography(src, dst)
        assertNotNull(h)

        src.forEachIndexed { idx, pt ->
            val mapped = PerspectiveCalibrator.applyHomography(h!!, pt)
            assertEquals(dst[idx].x, mapped.x, 0.02f)
            assertEquals(dst[idx].y, mapped.y, 0.02f)
        }
    }

    @Test
    fun bittingCalculator_decodesAndRoundTripsSchlageSc1() {
        val sc1 = KeyStandards.getById("SC1")
        val targetCode = "35274"
        val depths = BittingCalculator.depthsFromBittingCode(sc1, targetCode, 5)
        val spacings = KeyStandards.defaultSpacings(sc1, 5)

        val decoded = BittingCalculator.decode(sc1, depths, spacings, sc1.bladeWidthMm)
        assertEquals(targetCode, decoded.bittingCode)
        assertEquals(5, decoded.pins.size)
        assertEquals(0f, decoded.maxErrorMm, 0.001f)
    }

    @Test
    fun stlMeshGenerator_producesValidBinaryStlAndAsciiStl() {
        val sc1 = KeyStandards.getById("SC1")
        val record = KeyRecord(
            id = 1L,
            name = "Front Door",
            category = KeyCategory.HOME,
            keyStandardId = "SC1",
            bittingCode = "35274",
            bladeLengthMm = sc1.bladeLengthMm,
            bladeWidthMm = sc1.bladeWidthMm,
            bladeThicknessMm = sc1.bladeThicknessMm,
            pinCount = 5,
            depthsMm = BittingCalculator.depthsFromBittingCode(sc1, "35274", 5),
            spacingsMm = KeyStandards.defaultSpacings(sc1, 5)
        )

        val polygon = KeyPolygonBuilder.buildKeyPolygon(
            record.bladeLengthMm,
            record.bladeWidthMm,
            record.spacingsMm,
            record.depthsMm
        )
        assertTrue(polygon.outerContour.size > 20)
        assertEquals(16, polygon.bowHoleContour.size)

        val mesh = StlMeshGenerator.generateMesh(record)
        assertTrue(mesh.triangles.size > 80)

        val binary = StlMeshGenerator.toBinaryStl(mesh)
        assertEquals(84 + mesh.triangles.size * 50, binary.size)

        val ascii = StlMeshGenerator.toAsciiStl(mesh)
        assertTrue(ascii.startsWith("solid KeyCopier_Key"))
        assertTrue(ascii.contains("endsolid KeyCopier_Key"))
    }

    @Test
    fun svgAndDxfGenerators_includeExactMillimeterGeometry() {
        val kw1 = KeyStandards.getById("KW1")
        val record = KeyRecord(
            id = 2L,
            name = "Office Entry",
            category = KeyCategory.OFFICE,
            keyStandardId = "KW1",
            bittingCode = "24635",
            bladeLengthMm = kw1.bladeLengthMm,
            bladeWidthMm = kw1.bladeWidthMm,
            bladeThicknessMm = kw1.bladeThicknessMm,
            pinCount = 5,
            depthsMm = BittingCalculator.depthsFromBittingCode(kw1, "24635", 5),
            spacingsMm = KeyStandards.defaultSpacings(kw1, 5)
        )

        val svg = SvgTemplateGenerator.generateSvg(record)
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("mm\""))
        assertTrue(svg.contains("Bitting: 24635"))

        val dxf = DxfTemplateGenerator.generateDxf(record)
        assertTrue(dxf.contains("POLYLINE"))
        assertTrue(dxf.contains("KEY_OUTLINE"))
        assertTrue(dxf.contains("BOW_HOLE"))
        assertTrue(dxf.endsWith("EOF\n"))
    }

    @Test
    fun keyPolygonBuilder_handlesTightPinSpacingsAndShortBladeWithoutCrashing() {
        val polygon = KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = 28.5f,
            bladeWidthMm = 8.46f,
            spacingsMm = listOf(5.8f, 10.0f, 15.0f, 22.0f, 27.5f),
            depthsMm = listOf(3.4f, 3.4f, 3.4f, 3.4f, 3.4f)
        )
        assertTrue(polygon.outerContour.isNotEmpty())

        val extremePolygon = KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = 18.0f,
            bladeWidthMm = 5.5f,
            spacingsMm = listOf(5.0f, 9.0f, 13.0f, 17.0f, 21.0f, 25.0f),
            depthsMm = listOf(4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f)
        )
        assertTrue(extremePolygon.outerContour.isNotEmpty())
    }

    @Test
    fun doubleSidedKey_producesMirroredCutsAndValidExports() {
        val sc1 = KeyStandards.getById("SC1")
        val depths = listOf(0.00f, 1.14f, 2.29f, 3.05f, 0.76f)
        val spacings = KeyStandards.defaultSpacings(sc1, 5)
        val poly = KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = sc1.bladeLengthMm,
            bladeWidthMm = sc1.bladeWidthMm,
            spacingsMm = spacings,
            depthsMm = depths,
            isDoubleSided = true
        )
        // Double-sided blade must contain cut points on both upper half (> width/2) and lower mirrored half (< width/2)
        val hasBottomMirroredCut = poly.outerContour.any { it.x > 4f && it.y > 0.2f && it.y < sc1.bladeWidthMm * 0.48f }
        assertTrue(hasBottomMirroredCut)

        val record = KeyRecord(
            id = 9L,
            name = "Double Sided Auto",
            category = KeyCategory.VEHICLE,
            keyStandardId = "SC1",
            bittingCode = "03682",
            bladeLengthMm = sc1.bladeLengthMm,
            bladeWidthMm = sc1.bladeWidthMm,
            bladeThicknessMm = sc1.bladeThicknessMm,
            pinCount = 5,
            depthsMm = depths,
            spacingsMm = spacings,
            isDoubleSided = true
        )
        val mesh = StlMeshGenerator.generateMesh(record)
        assertTrue(mesh.triangles.size > 80)
        assertTrue(SvgTemplateGenerator.generateSvg(record).contains("<svg"))
        assertTrue(DxfTemplateGenerator.generateDxf(record).endsWith("EOF\n"))
    }
}
