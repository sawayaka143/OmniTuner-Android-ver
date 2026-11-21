---
name: ios-device
description: Build, sign, install, launch and screenshot the OmniTuner iOS app (`ios/`, SwiftUI + xtool) on the user's USB-connected iPhone from Windows via WSL. Use whenever working on the iOS version, checking whether it compiles, installing it on the phone, or looking at it on the device.
---

# OmniTuner iOS on the iPhone

The iOS app lives in `ios/`: an xtool SwiftPM project (`ios/xtool.yml`, bundle ID `com.omnituner.ios`, app target `OmniTunerApp`, shared logic in `ios/Packages/OmniTunerCore`). There is no Mac: everything builds in WSL Ubuntu with xtool and the Darwin Swift SDK, and talks to the phone through the Windows **Apple Devices** app's usbmuxd.

## Fixed setup (already done, don't reinstall)

- WSL Ubuntu with `networkingMode=mirrored` (`%USERPROFILE%\.wslconfig`), so WSL reaches Windows' usbmuxd on `127.0.0.1:27015`. `USBMUXD_SOCKET_ADDRESS=127.0.0.1:27015` is exported in `~/.profile` and `~/.bashrc`.
- `swift` 6.4.2 via swiftly (on `PATH` only in a **login** shell), `xtool` 1.24 (`/usr/local/bin/xtool`, signed in with a free personal team, `xtool auth status`), Darwin SDK (`xtool sdk status`).
- `pymobiledevice3` in a venv at `~/.pmd3/bin/pymobiledevice3` (screenshots, launch, app list), plus libimobiledevice (`idevice_id`, `ideviceinfo`).
- Device: iPhone 15 Pro Max on iOS 27, UDID `00008130-000E413A2689001C`, Developer Mode on.
- Installed bundle ID (xtool prefixes the team): `XTL-5226U347K7.com.omnituner.ios`.

## Running commands in WSL

Always go through a **login** shell, or `swift` is missing ("Failed to obtain Swift version") and usbmuxd isn't found ("No device found"):

```powershell
wsl -e bash -lc "cd /mnt/c/Projects/OmniTuner-Android-ver/ios && xtool devices"
```

For anything with quotes, pipes or `$`, write a `.sh` to the scratchpad and run `wsl -e bash -l /mnt/c/<scratchpad path>/x.sh` — inline quoting through PowerShell breaks easily.

## 1. Build only (compile check)

```powershell
wsl -e bash -lc "cd /mnt/c/Projects/OmniTuner-Android-ver/ios && xtool dev build"
```

Run it in the background; a clean build takes minutes. Build output goes to `ios/.build` and `ios/xtool/OmniTuner.app` (both git-ignored).

## 2. Build, sign, install and launch

```powershell
wsl -e bash -lc "cd /mnt/c/Projects/OmniTuner-Android-ver/ios && xtool dev run --udid 00008130-000E413A2689001C --usb"
```

- Free-team signing expires after ~7 days: if the app won't open on the phone ("no longer available" / untrusted), just run this again.
- If xtool says the session expired, the user has to run `xtool auth login` themself (it asks for their Apple ID password; never enter it).
- First install on a fresh device needs the user to trust the developer profile in Settings → General → VPN & Device Management.

## 3. Screenshot (always look after installing)

```bash
~/.pmd3/bin/pymobiledevice3 developer dvt screenshot /mnt/c/<scratchpad path>/phone.png
```

Then open the PNG with Read. Never save shots inside the repository. No separate tunnel (`remote tunneld`) is needed with the Apple Devices usbmuxd. If it fails with a DeveloperDiskImage / DVT error (e.g. after a phone restart), run `~/.pmd3/bin/pymobiledevice3 mounter auto-mount` and retry.

## 4. Relaunch without reinstalling

```bash
~/.pmd3/bin/pymobiledevice3 developer dvt launch --kill-existing XTL-5226U347K7.com.omnituner.ios
```

Wait ~3 s before screenshotting.

## Limits & gotchas

- Claude can't tap or swipe on the phone; navigating is up to the user. Ask them to open the screen you need, then screenshot.
- If `xtool devices` shows nothing: the phone must be unlocked and trusted, and the Apple Devices app installed on Windows (it provides usbmuxd). `usbipd` attaching is **not** used; leave the phone as "Shared"/unattached there.
- The phone is the user's personal device: only screenshot, launch, install/reinstall OmniTuner. Don't uninstall other apps or change device settings.
- AGENTS.md UI rules (Material 3, `SmoothShape`, …) are for the Android app; the iOS app is native SwiftUI.
