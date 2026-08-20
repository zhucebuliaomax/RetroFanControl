# Default configuration

`RetroControl-data.json` is the single source of truth for a fresh install and for
**Reset all data**. It must be a complete version-1 data export produced by the app.

To change defaults:

1. Configure the app as desired and export all data.
2. Replace `RetroControl-data.json` with the exported file (keep the filename).
3. Build the app. Gradle validates the file and generates the Kotlin defaults.

Do not edit the generated `BundledDefaultConfig.kt` under `app/build/`.
