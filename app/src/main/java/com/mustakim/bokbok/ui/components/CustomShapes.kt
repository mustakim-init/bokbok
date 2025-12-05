package com.mustakim.bokbok.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shape describing star with rounded corners
 * Copied from PixelPlay reference project
 */
class RoundedStarShape(
    private val sides: Int,
    private val curve: Double = 0.09,
    private val rotation: Float = 0f,
    iterations: Int = 360,
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val steps = (TWO_PI) / min(iterations, 360)
    private val rotationDegree = (PI / 180) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        val r = min(size.height, size.width) * 0.5f * mapRange(1.0, 0.0, 0.5, 1.0, curve) // Fixed radius calculation
        val xCenter = size.width * 0.5f
        val yCenter = size.height * 0.5f

        // Calculate first point
        var t = 0.0
        val x0 = r * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
        val y0 = r * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
        
        moveTo((x0 + xCenter).toFloat(), (y0 + yCenter).toFloat())

        t += steps
        while (t <= TWO_PI) {
            val x = r * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
            val y = r * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
            lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
            t += steps
        }
        
        // Close back to start
        lineTo((x0 + xCenter).toFloat(), (y0 + yCenter).toFloat())
        close()
    })

    private fun mapRange(a: Double, b: Double, c: Double, d: Double, x: Double): Double {
        return (x - a) / (b - a) * (d - c) + c
    }
}

/**
 * Shape describing Polygons (hexagon, pentagon, etc.)
 */
class PolygonShape(
    private val sides: Int, 
    private val rotation: Float = 0f
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val stepCount = ((TWO_PI) / sides)
    private val rotationDegree = (PI / 180) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        val r = min(size.height, size.width) * 0.5f
        val xCenter = size.width * 0.5f
        val yCenter = size.height * 0.5f

        // Start from first vertex
        val startX = r * cos(-rotationDegree)
        val startY = r * sin(-rotationDegree)
        moveTo((startX + xCenter).toFloat(), (startY + yCenter).toFloat())

        var t = -rotationDegree + stepCount
        // Iterate through all vertices
        repeat(sides) {
            val x = r * cos(t)
            val y = r * sin(t)
            lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
            t += stepCount
        }
        
        close()
    })
}

/**
 * Creates a simple hexagon shape
 */
fun createHexagonShape() = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val radius = min(size.width, size.height) / 2f
            val width = size.width
            val height = size.height
            val angle = 2.0 * PI / 6
            
            val startX = width / 2f + radius * cos(0.0).toFloat()
            val startY = height / 2f + radius * sin(0.0).toFloat()
            
            moveTo(startX, startY)
            
            for (i in 1..6) {
                val x = width / 2f + radius * cos(angle * i).toFloat()
                val y = height / 2f + radius * sin(angle * i).toFloat()
                lineTo(x, y)
            }
            close()
        })
    }
}

/**
 * Creates a hexagon shape with rounded corners
 */
