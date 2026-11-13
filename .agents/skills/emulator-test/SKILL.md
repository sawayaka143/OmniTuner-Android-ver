---
name: emulator-test
description: Build, install, launch, inspect, and test OmniTuner on the Medium_Phone Android emulator via android CLI and adb. Use whenever verifying UI changes visually, driving flows on the emulator, checking layout trees or screenshots, or launching the app on Medium_Phone.
---

# Test OmniTuner on `Medium_Phone`

Follow this fast path instead of rediscovering paths, AVDs, or APK locations each session. For general `android layout`, `android screen`, and `adb shell input` syntax, use the `android-cli` skill.

## Fixed paths & targets

- `ADB`: `C:\Users\esken\AppData\Local\Android\Sdk\platform-tools\adb.exe` (not on default `PATH`)
- `EMULATOR`: `C:\Users\esken\AppData\Local\Android\Sdk\emulator\emulator.exe`
- AVD: `Medium_Phone` (never modify or launch `Samsung_Galaxy_A57`)
- App ID / launcher: `com.omnituner.android` / `com.omnituner.android.MainActivity`
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk` (built by `:app:assembleDebug`; ignore stale APKs under `app\build\intermediates\apk\`)
- Screenshots: write PNGs to the conversation scratch/temp directory (`<artifact-dir>\scratch\`), never inside the repository.

## 1. Reuse or start `Medium_Phone`

Check `adb.exe devices` first so you don't boot a second emulator or wait unnecessarily:

```powershell
C:\Users\esken\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

- If `emulator-5554` (or another `Medium_Phone` instance) shows `device`, reuse it immediately.
- If no emulator is running:
  - **Agent-only verification:** launch headless as a daemon (`IsDaemon: true`) because processes spawned by `run_command` have hidden windows on Windows and are killed when a non-daemon command exits:
    ```powershell
    C:\Users\esken\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Medium_Phone -no-window
    ```
    Then wait for boot (`adb.exe wait-for-device` and `adb.exe shell getprop sys.boot_completed` returning `1`).
  - **When the user wants to watch on screen:** ask them to start `Medium_Phone` in Android Studio (or mirror it in the **Android Device Hub** pane).

## 2. Build, delta-install, and launch

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug --console=plain -q
android run --device=emulator-5554 --apks=app\build\outputs\apk\debug\app-debug.apk
```

- `android run` uses fast delta install by default and launches the main activity.
- To relaunch without rebuilding/reinstalling:
  `C:\Users\esken\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell am start -n com.omnituner.android/.MainActivity`

## 3. Inspect & drive the UI

1. **Start with the layout tree** (fast JSON; waits for UI idle automatically):
   ```powershell
   android layout --device=emulator-5554 --pretty
   ```
2. **Capture screenshots** when checking visuals, Canvas views (tuner needle, chord diagrams, fretboard, wheels), or theme/layout alignment, and **always inspect the PNG via `read_file`**:
   ```powershell
   android screen capture --device=emulator-5554 --output <scratch-dir>\screen.png
   ```
   Add `--annotate` if an element isn't clear in `android layout`.
3. **Interact** via `adb.exe -s emulator-5554 shell input tap <x> <y>` (or `swipe`, `text`, `keyevent`), then re-read `android layout` or capture a new PNG.

## Gotchas

- Run `verify-build` (compile, unit tests, lint) before reporting code changes complete; emulator checks supplement static/unit verification.
- If the app freezes on the splash screen or installs fail with empty error codes, suspect a stale emulator snapshot rather than app code — relaunch with `-no-snapshot` (or Cold Boot the AVD in Android Studio).
