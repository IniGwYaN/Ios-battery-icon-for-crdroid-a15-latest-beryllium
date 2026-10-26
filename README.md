# iOS 26 Battery Renderer for crDroid Android 15

This is an **LSPosed source project**, not a ready-to-install APK or Magisk ZIP.

It attempts to hook crDroid's `LandscapeBatteryDrawable` and `RLandscapeBatteryDrawable` in `com.android.systemui`, drawing a rounded horizontal capsule with a live battery fill and percentage. In `com.android.settings`, it attempts to rename the existing horizontal-left style entry to `iOS 26`. No persistent service is included.

## Build

- Android SDK platform 35
- JDK 17
- Gradle 8.9
- Dependency: Xposed API 82 from `https://api.xposed.info/`

The `.github/workflows/build-apk.yml` workflow builds the debug APK on GitHub Actions and uploads it as an artifact. See `BUILD_NOW.md`.

## Install after a successful build

1. Install `app-debug.apk`.
2. Enable the module in LSPosed and select **System UI** (`com.android.systemui`) and **Settings** (`com.android.settings`) as scope.
3. Reboot and select the renamed battery style option.

## Important limitations

This code has **not been compiled or tested on-device here**. The hook relies on the exact class names, fields, and methods present in the ROM. If those internals differ, the code logs an error and lets the original renderer run. Do not replace or re-sign the system APKs with this project.
