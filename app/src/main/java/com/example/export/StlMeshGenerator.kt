package com.example.export

import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.domain.model.Point2D
import com.example.engine.KeyPolygon2D
import com.example.engine.KeyPolygonBuilder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z)
        return if (len > 1e-7f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
    }
}

data class Triangle3D(
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val normal: Vec3
)

data class Mesh3D(
    val triangles: List<Triangle3D>,
    val center: Vec3,
    val boundingRadius: Float
)

object StlMeshGenerator {

    fun generateMesh(record: KeyRecord): Mesh3D {
        val polygon = KeyPolygonBuilder.buildKeyPolygon(
            bladeLengthMm = record.bladeLengthMm,
            bladeWidthMm = record.bladeWidthMm,
            spacingsMm = record.spacingsMm,
            depthsMm = record.depthsMm,
            isDoubleSided = record.isDoubleSided
        )
        val spec = KeyStandards.getById(record.keyStandardId)
        return buildMeshFromPolygon(
            polygon = polygon,
            thicknessMm = record.bladeThicknessMm.coerceIn(1.4f, 3.5f),
            grooveOffsetsMm = if (record.isDoubleSided) {
                listOf(record.bladeWidthMm * 0.5f)
            } else {
                spec.grooveOffsetsMm
            },
            isDoubleSided = record.isDoubleSided
        )
    }

    fun buildMeshFromPolygon(
        polygon: KeyPolygon2D,
        thicknessMm: Float,
        grooveOffsetsMm: List<Float>,
        isDoubleSided: Boolean = false
    ): Mesh3D {
        val triangles = ArrayList<Triangle3D>(512)
        val halfZ = thicknessMm * 0.5f

        // 1. Bridge outer contour with bow keyring hole to form a single simple polygon for ear-clipping
        val merged2D = bridgeHoleIntoOuter(polygon.outerContour, polygon.bowHoleContour)
        val faceTriangles2D = earClipTriangulate(merged2D)

        // 2. Top face (+Z) and Bottom face (-Z)
        for (tri in faceTriangles2D) {
            val (a, b, c) = tri
            // Top face (+Z, CCW)
            addTri(
                triangles,
                Vec3(a.x, a.y, halfZ),
                Vec3(b.x, b.y, halfZ),
                Vec3(c.x, c.y, halfZ)
            )
            // Bottom face (-Z, reversed winding)
            addTri(
                triangles,
                Vec3(a.x, a.y, -halfZ),
                Vec3(c.x, c.y, -halfZ),
                Vec3(b.x, b.y, -halfZ)
            )
        }

        // 3. Outer perimeter extruded side walls
        addLoopWalls(triangles, polygon.outerContour, halfZ, outward = true)

        // 4. Inner keyring hole extruded side walls
        addLoopWalls(triangles, polygon.bowHoleContour, halfZ, outward = false)

        // 5. Longitudinal Keyway Warding Ribs (adds mechanical keyway depth profile along blade)
        val bladePts = polygon.outerContour.filter { it.x >= 0f }
        val minTopY = bladePts.filter { it.y >= polygon.bladeWidthMm * 0.45f }.minOfOrNull { it.y } ?: polygon.bladeWidthMm
        val maxBotY = if (isDoubleSided) {
            bladePts.filter { it.y <= polygon.bladeWidthMm * 0.55f }.maxOfOrNull { it.y } ?: 0f
        } else {
            0f
        }
        val ribEndX = (polygon.bladeLengthMm - 3.2f).coerceAtLeast(4f)
        val ribDepth = 0.28f
        val ribHeight = 0.70f
        for (offsetY in grooveOffsetsMm) {
            if (offsetY > maxBotY + 0.6f && offsetY < minTopY - 0.6f && ribEndX > 2f) {
                addBox(
                    triangles,
                    x0 = 0.4f, x1 = ribEndX,
                    y0 = offsetY - ribHeight * 0.5f, y1 = offsetY + ribHeight * 0.5f,
                    z0 = halfZ, z1 = halfZ + ribDepth
                )
                addBox(
                    triangles,
                    x0 = 0.4f, x1 = ribEndX,
                    y0 = offsetY - ribHeight * 0.5f, y1 = offsetY + ribHeight * 0.5f,
                    z0 = -halfZ - ribDepth, z1 = -halfZ
                )
            }
        }

        // Compute bounding center & radius for 3D viewer normalization
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (t in triangles) {
            for (v in listOf(t.v1, t.v2, t.v3)) {
                if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z; if (v.z > maxZ) maxZ = v.z
            }
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val dx = maxX - cx; val dy = maxY - cy; val dz = maxZ - cz
        val radius = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)

        return Mesh3D(
            triangles = triangles,
            center = Vec3(cx, cy, cz),
            boundingRadius = radius
        )
    }

