# Variable Fonts in This Project

This project uses **Google Sans Flex** variable font files (`.ttf`, ~1MB+ each, stored in `core/src/main/res/font/`).

## How to Apply Variable Font Axes (e.g., slant, weight)

Use `FontVariation.Settings` on the `Font` constructor. Requires `@OptIn(ExperimentalTextApi::class)`.

```kotlin
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexSlanted = FontFamily(
    Font(
        CoreR.font.google_sans_flex_600,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.slant(-10f) // negative = italic slant
        )
    )
)
```

## Key Axes for Google Sans Flex

| Axis | Tag | Range | Usage |
|------|-----|-------|-------|
| Weight | `wght` | 100-900 | `FontVariation.weight(700)` |
| Slant | `slnt` | -10 to 0 | `FontVariation.slant(-10f)` (negative = italic) |
| Roundness | `ROND` | 0-100 | `FontVariation.Setting("ROND", 0f)` (0 = sharp, 100 = round) |
| Grade | `GRAD` | varies | `FontVariation.grade(0)` |
| Width | `wdth` | varies | `FontVariation.width(100f)` |
| Optical Size | `opsz` | varies | `FontVariation.opticalSize(9)` |

## Important Notes

- `fontVariationSettings` is **NOT** a parameter on `TextStyle.copy()` in Compose UI 1.9.x. Do NOT use `.copy(fontVariationSettings = ...)`.
- The `Text` composable also does NOT have a direct `fontVariationSettings` parameter.
- Always use `FontVariation.Settings` on the `Font()` constructor instead.
- `FontVariation.Settings` takes vararg `FontVariation.Setting`:
  ```kotlin
  FontVariation.Settings(
      FontVariation.weight(700),
      FontVariation.slant(-10f),
      // custom axes:
      FontVariation.Settings.Setting("GRAD", 0f)
  )
  ```
