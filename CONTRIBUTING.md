## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### Ways to Contribute

- 🐛 **Report bugs** - Open an issue with detailed reproduction steps.
- 💡 **Suggest features** - Share your ideas for improvements.
- 📝 **Improve docs** - Help make documentation clearer.
- 🔧 **Submit PRs** - Fix bugs or implement features.

### Development Guidelines

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Understand the Modular Structure**
   Ensure your changes are placed in the correct module layer to preserve separation of concerns:
   *   **Shared UI Elements or Core Utilities**: If you are creating a new design token, setting up colors, introducing reusable mesh layouts, or writing global utility preferences, add them to the **`:core`** module.
   *   **Music / Media Stream Logic**: If you are working on the ExoPlayer/Media3 Binder, caching, metadata matching, or song tracking, implement them in the **`:feature:music`** module.
   *   **Integration Providers (Lyrics, Scrobbling, API)**:
       *   To add or refine a lyrics client, edit the corresponding client modules (e.g., **`:lrclib`**, **`:kugou`**, **`:betterlyrics`**, or **`:simpmusic`**).
       *   To contribute to the YouTube music engine parser, edit **`:innertube`**.
       *   To adjust scrobbling actions, modify **`:lastfm`**.
       *   For Discord activity status connections, customize **`:kizzy`**.
   *   **Global Navigation, Dependency Injection, App Bootup, or Voice Signaling**: Edit the main orchestrating module, **`:app`**.
4. **Follow Kotlin & Compose conventions**
   *   Use descriptive variable and class names (avoid excessive abbreviations).
   *   Follow the standard unidirectional data flow (UDF) patterns for StateFlows and ViewModels.
   *   Add concise docstrings (KDoc) for public APIs and utility methods.
5. **Format & Test your changes**
   *   Use the default Android Studio Kotlin Formatter (`Ctrl + Alt + L` / `Cmd + Option + L`).
   *   Test your modifications on a physical device or a high-performance emulator.
   *   Validate that voice room signaling and playback operations operate with minimal recompositions.
6. **Commit with clean messages**
   ```bash
   git commit -m "Add: Clear description of your premium feature"
   ```
7. **Push and open a Pull Request**
   ```bash
   git push origin feature/amazing-feature
   ```

### Code Style

- **Kotlin** - Follow the [Official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Compose** - Follow the [Official Jetpack Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md).
- **Formatting** - Always format your code before creating a PR to avoid build pipeline failures.

---
