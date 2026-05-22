package com.mustakim.bokbok.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BokBokTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    useSystemFont: Boolean = false,
    seedPalette: ThemeSeedPalette? = null,
    content: @Composable () -> Unit
) {
    // The new expressive theme handles everything including MaterialExpressiveTheme,
    // dynamic coloring, and animated transitions.
    ExpressiveTheme(
        darkTheme = darkTheme,
        pureBlack = pureBlack,
        themeColor = themeColor,
        useSystemFont = useSystemFont,
        seedPalette = seedPalette,
        content = content
    )
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}
