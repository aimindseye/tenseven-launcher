# TenSeven Launcher 2.0 — D2r2 design-parity qualification (Titan 2)

## Source of truth

Approved `TenSeven-BB10-Titan2-Q25-Design-Review (1)(1).pdf`, pages 3–10. This ZIP applies to the already-tested D1 + D1.1 + D2 **uncommitted** `review/tenseven-d1-setup-settings` worktree, with D0 merged in its history. It does not change platform SableOS or Active Frames code.

## Target UI

- **Page 3 — Hub:** BlackBerry black/white design, compact calendar summary ABOVE dynamic flat tabs, conversation rows. Only populated notification categories appear. `Views` holds navigation commands (Search, Calls, Calendar, Contacts), so missing/unread-empty categories do not appear as false alerts.
- **Page 4 — Hub Settings:** Preserve rightward-swipe Hub panel and direct Launcher Settings route. One back goes to Launcher Settings for that launch origin.
- **Page 5 — Selected message:** Existing right panel with unlocked selection preview and Android-advertised actions. Do not mark read on selection, invent reply actions, or bypass device unlock.
- **Pages 6–10 — Lock rail / Glance / Peek:** Per-app icon/count rail, blue focus bracket, selected-app-only notifications, redaction if locked or previews denied. Tabler icon when selected; installed app artwork when Original.
- **App icons:** On fresh install TenSeven/Tabler where a mapping or monochrome icon is available, including first-party Extras. Explicit Original opt-out restores each app's actual installed artwork; external packs remain separate. Unknown third-party apps without a Tabler mapping retain their own icon rather than being disguised as a different app.
- **Peek:** Compact, square-edged black overlay; outline rather than large filled-blue Open Hub button; obeys icon preference.
- **Settings:** Root cards retain thin neutral-grey outlines (D2). Settings subpages keep focus/selection blue, as approved. Icon style can be chosen without expanding an advanced theme list.
- **Home role:** Android launcher activity explicitly labelled `TenSeven Launcher`; package name does not change.
- **Bundled apps:** Notes, Tasks, Calculator and Games already reuse TenSeven/LauncherTheme in mainline. This correction changes launcher presentation/icon policy, not their game logic or app databases.

## Titanium 2 acceptance (do in this order)

1. Run `apply_d2_r2.py --dry-run` then apply with a backup folder outside the repo. Run `tools/tenseven-d0-frames-guard.py` and `git diff --check`.
2. Run `./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease`; verify the registered signer, install using `adb -s TITAN20000006073 install -r`. Do **not uninstall**: preserving user preferences is an important migration test.
3. Open Launcher Settings root: it must say `D2r2 test build`. If not, the previous APK remains installed. In Android Settings > Apps > Default apps > Home app, check the displayed label `TenSeven Launcher`. If still the package identifier, capture the chooser screen and `adb shell dumpsys package io.github.aimindseye.tenseven | grep -E -m 3 'versionCode|versionName|labelRes'` (do not dump personal data).
4. New-install baseline can be tested on a disposable profile/device if desired, not by erasing the configured Titan 2. With no saved style, Tabler is the default. Existing explicitly saved Original remains Original.
5. Settings > Appearance & themes: toggle `Use TenSeven (Tabler) icons` On/Off. Check All Apps (Notes, Sudoku, games), Hub rows, Peek and lock icon rail. Off must show each provider's default icon.
6. Hub (no notifications): show `All` and `Views`, no empty Messages/Email/Unread/Snoozed/Remind icon tabs. Add WhatsApp and a silent Gmail notification: appropriate tabs appear; silent Gmail is included. With messages cleared, categories disappear. Real Android notifications are the source, not chat history.
7. Hub with calendar permission: today calendar strip displays above the tabs. Without permission, permission/empty state remains appropriate.
8. Hub selected WhatsApp: selected-message preview appears to the right; Reply only if WhatsApp provides an actual reply action. Selecting alone does not mark read.
9. BB10 Home edge swipe: Peek opens in black flat list, source icons follow icon preference. Press Open Hub / Back and confirm focus return.
10. Lock cover: app rail + counts, tap WhatsApp then Gmail, selected detail switches, focus bracket moves; with phone locked or previews disallowed, content redacts rather than leaking text. Unlock delegates to Android authentication.
11. Settings > Keyboard & gestures > Double-tap to wake: opens Android Display settings. Device/kernel support is required. TenSeven can double-tap to **lock**, not independently process taps with the display completely off.
12. Regressions: BB7/BB10 Home, wallpaper, Hub notifications, Task/Remind, Notes/Sudoku, and protected Active Frames media/app summaries still work.

## Boundaries

- No root/Magisk, no privileged task-snapshot or always-on screen polling, no new permissions, no data migration, no media/Active Frames code edit.
- Full parity cannot be claimed until actual Titan 2 screenshots/short video match pages 3–10; successful source tests and Gradle builds are necessary but not sufficient.
- **Do not open/merge PR until the owner validates this package.** If a quality gate later requires Kotlin formatter corrections, fix on the review branch without changing behavior.