fun createRoundedHexagonShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = min(width, height) / 2f
            // Clamp corner radius to max 50% of radius to prevent crash when size is small
            val maxCornerRadius = radius * 0.5f
            val cornerRadiusPx = min(with(density) { cornerRadius.toPx() }, maxCornerRadius)

            // Center of the shape
            val centerX = width / 2f
            val centerY = height / 2f

            // Calculate vertices of the hexagon
            val vertices = (0..5).map { i ->
                val angle = (PI / 3 * i) - (PI / 6) // Start at 30 degrees (flat top/bottom if rotated) or pointy top
                // Let's stick to standard pointy-top orientation (starts at -90 deg or similar)
                // Actually, standard hexagon usually starts at 0 (right).
                // Let's use the same angle logic as before but be precise.
                val theta = PI / 3 * i
                Offset(
                    x = centerX + radius * cos(theta).toFloat(),
                    y = centerY + radius * sin(theta).toFloat()
                )
            }

            // Move to the start of the first rounded corner
            // We start at vertex 0. The corner starts "before" vertex 0 and ends "after" it.
            // Actually, simpler: Iterate edges.
            // For each vertex, we draw a line from the previous corner end to this corner start.
            // Then draw the corner.
            
            // Let's calculate the start point (end of corner 5 / start of edge 0)
            // Vertex 0
            val p0 = vertices[0]
            val p5 = vertices[5]
            val p1 = vertices[1]
            
            // Vector from p0 to p1 (next)
            val v01 = p1 - p0
            val len01 = sqrt(v01.x * v01.x + v01.y * v01.y)
            val u01 = v01 / len01 // Unit vector
            
            // Start point of the path (after corner 0, on edge 0-1)
            val startX = p0.x + u01.x * cornerRadiusPx
            val startY = p0.y + u01.y * cornerRadiusPx
            
            moveTo(startX, startY)

            for (i in 0 until 6) {
                val current = vertices[i]
                val next = vertices[(i + 1) % 6]
                val nextNext = vertices[(i + 2) % 6]
                
                // Vector from current to next
                val vCurrentNext = next - current
                val lenCurrentNext = sqrt(vCurrentNext.x * vCurrentNext.x + vCurrentNext.y * vCurrentNext.y)
                val uCurrentNext = vCurrentNext / lenCurrentNext
                
                // Vector from next to nextNext
                val vNextNextNext = nextNext - next
                val lenNextNextNext = sqrt(vNextNextNext.x * vNextNextNext.x + vNextNextNext.y * vNextNextNext.y)
                val uNextNextNext = vNextNextNext / lenNextNextNext
                
                // Line to the start of the corner at 'next'
                // End of edge is 'next' minus cornerRadius in direction of edge
                val lineEndX = next.x - uCurrentNext.x * cornerRadiusPx
                val lineEndY = next.y - uCurrentNext.y * cornerRadiusPx
                
                lineTo(lineEndX, lineEndY)
                
                // Curve to the start of the next edge
                // Start of next edge is 'next' plus cornerRadius in direction of next edge
                val curveEndX = next.x + uNextNextNext.x * cornerRadiusPx
                val curveEndY = next.y + uNextNextNext.y * cornerRadiusPx
                
                // Use quadratic bezier with control point at the vertex 'next'
                quadraticBezierTo(next.x, next.y, curveEndX, curveEndY)
            }
            close()
        })
    }
}

/**
 * Creates a triangle shape with rounded corners
 */
fun createRoundedTriangleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            // Clamp corner radius to prevent crash
            val maxCornerRadius = min(width, height) * 0.25f
            val cornerRadiusPx = min(with(density) { cornerRadius.toPx() }, maxCornerRadius)

            // Triangle points
            val p1 = Offset(width / 2f, 0f) // Top
            val p2 = Offset(width, height) // Bottom right
            val p3 = Offset(0f, height) // Bottom left

            // Start after top corner
            moveTo(p1.x + cornerRadiusPx, p1.y + cornerRadiusPx)

            // Top corner
            quadraticBezierTo(p1.x, p1.y, p1.x - cornerRadiusPx, p1.y + cornerRadiusPx)
            
            // Line to bottom left
            lineTo(p3.x + cornerRadiusPx, p3.y - cornerRadiusPx)
            
            // Bottom left corner
            quadraticBezierTo(p3.x, p3.y, p3.x + cornerRadiusPx * 2, p3.y)
            
            // Line to bottom right
            lineTo(p2.x - cornerRadiusPx * 2, p2.y)
            
            // Bottom right corner
            quadraticBezierTo(p2.x, p2.y, p2.x - cornerRadiusPx, p2.y - cornerRadiusPx)
            
            close()
        })
    }
}

/**
 * Creates a squircle (rounded square) shape
 */
class SquircleShape(private val cornerRadiusPercent: Float = 30f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = min(size.width, size.height) * (cornerRadiusPercent / 100f)
        return Outline.Generic(Path().apply {
            moveTo(0f, radius)
            quadraticBezierTo(0f, 0f, radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, radius)
            lineTo(size.width, size.height - radius)
            quadraticBezierTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - radius)
            close()
        })
    }
}

/**
 * A clover/flower-like shape
 */
