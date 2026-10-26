# Build the APK remotely (the ZIP is source, not an installable module)

This workspace does not contain Android SDK/Gradle and cannot reach the dependency repositories, so it cannot produce an APK locally. The included GitHub Actions workflow performs the build on GitHub's hosted runner.

1. Create a new repository on GitHub (or use a repository where you can commit this project).
2. Upload the contents of this folder so `.github/workflows/build-apk.yml` is at the repository root.
3. Open the repository's **Actions** tab and run **Build iOS 26 Battery Renderer** (or wait for the first push build).
4. If the build passes, download the artifact named `ios26-battery-renderer-debug-apk`; it contains `app-debug.apk`.
5. Install that APK, enable the module in LSPosed for **System UI** and **Settings**, and reboot.

Important: a successful compile only proves the APK builds. The hook still needs on-device testing against this exact crDroid Android 15 build. Keep the existing SystemUI APK untouched; disable the LSPosed module if the icon or SystemUI misbehaves.
