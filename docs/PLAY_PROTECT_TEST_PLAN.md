# Lite / Full Play Protect device qualification

**STATUS: NOT RUN.** Build success and a registered APK signing certificate do not prove that a downloaded app will be accepted by Google Play Protect.

## Prerequisites

- A certified physical Android phone with Google Play services, Play Protect scanning **enabled**, and a reliable alternate Home app.
- Final **signed** Full and Lite APKs from one commit, with the same versionCode, package name and registered certificate.
- Run `bash tools/verify-release-apks.sh <full.apk> <lite.apk>` before device installation. Record its complete output and the individual APK SHA-256 file hashes.
- Obtain consent before erasing any installation or its launcher preferences. Back up settings. Older published alpha builds may have a different signing certificate, in which case an in-place APK update is not possible.

## Signed APK verification evidence (local Mac, supplied 2026-11-01)

- Script: `bash tools/verify-release-apks.sh` at PR #76 commit `682307f2378faef7a9d3848c7c67c33d789fd68c`
- Regression tests: **15 passed**
- Full APK `app/build/outputs/apk/release/Tenseven-Launcher-release.apk`: valid **v2** signature, registered certificate SHA-256 confirmed, compiled manifest and package/minSdk/versionCode checks PASS. File SHA-256: `7dd0420d899af83839616d5d002b2a178ace94c34bccaab5dc7c5508a1b73ef9`
- Lite APK `app/build/outputs/apk/lite/Tenseven-Launcher-lite.apk`: valid **v2** signature, same registered certificate, compiled manifest and package/minSdk/versionCode checks PASS. File SHA-256: `a15a2e439ee1207b9cce90989e422d010f0750e811444728b3783e92ba652f0b`
- Both report `versionCode=1`, `minSdk=30`, and certificate SHA-256 `9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292`.

This is user-supplied local validation output. The signed APK binaries have **not** been uploaded to CI and this is **not** physical-device or Play Protect evidence.

## Matrix (every result starts NOT RUN)

| ID | Install source | Variant | What to record |
|---|---|---|---|
| A | Browser / file-manager download | Lite | Install result with Play Protect enabled, warning text or screen, device/build |
| B | Same download source | Full | Exact sensitive-access warning or success, without turning protections off |
| C | ADB (distinct source) | Lite | Install and launcher / keyboard / Hub fallback behavior |
| D | ADB (distinct source) | Full | Install and opt-in services, Hub and double-tap lock |
| E | Installed Lite → Full | Same signed package, same versionCode | `adb install -r` result, preferences, Home role and feature migration |
| F | Installed Full → Lite | Same signed package, same versionCode | `adb install -r` result, preferences and removal of dead service controls |

**ADB is not a replacement for internet-sideload testing.** It may have different install-source behavior, so its success does not establish that browser/file-manager downloads will pass.

## Procedure

1. Capture device identity and OS details: `adb devices -l`, `adb shell getprop ro.build.version.release`, `adb shell getprop ro.build.version.sdk`, and `adb shell getprop ro.build.version.security_patch`. Record device model, country, installer and Play Protect version separately.
2. Confirm Play Protect is **enabled** in the Play Store UI. Do not disable Play Protect or Advanced Protection for the test.
3. Install Lite from the selected download source. Record allowed/blocked/scan prompt and exact text, then check launch, BB7/BB10 switching, keyboard search, app drawer and Hub fallback.
4. Check that Lite exposes no Accessibility service, notification-listener grant step, double-tap lock control, automatic lock layer, or lock-layer overlay permission.
5. Install Full through the corresponding source; record the warning or success. If installed, grant optional permissions only with tester consent, then test Hub conversations, Now Playing, double-tap locking and lock-layer behavior.
6. Back up settings, then perform both same-package update directions using `adb install -r <apk>`. Confirm Home selection, preferences, notification state, service declarations and no crashes.
7. Verify the **real locked screen** never displays message content, even when the Home Hub peek setting allows message text. A settings preview may display explicitly opted-in text.
8. Retain only redacted warning screenshots and narrowly scoped error logs. Never publish real notifications, contacts, credentials or complete unredacted logcat output.

## Acceptance conditions

- **Build:** Android CI passes Full/Lite assembly, unit tests and compiled-manifest checks.
- **Signing:** Both final APKs pass `tools/verify-release-apks.sh`, including a valid v2 or v3 APK signature and the registered signing certificate fingerprint.
- **Lite install:** On target phones, installation through intended downloadable sources succeeds with Play Protect enabled; unsupported service features are not offered.
- **Full install:** Exact Play Protect outcome and permission explanations recorded honestly; do not promise installation where Android blocks it.
- **Update:** Either direction is declared supported only after device-side evidence, otherwise instruct users to back up and reinstall.
- **Privacy:** No message text on the real lock screen. Full's existing overlay/background-start behavior requires independent compatibility testing.

Do not claim Play Protect compatibility PASS or publish these variants as validated until physical-device results support it.
