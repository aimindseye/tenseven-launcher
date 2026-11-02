# P2-A notification recovery regression — Hub, Peek, TenSeven lock screen

## Context
Q25 report on P2-A foundation v6: **all notification content/counts disappeared simultaneously** from Hub, BB10 Peek and TenSeven's lock screen, after P1.2 v5 worked.

These three surfaces share the Android `ConversationListenerService`. A source diff from P1.2 to P2-A did **not** find a change to listener parsing, manifest declaration or enabled-app selection. The underlying device cause (Android revoked access, listener not connected, failed input parsing, or all sources disabled) is **unconfirmed without Q25 evidence**.

## Device recovery — no uninstall / no clear storage
1. Confirm original apps still have notifications visible in the **Android notification shade**.
2. Settings → Apps → TenSeven → ⋮ → Allow restricted settings (only when Android offers it).
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

This PR is **not** the completion of P2-A Tasks/Remind. Do not merge until Android/tests/CodeQL/security and Q25 tests pass. SonarCloud Quality Gate remains a separate integration blocker.
