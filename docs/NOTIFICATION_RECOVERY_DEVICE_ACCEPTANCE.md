# P2-A notification recovery regression — Hub, Peek, TenSeven lock screen

> Historical acceptance record for the build named below. Current release behavior and setup:
> [1.0.0 user guide](USER_GUIDE.md); current delivery state: [P2/P3 status](P2_P3_STATUS_20261103.md).

## Confirmed Titan 2 observations (2026-11-02)

- Physical test device for this launcher workflow is **Unihertz Titan 2**, not Zinwa Q25.
- Notification access and Usage access were shown as **enabled** in Launcher Settings even when Hub/Peek/TenSeven lock-screen notifications were empty.
- Turning **Notification access off and back on** restored all three surfaces; this confirms a reconnection recovery path, not a proven reason for the original disconnect.
- On Titan 2, **Allow restricted settings** did **not** appear in App Info before the first request was blocked; the option became available after Android denied a notification or usage access attempt.
- Launcher access status must distinguish **permission granted** from **listener connected**. The new diagnostics cover this distinction, but device validation is still required.
- **Do not uninstall or clear app data** during regression tests. Q25 remains a separate future compatibility target, not the device that reproduced this bug.

## Context
Titan 2 report on P2-A foundation v6: **all notification content/counts disappeared simultaneously** from Hub, BB10 Peek and TenSeven's lock screen, after P1.2 v5 worked.

These three surfaces share the Android `ConversationListenerService`. A source diff from P1.2 to P2-A did **not** find a change to listener parsing, manifest declaration or enabled-app selection. The underlying device cause (Android revoked access, listener not connected, failed input parsing, or all sources disabled) is **unconfirmed without Titan 2 evidence**.

## Device recovery — no uninstall / no clear storage
1. Confirm original apps still have notifications visible in the **Android notification shade**.
2. First attempt to grant Notification/Usage access. If Android blocks the request, reopen Settings → Apps → TenSeven → ⋮ → Allow restricted settings, **if the menu appears after the denied request**. Before the first attempt this option may be absent on Titan 2.
3. Settings → Notifications → Device & app notifications → Notification access → TenSeven. Confirm enabled; if already on, turn off/on and return to TenSeven.
4. In TenSeven **Hub Settings**, inspect **Notification listener diagnostics**:
   - "Access off": restore Android listener access, not calendar permission.
   - "Access granted, listener disconnected": tap diagnostics to ask Android to reconnect.
   - "Connected": compare active device notifications, Hub entries, lock-screen counts, and enabled app sources. No message bodies are logged or persisted in diagnostics.
   - "No apps selected": explicitly opt in the desired sources; never silently enable all apps.
5. Post a fresh WhatsApp/Signal/Gmail notification, and observe Hub, BB10 Peek and TenSeven's lock screen. Reconnect should also read eligible already-active notifications where Android supplies them.

## Test matrix
- Upgrade Full APK v5 → v6 → v7 using the **same signing certificate** and package, without data wipe; preserve Hub app opt-in list, theme, wallpaper and both home layouts.
- With access on but listener disconnected after app upgrade/process death, launcher resume requests one rebind, throttled to at most once a minute while disconnected.
- With notification access off (or service missing), no rebind attempt and guidance remains in Hub Settings. New permissions must not be granted automatically.
- With access on/connected, verify existing eligible active notifications and subsequent posted/removed notifications update **Hub + Peek + lock-screen count** consistently. After clearing one, count decrements, but other apps remain.
- With malformed/unsupported notification, one bad record cannot crash listener or erase all other notifications. Secure/locked content remains redacted.
- UI restores from foreground/background, phone reboot, full process death and changes to enabled sources.
- No private notification text in logs, test artifacts or remote telemetry. Hub diagnostic only shows counts/connection states.
- Confirm no regression to BBOS 7 home shortcuts, icon choices, wallpaper visibility, keyboard or Peek privacy.

## Build / acceptance
Branch `fix/p2a-notification-listener-recovery-20261102`, versionCode **7**, versionName `0.2.0-p2a-notification-fix.1`, one Full-only Mac-signed APK via `bash tools/build-full-consolidated-test.sh`.

This PR is **not** the completion of P2-A Tasks/Remind. Do not merge until Android/tests/CodeQL/security and Titan 2 tests pass. SonarCloud Quality Gate remains a separate integration blocker.

## Titan 2 onboarding sequence regression
1. Freshly sideloaded trusted Full APK, special access not granted. **Before** first request, inspect App Info: allow-restricted-settings overflow may be absent.
2. Attempt Notification access. If Android blocks it, return to TenSeven Setup; it should now show App Info recovery guidance. Open App Info, select **Allow restricted settings** if available, return and retry.
3. Repeat separately for Usage access. Both are optional, independent grants; no manifest or automation bypass is permitted.
4. With permission shown as granted but listener disconnected, Setup/Hub Settings must not claim notifications are working. Use Hub Settings diagnostics to distinguish disconnected from no enabled apps/no source notifications.
5. Open/reopen Setup multiple times while connected: avoid unnecessary rebinds, and ensure Hub, Peek and lock-screen counts do not vanish.