    fun toBinaryStl(mesh: Mesh3D, headerTitle: String = "KeyCopier Pro Binary STL (mm)"): ByteArray {
        val triCount = mesh.triangles.size
        val out = ByteArrayOutputStream(84 + triCount * 50)
        val header = ByteArray(80)
        val titleBytes = headerTitle.toByteArray(Charsets.US_ASCII)
        System.arraycopy(titleBytes, 0, header, 0, minOf(titleBytes.size, 80))
        out.write(header)

        val buf = ByteBuffer.allocate(4 + triCount * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(triCount)
        for (tri in mesh.triangles) {
            buf.putFloat(tri.normal.x)
            buf.putFloat(tri.normal.y)
            buf.putFloat(tri.normal.z)
            buf.putFloat(tri.v1.x)
            buf.putFloat(tri.v1.y)
            buf.putFloat(tri.v1.z)
            buf.putFloat(tri.v2.x)
            buf.putFloat(tri.v2.y)
            buf.putFloat(tri.v2.z)
            buf.putFloat(tri.v3.x)
            buf.putFloat(tri.v3.y)
            buf.putFloat(tri.v3.z)
            buf.putShort(0) // attribute byte count
        }
        out.write(buf.array())
        return out.toByteArray()
    }

    fun toAsciiStl(mesh: Mesh3D, solidName: String = "KeyCopier_Key"): String {
        val sb = StringBuilder(mesh.triangles.size * 180)
        sb.append("solid ").append(solidName).append('\n')
        for (tri in mesh.triangles) {
            sb.append("  facet normal ${tri.normal.x} ${tri.normal.y} ${tri.normal.z}\n")
            sb.append("    outer loop\n")
            sb.append("      vertex ${tri.v1.x} ${tri.v1.y} ${tri.v1.z}\n")
            sb.append("      vertex ${tri.v2.x} ${tri.v2.y} ${tri.v2.z}\n")
            sb.append("      vertex ${tri.v3.x} ${tri.v3.y} ${tri.v3.z}\n")
            sb.append("    endloop\n")
            sb.append("  endfacet\n")
        }
        sb.append("endsolid ").append(solidName).append('\n')
        return sb.toString()
    }

    private fun addTri(out: MutableList<Triangle3D>, a: Vec3, b: Vec3, c: Vec3) {
        val n = ((b - a) cross (c - a)).normalized()
        out.add(Triangle3D(a, b, c, n))
    }

    private fun addLoopWalls(
        out: MutableList<Triangle3D>,
        loop: List<Point2D>,
        halfZ: Float,
        outward: Boolean
    ) {
        val ccw = signedArea(loop) > 0f
        val n = loop.size
        for (i in 0 until n) {
            val p1 = loop[i]
            val p2 = loop[(i + 1) % n]
            val top1 = Vec3(p1.x, p1.y, halfZ)
            val top2 = Vec3(p2.x, p2.y, halfZ)
            val bot1 = Vec3(p1.x, p1.y, -halfZ)
            val bot2 = Vec3(p2.x, p2.y, -halfZ)

            val flip = (ccw != outward)
            if (!flip) {
                addTri(out, top1, bot1, bot2)
                addTri(out, top1, bot2, top2)
            } else {
                addTri(out, top1, bot2, bot1)
                addTri(out, top1, top2, bot2)
            }
        }
    }

    private fun addBox(
        out: MutableList<Triangle3D>,
        x0: Float, x1: Float,
        y0: Float, y1: Float,
        z0: Float, z1: Float
    ) {
        val p000 = Vec3(x0, y0, z0); val p100 = Vec3(x1, y0, z0)
        val p110 = Vec3(x1, y1, z0); val p010 = Vec3(x0, y1, z0)
        val p001 = Vec3(x0, y0, z1); val p101 = Vec3(x1, y0, z1)
        val p111 = Vec3(x1, y1, z1); val p011 = Vec3(x0, y1, z1)

        // +Z & -Z
        addTri(out, p001, p101, p111); addTri(out, p001, p111, p011)
        addTri(out, p000, p110, p100); addTri(out, p000, p010, p110)
        // +Y & -Y
        addTri(out, p010, p011, p111); addTri(out, p010, p111, p110)
        addTri(out, p000, p101, p001); addTri(out, p000, p100, p101)
        // +X & -X
        addTri(out, p100, p110, p111); addTri(out, p100, p111, p101)
        addTri(out, p000, p001, p011); addTri(out, p000, p011, p010)
    }

    private fun bridgeHoleIntoOuter(
        outer: List<Point2D>,
        hole: List<Point2D>
    ): List<Point2D> {
        // Ensure outer is CCW and hole is CW
        val ccwOuter = if (signedArea(outer) >= 0f) outer else outer.reversed()
        val cwHole = if (signedArea(hole) <= 0f) hole else hole.reversed()
        if (cwHole.isEmpty()) return ccwOuter

        // Connect leftmost hole vertex to nearest outer vertex to the left (guaranteed unobstructed inside bow)
        var holeIdx = 0
        var minHoleX = Float.MAX_VALUE
        cwHole.forEachIndexed { idx, p ->
            if (p.x < minHoleX) {
                minHoleX = p.x
                holeIdx = idx
            }
        }
        val hp = cwHole[holeIdx]

        var outerIdx = 0
        var bestDist = Float.MAX_VALUE
        ccwOuter.forEachIndexed { idx, p ->
            val dx = p.x - hp.x
            val dy = p.y - hp.y
            val dist = dx * dx + dy * dy
            if (p.x <= hp.x + 2f && dist < bestDist) {
                bestDist = dist
                outerIdx = idx
            }
        }

        val merged = ArrayList<Point2D>(ccwOuter.size + cwHole.size + 2)
        for (i in 0..outerIdx) merged.add(ccwOuter[i])
        for (i in 0 until cwHole.size) {
            merged.add(cwHole[(holeIdx + i) % cwHole.size])
        }
        merged.add(cwHole[holeIdx])
        for (i in outerIdx until ccwOuter.size) merged.add(ccwOuter[i])
        return merged
    }

    private fun signedArea(pts: List<Point2D>): Float {
        var area = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            area += (a.x * b.y - b.x * a.y)
        }
        return area * 0.5f
    }

