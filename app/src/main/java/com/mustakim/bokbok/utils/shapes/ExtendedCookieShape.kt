package com.mustakim.bokbok.utils.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class ExtendedCookieShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val cornerRadius = minOf(width, height) * 0.15f

            // Create a rounded rectangle with slight variations for cookie effect
            moveTo(cornerRadius * 1.5f, 0f)
            lineTo(width - cornerRadius, 0f)

            // Top right corner
            quadraticTo(
                width, 0f,
                width, cornerRadius * 1.2f
            )

            lineTo(width, height - cornerRadius * 1.5f)

            // Bottom right corner
            quadraticTo(
                width, height,
                width - cornerRadius * 1.2f, height
            )

            lineTo(cornerRadius * 1.5f, height)

            // Bottom left corner
            quadraticTo(
                0f, height,
                0f, height - cornerRadius
            )

            lineTo(0f, cornerRadius * 1.5f)

            // Top left corner
            quadraticTo(
                0f, 0f,
                cornerRadius * 1.5f, 0f
            )

            close()
        }
        return Outline.Generic(path)
    }
}
