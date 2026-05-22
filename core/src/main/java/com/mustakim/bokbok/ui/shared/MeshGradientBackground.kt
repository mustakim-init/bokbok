package com.mustakim.bokbok.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex

@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    color1: Color = MaterialTheme.colorScheme.primary,
    color2: Color = MaterialTheme.colorScheme.secondary,
    color3: Color = MaterialTheme.colorScheme.tertiary,
    color4: Color = MaterialTheme.colorScheme.primaryContainer,
    color5: Color = MaterialTheme.colorScheme.secondaryContainer,
    coverage: Float = 0.7f // How much of the screen it covers from top down
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(coverage)
                .align(Alignment.TopCenter)
                .zIndex(-1f)
                .drawWithCache {
                    val width = this.size.width
                    val height = this.size.height

                    // First color blob - top left
                    val brush1 = Brush.radialGradient(
                        colors = listOf(
                            color1.copy(alpha = 0.38f),
                            color1.copy(alpha = 0.24f),
                            color1.copy(alpha = 0.14f),
                            color1.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.15f, height * 0.1f),
                        radius = width * 0.55f
                    )

                    // Second color blob - top right
                    val brush2 = Brush.radialGradient(
                        colors = listOf(
                            color2.copy(alpha = 0.34f),
                            color2.copy(alpha = 0.2f),
                            color2.copy(alpha = 0.11f),
                            color2.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.85f, height * 0.2f),
                        radius = width * 0.65f
                    )

                    // Third color blob - middle left
                    val brush3 = Brush.radialGradient(
                        colors = listOf(
                            color3.copy(alpha = 0.3f),
                            color3.copy(alpha = 0.17f),
                            color3.copy(alpha = 0.09f),
                            color3.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.3f, height * 0.45f),
                        radius = width * 0.6f
                    )

                    // Fourth color blob - middle right
                    val brush4 = Brush.radialGradient(
                        colors = listOf(
                            color4.copy(alpha = 0.32f),
                            color4.copy(alpha = 0.18f),
                            color4.copy(alpha = 0.1f),
                            color4.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.75f, height * 0.6f),
                        radius = width * 0.65f
                    )

                    // Fifth color blob - bottom center
                    val brush5 = Brush.radialGradient(
                        colors = listOf(
                            color5.copy(alpha = 0.28f),
                            color5.copy(alpha = 0.16f),
                            color5.copy(alpha = 0.08f),
                            color5.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(width * 0.5f, height * 0.85f),
                        radius = width * 0.7f
                    )

                    onDrawBehind {
                        drawRect(brush1)
                        drawRect(brush2)
                        drawRect(brush3)
                        drawRect(brush4)
                        drawRect(brush5)
                    }
                }
        )
    }
}
