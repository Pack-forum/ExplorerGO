# ExplorerGO

Lightweight, fast, and customizable open-source Android Launcher built natively with Kotlin (zero external UI libraries).

## Features
- **Ultra-Lightweight Engine:** Custom `Pager`, cached frosted-glass (`GlassDrawable`) blur without live GPU overhead, and lazy-loaded app pages.
- **Multi-Language Support (i18n):** Full English and Turkish support with automatic system detection and in-app language switching (`System > Language / Dil`).
- **Customizable Lock Screen:** Custom clock sizes/fonts, optional seconds, date formats, and media controls.
- **Side Panel & Widgets:** Quick navigation, music player controls, quick app shortcuts, and Android AppWidget hosting.
- **Bottom Taskbar:** Start menu popup with instant search, pinned apps, volume, and lock triggers.

## Build with GitHub Actions or Gradle
This repository includes a pre-configured GitHub Actions workflow (`.github/workflows/build.yml`) that automatically builds the release APK on every push.

To build locally:
```bash
cd ExplorerLauncher
gradle :app:assembleRelease
```

## License
Distributed under the MIT License. See `LICENSE` for more information.
