# Components

## TopAppBar / TopBar

### Rule

TopBars must use the shared `TopBar` composable in `ui/screens/common/TopBar.kt`. Never use raw Material `TopAppBar` in screen files.

### Frosted glass appearance

The TopBar achieves a premium, highly refined frosted glass look using a custom overlay technique:

1. **`containerColor = Color.Transparent`** — the Material TopAppBar paints no container, letting the custom background show through completely.
2. **`matchParentSize()`** — used inside a wrapping `Box` containing the TopAppBar to dynamically measure and match the TopAppBar's height and width. This removes any hardcoded box or height constraints, allowing the TopAppBar to dynamically layout its content and height as expected.
3. **`Modifier.graphicsLayer` with `RenderEffect` (API 31+)** — on Android 12+, we apply a real Gaussian blur to the background scrim layer (`android.graphics.RenderEffect.createBlurEffect`). Because the scrim is a sibling/underlay of the TopAppBar (and not a parent), the background is beautifully blurred while the TopAppBar's title, text, and icons remain perfectly crisp, sharp, and readable.
4. **Non-linear Transparency Gradient** — instead of a simple linear alpha gradient, we use a custom vertical gradient with highly-tuned stops:
   - `0.0f` (very top) to `0.35f` is mostly transparent (`alpha = 0.0f` to `0.15f`).
   - `0.5f` (middle) features a **drastic jump** to `alpha = 0.75f` (25% transparency), keeping it transparent longer at the top and quickly solidifying in the center.
   - `1.0f` (bottom) reaches `alpha = 0.96f` for solid separation.
5. **Crisp Inner Highlights/Separators** — top edge has a thin white highlight (`Color.White.copy(alpha = 0.15f)`) and the bottom edge has a clean separator (`outlineVariant.copy(alpha = 0.18f)`) drawn as unblurred vectors on a separate sibling layer, adding a realistic 3D physical glass thickness.

### Usage

```kotlin
// Screen uses MainScaffold — TopBar is rendered automatically
MainScaffold(
    navController = navController,
    title = "Screen Title",
    showBottomBar = true,
    isStatic = true,
    userViewModel = userViewModel,
    containerColor = Color.Transparent,  // required for transparency
    background = {
        // Mesh gradient fills the full screen (shows through TopBar)
        Box(Modifier.fillMaxSize().drawWithCache { ... })
    }
) { paddingValues -> ... }
```

### Screens using this pattern

- **Lounge**
- **Chats**
- **GameBoost**
- **AICompanion**
