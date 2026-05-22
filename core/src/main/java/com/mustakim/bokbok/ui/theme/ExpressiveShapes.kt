package com.mustakim.bokbok.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Converts a RoundedPolygon to a Compose Shape for use with Modifier.clip(), etc.
 */
fun RoundedPolygon.toComposeShape(): Shape {
    val polygon = this
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = android.graphics.Path()
            
            // 1. Get the normalized path
            polygon.toPath(path)
            
            // 2. Measure its actual bounds
            val bounds = android.graphics.RectF()
            path.computeBounds(bounds, true)
            
            // 3. Create a matrix to scale the content to fill the Size
            val matrix = android.graphics.Matrix()
            matrix.setRectToRect(
                bounds,
                android.graphics.RectF(0f, 0f, size.width, size.height),
                android.graphics.Matrix.ScaleToFit.CENTER
            )
            
            // 4. Transform the path
            path.transform(matrix)
            
            return Outline.Generic(path.asComposePath())
        }
    }
}


/**
 * Creates a morphing shape that interpolates between two RoundedPolygons.
 * @param progress 0f = start shape, 1f = end shape
 */
fun Morph.toComposeShape(progress: Float): Shape {
    val morph = this
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = android.graphics.Path()
            
            // 1. Get the morphed path at current progress
            morph.toPath(progress, path)
            
            // 2. Measure actual bounds
            val bounds = android.graphics.RectF()
            path.computeBounds(bounds, true)
            
            // 3. Scale to fill destination while maintaining aspect ratio
            val matrix = android.graphics.Matrix()
            matrix.setRectToRect(
                bounds,
                android.graphics.RectF(0f, 0f, size.width, size.height),
                android.graphics.Matrix.ScaleToFit.CENTER
            )
            
            // 4. Transform
            path.transform(matrix)
            
            return Outline.Generic(path.asComposePath())
        }
    }
}

/**
 * Creates a MorphingShape between Puffy and Circle based on progress.
 * @param progress 0f = Puffy, 1f = Circle
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun getMorphingShape(progress: Float): Shape {
    val morph = Morph(MaterialShapes.Puffy, MaterialShapes.Circle)
    return morph.toComposeShape(progress)
}

/**
 * Puffy shape from Material 3 Expressive library.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PuffyShape: Shape = MaterialShapes.Puffy.toComposeShape()

/**
 * Circle shape from Material 3 Expressive library.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val ExpressiveCircleShape: Shape = MaterialShapes.Circle.toComposeShape()
