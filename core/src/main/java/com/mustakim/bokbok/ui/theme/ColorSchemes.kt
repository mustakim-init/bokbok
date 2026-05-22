package com.mustakim.bokbok.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    GRUVBOX,
    MONOCHROME,
    NORD,
    TOKYO_NIGHT,
    DRACULA,
    SOLARIZED,
    ROSE_PINE,
    ONE_DARK,
    MATERIAL_CLASSIC
}

fun getDarkColorScheme(theme: AppTheme): ColorScheme = when (theme) {

    AppTheme.GRUVBOX -> darkColorScheme(
        primary = GruvboxPrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF5C4419), // 30% lightness
        onPrimaryContainer = GruvboxPrimaryDark,
        secondary = GruvboxSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF4E5214), // 30% lightness
        onSecondaryContainer = GruvboxSecondaryDark,
        tertiary = GruvboxTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF6B1E18), // 30% lightness
        onTertiaryContainer = GruvboxTertiaryDark,
        background = GruvboxBackgroundDark,
        onBackground = Color.White,
        surface = GruvboxSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF504945),
        onSurfaceVariant = Color(0xFFEBDBB2),
        surfaceContainer = Color(0xFF32302F),
        surfaceContainerLow = Color(0xFF2D2B2A),
        surfaceContainerLowest = Color(0xFF282828),
        surfaceContainerHigh = Color(0xFF3D3B3A),
        surfaceContainerHighest = Color(0xFF484644),
        error = Color(0xFFCC241D),
        onError = Color.White,
        outline = Color(0xFF504945),
        outlineVariant = Color(0xFF3C3836)
    )

    AppTheme.MONOCHROME -> darkColorScheme(
        primary = MonochromePrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF4A4A4C), // 30% lightness
        onPrimaryContainer = MonochromePrimaryDark,
        secondary = MonochromeSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF48484A), // 30% lightness
        onSecondaryContainer = MonochromeSecondaryDark,
        tertiary = MonochromeTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF464648), // 30% lightness
        onTertiaryContainer = MonochromeTertiaryDark,
        background = MonochromeBackgroundDark,
        onBackground = Color.White,
        surface = MonochromeSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2D2D2D),
        onSurfaceVariant = Color(0xFFCCCCCC),
        surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF333333),
        error = Color(0xFFFF4444),
        onError = Color.White,
        outline = Color(0xFF333333),
        outlineVariant = Color(0xFF1A1A1A)
    )

    AppTheme.NORD -> darkColorScheme(
        primary = NordPrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF3A5A6E), // 30% lightness
        onPrimaryContainer = NordPrimaryDark,
        secondary = NordSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF3F576B), // 30% lightness
        onSecondaryContainer = NordSecondaryDark,
        tertiary = NordTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF2F425E), // 30% lightness
        onTertiaryContainer = NordTertiaryDark,
        background = NordBackgroundDark,
        onBackground = Color.White,
        surface = NordSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF434C5E),
        onSurfaceVariant = Color(0xFFECEFF4),
        surfaceContainer = Color(0xFF2E3440),
        surfaceContainerLow = Color(0xFF2A303C),
        surfaceContainerLowest = Color(0xFF262C38),
        surfaceContainerHigh = Color(0xFF3A4454),
        surfaceContainerHighest = Color(0xFF46525F),
        error = Color(0xFFBF616A),
        onError = Color.White,
        outline = Color(0xFF4C566A),
        outlineVariant = Color(0xFF3B4252)
    )

    AppTheme.TOKYO_NIGHT -> darkColorScheme(
        primary = TokyoNightPrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF2E4770), // 30% lightness
        onPrimaryContainer = TokyoNightPrimaryDark,
        secondary = TokyoNightSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF4F3A6B), // 30% lightness
        onSecondaryContainer = TokyoNightSecondaryDark,
        tertiary = TokyoNightTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF6B2F44), // 30% lightness
        onTertiaryContainer = TokyoNightTertiaryDark,
        background = TokyoNightBackgroundDark,
        onBackground = Color.White,
        surface = TokyoNightSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF292E42),
        onSurfaceVariant = Color(0xFFC0CAF5),
        surfaceContainer = Color(0xFF16161E),
        surfaceContainerLow = Color(0xFF141420),
        surfaceContainerLowest = Color(0xFF12121A),
        surfaceContainerHigh = Color(0xFF1F2336),
        surfaceContainerHighest = Color(0xFF292E42),
        error = Color(0xFFF7768E),
        onError = Color.White,
        outline = Color(0xFF414868),
        outlineVariant = Color(0xFF24283B)
    )

    AppTheme.DRACULA -> darkColorScheme(
        primary = DraculaPrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF513C7A), // 30% lightness
        onPrimaryContainer = DraculaPrimaryDark,
        secondary = DraculaSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF6E3961), // 30% lightness
        onSecondaryContainer = DraculaSecondaryDark,
        tertiary = DraculaTertiaryDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF26703F), // 30% lightness
        onTertiaryContainer = DraculaTertiaryDark,
        background = DraculaBackgroundDark,
        onBackground = Color.White,
        surface = DraculaSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF6272A4),
        onSurfaceVariant = Color(0xFFF8F8F2),
        surfaceContainer = Color(0xFF282A36),
        surfaceContainerLow = Color(0xFF242631),
        surfaceContainerLowest = Color(0xFF21222C),
        surfaceContainerHigh = Color(0xFF33354A),
        surfaceContainerHighest = Color(0xFF44475A),
        error = Color(0xFFFF5555),
        onError = Color.White,
        outline = Color(0xFF6272A4),
        outlineVariant = Color(0xFF44475A)
    )

    AppTheme.SOLARIZED -> darkColorScheme(
        primary = SolarizedPrimaryDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF0D4159), // 30% lightness
        onPrimaryContainer = SolarizedPrimaryDark,
        secondary = SolarizedSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF104A45), // 30% lightness
        onSecondaryContainer = SolarizedSecondaryDark,
        tertiary = SolarizedTertiaryDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF3B4700), // 30% lightness
        onTertiaryContainer = SolarizedTertiaryDark,
        background = SolarizedBackgroundDark,
        onBackground = Color.White,
        surface = SolarizedSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF073642),
        onSurfaceVariant = Color(0xFF93A1A1),
        surfaceContainer = Color(0xFF002B36),
        surfaceContainerLow = Color(0xFF002530),
        surfaceContainerLowest = Color(0xFF00212B),
        surfaceContainerHigh = Color(0xFF0A3845),
        surfaceContainerHighest = Color(0xFF134452),
        error = Color(0xFFDC322F),
        onError = Color.White,
        outline = Color(0xFF586E75),
        outlineVariant = Color(0xFF073642)
    )

    AppTheme.ROSE_PINE -> darkColorScheme(
        primary = RosePinePrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF624854), // 30% lightness
        onPrimaryContainer = RosePinePrimaryDark,
        secondary = RosePineSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF6B4F30), // 30% lightness
        onSecondaryContainer = RosePineSecondaryDark,
        tertiary = RosePineTertiaryDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF44606A), // 30% lightness
        onTertiaryContainer = RosePineTertiaryDark,
        background = RosePineBackgroundDark,
        onBackground = Color.White,
        surface = RosePineSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF26233A),
        onSurfaceVariant = Color(0xFFE0DEF4),
        surfaceContainer = Color(0xFF191724),
        surfaceContainerLow = Color(0xFF171520),
        surfaceContainerLowest = Color(0xFF15131C),
        surfaceContainerHigh = Color(0xFF21202E),
        surfaceContainerHighest = Color(0xFF2A2837),
        error = Color(0xFFEB6F92),
        onError = Color.Black,
        outline = Color(0xFF403D52),
        outlineVariant = Color(0xFF26233A)
    )

    AppTheme.ONE_DARK -> darkColorScheme(
        primary = OneDarkPrimaryDark,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF2A507A), // 30% lightness
        onPrimaryContainer = OneDarkPrimaryDark,
        secondary = OneDarkSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF5A3B73), // 30% lightness
        onSecondaryContainer = OneDarkSecondaryDark,
        tertiary = OneDarkTertiaryDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF3F613D), // 30% lightness
        onTertiaryContainer = OneDarkTertiaryDark,
        background = OneDarkBackgroundDark,
        onBackground = Color.White,
        surface = OneDarkSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C313A),
        onSurfaceVariant = Color(0xFFABB2BF),
        surfaceContainer = Color(0xFF21252B),
        surfaceContainerLow = Color(0xFF1E2127),
        surfaceContainerLowest = Color(0xFF1B1E23),
        surfaceContainerHigh = Color(0xFF2C313C),
        surfaceContainerHighest = Color(0xFF363C48),
        error = Color(0xFFE06C75),
        onError = Color.White,
        outline = Color(0xFF4B5263),
        outlineVariant = Color(0xFF2C313A)
    )

    AppTheme.MATERIAL_CLASSIC -> darkColorScheme(
        primary = MaterialPrimaryDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF2E0085), // 30% lightness - official Material spec
        onPrimaryContainer = Color(0xFFBB86FC),
        secondary = MaterialSecondaryDark,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF015E5D), // 30% lightness
        onSecondaryContainer = MaterialSecondaryDark,
        tertiary = MaterialTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF7A0019), // 30% lightness
        onTertiaryContainer = MaterialTertiaryDark,
        background = MaterialBackgroundDark,
        onBackground = Color.White,
        surface = MaterialSurfaceDark,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFE1E1E1),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainerLowest = Color(0xFF161616),
        surfaceContainerHigh = Color(0xFF292929),
        surfaceContainerHighest = Color(0xFF343434),
        error = Color(0xFFCF6679),
        onError = Color.Black,
        outline = Color(0xFF3C3C3C),
        outlineVariant = Color(0xFF2C2C2C)
    )
}

