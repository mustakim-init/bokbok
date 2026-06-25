# TopBar Frosted Glass & Gradient Transparency

## Problem

Main module screens had a solid, opaque TopBar floating over beautiful mesh gradient backgrounds, looking disconnected and visually heavy. Simply changing the alpha didn't look like real glass, and wrapping in a fixed-height Box broke dynamic height calculation.

## Solution

Implemented a premium, high-fidelity frosted glass TopBar with non-linear vertical gradient transparency and actual blur.

1. **Dynamic Measuring (`matchParentSize()`)**:
   We wrapped the TopBar in a parent `Box` with no height constraints. Underneath the TopAppBar, we placed background and separator sibling layers that use `Modifier.matchParentSize()`. This tells Compose to measure the parent `Box` based entirely on the `TopAppBar`'s dynamic size, and then stretch the background and borders to match perfectly.
   
2. **Actual Backdrop Blur (API 31+)**:
   We applied a `RenderEffect` Gaussian blur onto the background layer:
   ```kotlin
   graphicsLayer {
       if (android.os.Build.VERSION.SDK_INT >= 31) {
           renderEffect = android.graphics.RenderEffect.createBlurEffect(
               12f, 12f, android.graphics.Shader.TileMode.CLAMP
           ).asComposeRenderEffect()
       }
   }
   ```
   By keeping the blur on a separate sibling layer *behind* the TopAppBar, we blur the underlay/scrim while ensuring the title text, icons, and badges are **never** blurred and remain perfectly readable.

 3. **Non-linear Gradient Transparency (flipped)**:
    The gradient is inverted — **100% transparent at the bottom** and nearly opaque at the top:
    - `0.0f` (very top): `alpha = 0.96f` (nearly opaque)
    - `0.6f` (around middle): `alpha = 0.75f` (drastic jump to opaque)
    - `1.0f` (bottom): `alpha = 0.0f` (fully transparent)
    
 4. **Crisp Highlight/Shadow Lines**:
   We draw a 1dp top white edge (`Color.White.copy(alpha = 0.15f)`) and a 1dp bottom separator (`outlineVariant.copy(alpha = 0.18f)`) on a separate unblurred sibling layer so that they remain perfectly crisp and sharp.

## Files changed

- `ui/screens/common/TopBar.kt` — Rewrote the TopBar layout using parent Box + matchParentSize() layer siblings, adding RenderEffect blur, non-linear gradients, and unblurred highlight lines.
- `ui/screens/chats/ChatsScreen.kt` — Moved mesh gradient background to MainScaffold background layer.
- `.agents/design/components.md` — Documented the new standard.

## Gotchas

- **Do NOT wrap the TopAppBar in a `Box` with fixed height or hardcoded constraints.** Doing so breaks dynamic heights on `LargeTopAppBar` as it collapses on scroll. Use `Modifier.matchParentSize()` inside a standard wrapping `Box` so children match the parent's layout bounds.
- **Never apply `RenderEffect` directly to a TopAppBar.** It will blur all child text, icons, and avatars. Always apply the blur to a separate sibling background layer *behind* the TopAppBar.
