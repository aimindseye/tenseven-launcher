# Hub 2.0 and Lock Screen 2.0 — device acceptance

**Status: IMPLEMENTATION CANDIDATE, DEVICE VALIDATION NOT RUN.** This plan applies to
the stacked branch `feat/hub-calendar-peek-20261101` (PR #89 atop #88, #87, #86, and #76).
Do not publish a release or claim Play Protect certification from CI alone.

## Build and signing

On the developer's Mac, with the **existing registered release keystore** and protected
`keystore.properties`, after switching to the final approved branch:

```bash
./gradlew --no-daemon :app:verifyRegisteredReleaseKey :app:testDebugUnitTest \
  :app:assembleRelease :app:assembleLite :app:lintLite
python3 tests/test_release_verifier.py
bash tools/verify-release-apks.sh \
  app/build/outputs/apk/release/Tenseven-Launcher-release.apk \
  app/build/outputs/apk/lite/Tenseven-Launcher-lite.apk
shasum -a 256 app/build/outputs/apk/{release,lite}/*.apk
```

Do not paste key passwords or upload `*.jks` / `keystore.properties`. CI builds unsigned
candidates; only local signed output can qualify for Google Drive distribution testing.

## A. App opt-in safety — Full and Lite

- Fresh Full with Notification Access granted: Hub sources show **0 apps selected**; Hub
  notification-derived feed and lock-layer app badges are empty.
- Old Full install with historical `hub_excluded_apps` denylist: upgrade **does not**
  infer consent. Explicitly reselect approved apps.
- Enable WhatsApp and Signal only: only those sources are processed for Hub. Gmail,
  Outlook and newly installed apps stay OFF until selected.
- Disable Signal: Signal rows and lock badges disappear promptly. Notification shade
  continues to function independently.
- Import old backup with legacy key: sources remain OFF; new backup includes
  `hub_enabled_apps`.
- Lite: absent notification listener; no fake request to enable it or promise Hub messages.

## B0. Hub navigation identity regression (PR #93)

This is the regression that made the previous signed Hub2 APK *look unchanged*.
Test **before** granting notifications or selecting any apps:

- Hub must show a single horizontally scrollable nav row: **All / Unread / Messages /
  Email / Calls / Contacts**, plus only explicitly selected app sources; **no legacy
  `All / Messages / Calls / Contacts` row above a second filter row**.
- Full, with zero opted-in Hub sources: title remains Hub, new nav is visible,
  and the empty feed shows **No apps selected for Hub** with a settings action.
- Lite, with no notification listener: same new nav remains visible, and a clear
  Lite capability explanation appears instead of pretending conversation access works.
- Once WhatsApp and Signal are enabled, a notification from either gives a flat
  conversation row; selecting the app source filters the stream without opening
  the external app. **Email** filters notification-derived mail from any opted-in
  mail client; calls and contacts still navigate appropriately.
- On a physical keyboard, initial focus starts on **All**; moving to hidden
  source chips scrolls horizontally. No second, redundant filter bar remains.
- Compare the SHA-256 of the *on-device installed* base APK with the newly
  verified signed release/lite artifact using `adb shell pm path` then `adb pull`.
  A local build hash alone does **not** establish what was installed.
- After the user's local dev test, verify the source branch and commit match
  the PR #93 head. Old PR #89 build hashes are **not** acceptance evidence for
  the new navigation fix.

## B. Hub 2.0 conversation feed

- At least two enabled mail apps (e.g. Gmail and Outlook), with notifications posted from
  each: both appear in unified chronological conversation feed, per-app source selection
  filters them. This is **notification-derived**, NOT complete mail-account syncing.
- Enable two messaging apps: compact rows, timestamp and one-line preview appear only
  while unlocked. Keyboard/touch selects without cramped permanent action buttons.
- Press Enter/tap row: contextual Open / Reply (only if RemoteInput exists) / Mark read
  and Snooze. A local notification dismissal must never be misrepresented as deleting
  email or chat content at the provider.
- Search and unread/app-source filters; 0/1/20 conversations; stable order and keyboard
  focus on ~720px square display. Check portrait/landscape.
- Browser/Drive sideload scan with Play Protect enabled. A successful install and
  a later restricted-settings grant are distinct checks.

## C. Lock Screen 2.0

- Without notifications: idle clock/date/wallpaper, secure camera and Enter unlock.
- With opted-in apps: badge/count list; swipe up (or DPAD_DOWN) reveals borderless Peek;
  next swipe expands; swipe down/Back collapses.
- While Android keyguard is locked: **no sender/message content or reply actions**.
  Only app labels and counts. Tapping an app requests Android authentication.
- With PIN / pattern lock enabled: no action opens before successful authentication;
  after cancellation, private content never appears.
- Screen off/on, orientation, camera action, preview mode and keyboard keys.
- Notification listener revoked: no stale content; source allowlist enforced.

## D. Optional calendar peek

- Default OFF even if the app has Android `READ_CALENDAR` previously granted.
- Opt into `Upcoming events in Hub`, expand to request Calendar permission only on
  an explicit user action.
- On Android with Google, Outlook/Exchange or local calendars **synchronized into the
  shared Android CalendarProvider**, show the next three events chronologically with
  calendar names. Test two different calendars, duplicates, past events and empty state.
- Some mail/calendar apps keep data in their own stores. Such apps **will not appear**
  unless they synchronize to Android CalendarProvider. No OAuth/API integration is
  included in this version.
- Disable calendar setting: do not query/display events. No event bodies persisted.

## E. Release & integration sequencing

- #76 signing/build baseline; #86 source opt-in; #87 Hub UI; #88 lock Peek; #89 calendar.
- Stacked PRs are intentionally drafts until CI, review and real-device tests pass.
- Reconcile branch bases after earlier PR merges, retest the exact final combined
  commit, and generate **new APK SHA-256 hashes** before distributing.

## F. Consolidated Full-only device test (branch test/full-consolidated-hub2-bb10-20261102)

- **One APK to distribute:** Full only. The build helper may compile Lite internally
  to run the paired signature/manifest verifier; only the signed Full artifact is
  copied to the Downloads test folder. This is not a decision to delete Lite from
  source control. The test build has `versionCode=2` so it is distinguishable
  from the previously installed `versionCode=1` signed APKs.
- **Active Frames (#82):** with Usage Access denied and then enabled, launch an app
  through Tenseven, then through Android externally. Both should appear in
  BB10 Active Frames, even with an incomplete nonempty Android recent-task list.
  Test close/resume real tasks and synthetic cards separately.
- **Wallpaper (#80):** choose non-theme wallpaper, close Launcher Settings,
  reopen and ensure the selected preset remains highlighted. Change theme and
  reopen; the explicit wallpaper choice should still be selected. Apply to Home,
  Lock and Both; confirm the independent BB10 Show Home Wallpaper switch.
- **Style-specific Settings (#77):** select BB10 Home, and verify BB7-only
  preview/row-count controls are hidden. Shared grid size, columns and labels
  remain useful, identified as BB10. Switch to BB7 and verify preview/rows
  appear without resetting settings. Switch back and check immediate UI update.
- **Hub 2.0 (#93):** the unified All/Unread/Messages/Email/Calls/Contacts strip
  must appear even with **zero** allowed sources; no legacy duplicate tab bar.
  Explicitly enable apps in Hub Settings; they must default OFF. Check multiple
  messaging/mail clients and actual notification-derived actions.
- **Lock 2.0 (#88):** enable Lock layer, test badges → borderless Peek →
  expanded source counts, keyboard/gesture collapse, secure unlock. While locked
  this version does **not** expose individual private message text or replies.
- **Calendar (#89):** calendar Peek defaults OFF; with separate user opt-in and
  Calendar permission, shows events from multiple Android-synced calendars.
- **Restricted settings:** Android 13+ may deny Notification Access, Usage Access
  or Accessibility following a downloaded APK install. **Do not bypass this**.
  If the user trusts the signed APK, open system Settings → Apps → Tenseven →
  menu → Allow restricted settings, then grant the desired normal settings
  individually. The existing Tenseven onboarding includes an App Info help link.
- **Play Protect:** install the exact signed Full APK through Google Drive
  while protection is enabled. Record scan/pass/block separately from later
  special-access consent, and compare the installed SHA-256 with this build.