fun getLightColorScheme(theme: AppTheme): ColorScheme = when (theme) {

    AppTheme.GRUVBOX -> lightColorScheme(
        primary = GruvboxPrimaryDark,  // Gruvbox uses same accent in light
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFF3D6),
        onPrimaryContainer = Color(0xFF3C2A00),
        secondary = GruvboxSecondaryDark,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF2F5D0),
        onSecondaryContainer = Color(0xFF2A2D00),
        tertiary = GruvboxTertiaryDark,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE0DB),
        onTertiaryContainer = Color(0xFF5C0A04),
        background = Color(0xFFFBF1C7),
        onBackground = Color(0xFF3C3836),
        surface = Color(0xFFF9F5D7),
        onSurface = Color(0xFF3C3836),
        surfaceVariant = Color(0xFFEBDBB2),
        onSurfaceVariant = Color(0xFF504945),
        surfaceContainer = Color(0xFFF2E5BC),
        surfaceContainerHigh = Color(0xFFEBDBB2),
        surfaceContainerHighest = Color(0xFFD5C4A1),
        surfaceContainerLow = Color(0xFFF9F0CC),
        surfaceContainerLowest = Color(0xFFFBF1C7),
        error = Color(0xFFCC241D),
        onError = Color.White,
        outline = Color(0xFF928374),
        outlineVariant = Color(0xFFD5C4A1)
    )

    AppTheme.MONOCHROME -> lightColorScheme(
        primary = MonochromePrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF0F0F2), // 90% lightness with subtle warm tint
        onPrimaryContainer = Color(0xFF000000),
        secondary = MonochromeSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEEEF0), // 90% lightness with subtle cool tint
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = MonochromeTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECECEE), // 90% lightness with subtle neutral tint
        onTertiaryContainer = Color(0xFF333333),
        background = MonochromeBackgroundLight,
        onBackground = Color.Black,
        surface = MonochromeSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF4D4D4D),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFF5F5F5),
        surfaceContainerHighest = Color(0xFFE8E8E8),
        surfaceContainerLow = Color.White,
        surfaceContainerLowest = Color.White,
        error = Color(0xFFCC0000),
        onError = Color.White,
        outline = Color(0xFFAAAAAA),
        outlineVariant = Color(0xFFE0E0E0)
    )

    AppTheme.NORD -> lightColorScheme(
        primary = NordPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E9F0), // 90% lightness
        onPrimaryContainer = Color(0xFF2E3440),
        secondary = NordSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECEFF4), // 92% lightness
        onSecondaryContainer = Color(0xFF2E3440),
        tertiary = NordTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5E9F0), // 90% lightness
        onTertiaryContainer = Color(0xFF2E3440),
        background = NordBackgroundLight,
        onBackground = Color.Black,
        surface = NordSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFD8DEE9),
        onSurfaceVariant = Color(0xFF3B4252),
        surfaceContainer = Color(0xFFECEFF4),
        surfaceContainerHigh = Color(0xFFE5E9F0),
        surfaceContainerHighest = Color(0xFFD8DEE9),
        surfaceContainerLow = Color(0xFFF4F6F9),
        surfaceContainerLowest = Color.White,
        error = Color(0xFFBF616A),
        onError = Color.White,
        outline = Color(0xFF8FBCBB),
        outlineVariant = Color(0xFFD8DEE9)
    )

    AppTheme.TOKYO_NIGHT -> lightColorScheme(
        primary = TokyoNightPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E7FF), // 90% lightness
        onPrimaryContainer = Color(0xFF001947),
        secondary = TokyoNightSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E8FF), // 90% lightness
        onSecondaryContainer = Color(0xFF4B0082),
        tertiary = TokyoNightTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE5E5), // 90% lightness
        onTertiaryContainer = Color(0xFF8C0009),
        background = TokyoNightBackgroundLight,
        onBackground = Color.Black,
        surface = TokyoNightSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFBFC5D1),
        onSurfaceVariant = Color(0xFF3B4261),
        surfaceContainer = Color(0xFFD5D6DB),
        surfaceContainerHigh = Color(0xFFCBCCD1),
        surfaceContainerHighest = Color(0xFFBFC5D1),
        surfaceContainerLow = Color(0xFFE4E5E9),
        surfaceContainerLowest = Color.White,
        error = Color(0xFFE82424),
        onError = Color.White,
        outline = Color(0xFF8990A5),
        outlineVariant = Color(0xFFC3C9D8)
    )

    AppTheme.DRACULA -> lightColorScheme(
        primary = DraculaPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3EFFF), // 90% lightness
        onPrimaryContainer = Color(0xFF3D2E5A),
        secondary = DraculaSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFDEEFF), // 90% lightness
        onSecondaryContainer = Color(0xFF6B2573),
        tertiary = DraculaTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F9E9), // 90% lightness
        onTertiaryContainer = Color(0xFF0A5224),
        background = DraculaBackgroundLight,
        onBackground = Color.Black,
        surface = DraculaSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFD9D9D9),
        onSurfaceVariant = Color(0xFF4D4D4D),
        surfaceContainer = Color(0xFFF8F8F2),
        surfaceContainerHigh = Color(0xFFE6E6E6),
        surfaceContainerHighest = Color(0xFFD9D9D9),
        surfaceContainerLow = Color.White,
        surfaceContainerLowest = Color.White,
        error = Color(0xFFCC0000),
        onError = Color.White,
        outline = Color(0xFFAAAAAA),
        outlineVariant = Color(0xFFD9D9D9)
    )

    AppTheme.SOLARIZED -> lightColorScheme(
        primary = SolarizedPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6F0FF), // 90% lightness
        onPrimaryContainer = Color(0xFF001C3A),
        secondary = SolarizedSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6F8F5), // 90% lightness
        onSecondaryContainer = Color(0xFF003731),
        tertiary = SolarizedTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEEF5D6), // 90% lightness
        onTertiaryContainer = Color(0xFF3A4400),
        background = SolarizedBackgroundLight,
        onBackground = Color.Black,
        surface = SolarizedSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE3DCC8),
        onSurfaceVariant = Color(0xFF657B83),
        surfaceContainer = Color(0xFFFDF6E3),
        surfaceContainerHigh = Color(0xFFEEE8D5),
        surfaceContainerHighest = Color(0xFFE3DCC8),
        surfaceContainerLow = Color(0xFFFFFCF5),
        surfaceContainerLowest = Color.White,
        error = Color(0xFFDC322F),
        onError = Color.White,
        outline = Color(0xFF93A1A1),
        outlineVariant = Color(0xFFE3DCC8)
    )

    AppTheme.ROSE_PINE -> lightColorScheme(
        primary = RosePinePrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFEEF1), // 90% lightness
        onPrimaryContainer = Color(0xFF6E3B52),
        secondary = RosePineSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFF2E6), // 90% lightness
        onSecondaryContainer = Color(0xFF5F3D15),
        tertiary = RosePineTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6F3F5), // 90% lightness
        onTertiaryContainer = Color(0xFF2D4F56),
        background = RosePineBackgroundLight,
        onBackground = Color.Black,
        surface = RosePineSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFF2E9E1),
        onSurfaceVariant = Color(0xFF575279),
        surfaceContainer = Color(0xFFFAF4ED),
        surfaceContainerHigh = Color(0xFFFFFAF3),
        surfaceContainerHighest = Color(0xFFF2E9E1),
        surfaceContainerLow = Color(0xFFFDFBF9),
        surfaceContainerLowest = Color.White,
        error = Color(0xFFB4637A),
        onError = Color.White,
        outline = Color(0xFF9893A5),
        outlineVariant = Color(0xFFF2E9E1)
    )

    AppTheme.ONE_DARK -> lightColorScheme(
        primary = OneDarkPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6F0FF), // 90% lightness
        onPrimaryContainer = Color(0xFF002D6E),
        secondary = OneDarkSecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF8F0FF), // 90% lightness
        onSecondaryContainer = Color(0xFF5D0070),
        tertiary = OneDarkTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F9E9), // 90% lightness
        onTertiaryContainer = Color(0xFF1A5228),
        background = OneDarkBackgroundLight,
        onBackground = Color.Black,
        surface = OneDarkSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE3E3E3),
        onSurfaceVariant = Color(0xFF526074),
        surfaceContainer = Color(0xFFFAFAFA),
        surfaceContainerHigh = Color(0xFFF0F0F0),
        surfaceContainerHighest = Color(0xFFE3E3E3),
        surfaceContainerLow = Color.White,
        surfaceContainerLowest = Color.White,
        error = Color(0xFFBA2F3B),
        onError = Color.White,
        outline = Color(0xFFA0A7B8),
        outlineVariant = Color(0xFFE3E3E3)
    )

    AppTheme.MATERIAL_CLASSIC -> lightColorScheme(
        primary = MaterialPrimaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF2E7FF), // 90% lightness
        onPrimaryContainer = Color(0xFF21005D),
        secondary = MaterialSecondaryLight,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFFD5F7F2), // 90% lightness
        onSecondaryContainer = Color(0xFF002020),
        tertiary = MaterialTertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFEDEA), // 90% lightness
        onTertiaryContainer = Color(0xFF93000A),
        background = MaterialBackgroundLight,
        onBackground = Color.Black,
        surface = MaterialSurfaceLight,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainer = Color(0xFFFFFBFE),
        surfaceContainerHigh = Color(0xFFF5F5F5),
        surfaceContainerHighest = Color(0xFFECE6F0),
        surfaceContainerLow = Color.White,
        surfaceContainerLowest = Color.White,
        error = Color(0xFFB3261E),
        onError = Color.White,
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0)
    )
}