class CloverShape(private val petalCurve: Float = 0.3f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        val r = min(size.height, size.width) * 0.5f
        val xCenter = size.width * 0.5f
        val yCenter = size.height * 0.5f
        
        val steps = 360
        val angleStep = (2 * PI) / steps
        
        // Start point
        var angle = 0.0
        val startR = r * (1.0 - petalCurve * 0.5 + petalCurve * cos(4 * angle))
        val startX = xCenter + startR * cos(angle)
        val startY = yCenter + startR * sin(angle)
        
        moveTo(startX.toFloat(), startY.toFloat())
        
        for (i in 1..steps) {
            angle = i * angleStep
            // Formula for 4-petal clover
            val currentR = r * (1.0 - petalCurve * 0.5 + petalCurve * cos(4 * angle))
            val x = xCenter + currentR * cos(angle)
            val y = yCenter + currentR * sin(angle)
            lineTo(x.toFloat(), y.toFloat())
        }
        
        close()
    })
}

/**
 * Scallop shape with smooth rounded edges - from FriendsStatusSection
 * This creates beautiful flower/star-like shapes using Catmull-Rom splines
 * 
 * @param lobes Number of "petals" (4 = clover, 6 = flower, 8 = star-like)
 * @param innerRadiusRatio How deep the indents go (0.8-0.95 recommended)
 * @param smoothness How smooth the curves are (1 = very smooth)
 * @param rotationDegrees Rotation of the shape
 */
class ScallopShape(
    private val lobes: Int = 8,
    private val innerRadiusRatio: Float = 0.88f,
    private val smoothness: Float = 1f,
    private val rotationDegrees: Float = 0f
) : Shape {

    private var cachedPath: Path? = null
    private var cachedSize: Size? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (cachedPath == null || cachedSize != size) {
            cachedSize = size
            cachedPath = createScallopPath(size)
        }
        return Outline.Generic(cachedPath!!)
    }

    private fun createScallopPath(size: Size): Path {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val baseInnerRadius = outerRadius * innerRadiusRatio
        val points = buildAlternatingPoints(
            lobes = lobes,
            center = center,
            outerR = outerRadius,
            baseInnerR = baseInnerRadius,
            rotationRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        )
        return catmullRomClosedPath(points, smoothness)
    }

    private fun buildAlternatingPoints(
        lobes: Int,
        center: Offset,
        outerR: Float,
        baseInnerR: Float,
        rotationRad: Float
    ): List<Offset> {
        val pts = mutableListOf<Offset>()
        val step = 2.0 * PI / lobes
        for (i in 0 until lobes) {
            val baseAngle = i * step + rotationRad
            // Outer point
            val aOuter = baseAngle
            val outerX = center.x + (outerR * cos(aOuter)).toFloat()
            val outerY = center.y + (outerR * sin(aOuter)).toFloat()
            pts.add(Offset(outerX, outerY))
            // Inner point
            val aInner = baseAngle + step / 2.0
            val innerX = center.x + (baseInnerR * cos(aInner)).toFloat()
            val innerY = center.y + (baseInnerR * sin(aInner)).toFloat()
            pts.add(Offset(innerX, innerY))
        }
        return pts
    }

    private fun catmullRomClosedPath(points: List<Offset>, smoothness: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        val n = points.size
        path.moveTo(points[0].x, points[0].y)
        val tension = 1f - smoothness
        val factor = (1f - tension) / 6f
        for (i in 0 until n) {
            val im1 = (i - 1 + n) % n
            val ip1 = (i + 1) % n
            val ip2 = (i + 2) % n
            val p0 = points[im1]
            val p1 = points[i]
            val p2 = points[ip1]
            val p3 = points[ip2]
            val c1 = Offset(
                x = p1.x + (p2.x - p0.x) * factor,
                y = p1.y + (p2.y - p0.y) * factor
            )
            val c2 = Offset(
                x = p2.x - (p3.x - p1.x) * factor,
                y = p2.y - (p3.y - p1.y) * factor
            )
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        path.close()
        return path
    }
}