    private fun earClipTriangulate(polygon: List<Point2D>): List<Triple<Point2D, Point2D, Point2D>> {
        val pts = polygon.toMutableList()
        if (signedArea(pts) < 0f) pts.reverse()

        val result = ArrayList<Triple<Point2D, Point2D, Point2D>>(pts.size)
        var guard = 0
        val maxIter = pts.size * pts.size + 10

        while (pts.size > 3 && guard < maxIter) {
            guard++
            var earFound = false
            val n = pts.size
            for (i in 0 until n) {
                val prev = pts[(i - 1 + n) % n]
                val curr = pts[i]
                val next = pts[(i + 1) % n]

                if (cross2D(prev, curr, next) > 1e-6f) {
                    var containsPoint = false
                    for (j in 0 until n) {
                        if (j == (i - 1 + n) % n || j == i || j == (i + 1) % n) continue
                        if (pointInTriangle(pts[j], prev, curr, next)) {
                            containsPoint = true
                            break
                        }
                    }
                    if (!containsPoint) {
                        result.add(Triple(prev, curr, next))
                        pts.removeAt(i)
                        earFound = true
                        break
                    }
                }
            }
            if (!earFound) break
        }
        if (pts.size == 3) {
            result.add(Triple(pts[0], pts[1], pts[2]))
        }
        return result
    }

    private fun cross2D(a: Point2D, b: Point2D, c: Point2D): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun pointInTriangle(p: Point2D, a: Point2D, b: Point2D, c: Point2D): Boolean {
        val c1 = cross2D(a, b, p)
        val c2 = cross2D(b, c, p)
        val c3 = cross2D(c, a, p)
        val hasNeg = (c1 < -1e-5f) || (c2 < -1e-5f) || (c3 < -1e-5f)
        val hasPos = (c1 > 1e-5f) || (c2 > 1e-5f) || (c3 > 1e-5f)
        return !(hasNeg && hasPos) && abs(cross2D(a, b, c)) > 1e-6f
    }
}
