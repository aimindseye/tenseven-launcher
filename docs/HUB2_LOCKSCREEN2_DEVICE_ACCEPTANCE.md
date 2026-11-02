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
