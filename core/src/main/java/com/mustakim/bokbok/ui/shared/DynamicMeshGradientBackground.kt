package com.mustakim.bokbok.ui.shared

import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

@Composable
fun rememberPaletteState(imageUrl: String?): State<Palette?> {
    val context = LocalContext.current
    val paletteState = remember { mutableStateOf<Palette?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            paletteState.value = null
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false) // Needed for Palette
            .size(100) // Small size for faster color extraction
            .build()

        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                Palette.from(bitmap).generate { palette ->
                    paletteState.value = palette
                }
            }
        }
    }
    return paletteState
}

@Composable
fun DynamicMeshGradientBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    coverage: Float = 0.7f
) {
    val palette by rememberPaletteState(imageUrl)

    // Fallbacks to default MaterialTheme colors
    val defaultColor1 = MaterialTheme.colorScheme.primary
    val defaultColor2 = MaterialTheme.colorScheme.secondary
    val defaultColor3 = MaterialTheme.colorScheme.tertiary
    val defaultColor4 = MaterialTheme.colorScheme.primaryContainer
    val defaultColor5 = MaterialTheme.colorScheme.secondaryContainer

    val color1 = palette?.vibrantSwatch?.rgb?.let { Color(it) } ?: defaultColor1
    val color2 = palette?.darkVibrantSwatch?.rgb?.let { Color(it) } ?: defaultColor2
    val color3 = palette?.lightVibrantSwatch?.rgb?.let { Color(it) } ?: defaultColor3
    val color4 = palette?.mutedSwatch?.rgb?.let { Color(it) } ?: defaultColor4
    val color5 = palette?.darkMutedSwatch?.rgb?.let { Color(it) } ?: defaultColor5

    MeshGradientBackground(
        modifier = modifier,
        color1 = color1,
        color2 = color2,
        color3 = color3,
        color4 = color4,
        color5 = color5,
        coverage = coverage
    )
}
