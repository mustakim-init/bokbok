package com.mustakim.bokbok.utils.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RoundedStarShape(
    private val sides: Int = 5,
    private val curve: Double = 0.1
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(centerX, centerY)
        val innerRadius = radius * 0.5f

        for (i in 0 until sides * 2) {
            val angle = (PI * i / sides).toFloat()
            val r = if (i % 2 == 0) radius else innerRadius
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}
