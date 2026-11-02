# P1 Full-only device acceptance — Hub 2.0, Peek and BBOS 7/10

Canonical scope: [issue #95](https://github.com/aimindseye/tenseven-launcher/issues/95).
Source: draft [PR #96](https://github.com/aimindseye/tenseven-launcher/pull/96), stacked on #94.
This replaces the older paired Lite/Full acceptance plan. Historical installation evidence
is retained in [PLAY_PROTECT_TEST_PLAN.md](PLAY_PROTECT_TEST_PLAN.md).

## Local Mac signed test build

The only release build is Full. Keep the previously registered release keystore on the Mac,
never in CI or GitHub. On a clean checked-out P1 branch:

```bash
git fetch origin
git switch fix/p1-onboarding-icon-hub-20261102
git status --short
bash tools/build-full-consolidated-test.sh
```

This builds and tests, runs release lint and Full-only signature verification and writes:
`~/Downloads/tenseven-full-combined-test/Tenseven-Full-Hub2-<sha>.apk`
with `.sha256`. Expected application ID `io.github.aimindseye.tenseven`,
versionCode `4`, versionName `0.1.2-p1.1-test.1` for this P1.1 branch.
Registered certificate SHA-256:
`9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292`.

## P1.1 focus: permissions and Hub navigation (PR #97)

1. Install the **Mac-signed** Full APK and verify Android App Info reflects **0.1.2-p1.1-test.1** (versionCode 4). Confirm existing wallpaper/home preferences survive an update from versionCode 3.
2. On the Q25 where the APK was sideloaded, with Notification and Usage access not yet granted, reopen TenSeven **Setup and permissions** (overview). Verify the visible **Before Notification or Usage access** guidance directs you to Settings → Apps → Tenseven → three-dot menu → Allow restricted settings **when available**.
3. Tap **App info** from the guidance. Manually approve restricted settings, return to TenSeven, enable Notification and Usage access, then confirm the Setup overview updates. TenSeven must not claim to grant these switches itself.
4. With Notification access still absent, also open **Hub Settings**; verify the instruction is visible there and App Info can be opened. Test on an Android variant without this menu: the app must not trap the user.
5. In Hub: confirm icon-bearing navigation for Hub, Unread, Messages, Email, Calls, Contacts, Snoozed, Search; source-app icons; hardware keyboard focus, Enter activation, Back, horizontal scrolling on 720×720.
6. For a conversation with supported actions, confirm compact Open / Reply / Mark read / Snooze glyph controls function, have accessibility labels and do not overflow. Verify lockscreen privacy, Peek, wallpaper, BB7/BB10 homes remain unchanged.
7. Record pass/fail with screenshots and source APK checksum. No Sonar outcome is inferred from this stacked PR; include Sonar in the eventual integration PR targeting main.

## P1 physical-device matrix (record PASS/FAIL per row)

| Test | Steps | Expected |
|---|---|---|
| Upgrade | Install signed Full over previous version 2 | Version 3, preferences and Home role preserved |
| Play Protect | Download signed Full via intended installer | Record full scan/install result; do not disable protection |
| Restricted settings | Follow first-run guidance to App Info, grant access manually if available | Setup detects returned permissions; unsupported OEM path handled |
| App consent | Start empty, enable two message apps and two mail clients | No notification data before opt-in; distinct real app icons after |
| Hub 2.0 | Receive 0/1/10 notifications, group conversations, keyboard navigation | One compact list; no duplicate filter bars, actions remain supported |
| Hub Peek | Open within Home using left-edge gesture, Enter/Back | Flat translucent borderless rows; actual app icons |
| Calendar | Grant permission, select multiple Google/Outlook/on-device calendars | Only selected source events shown; duplicate reminders avoided where possible |
| Home counts | Open BB7 and BB10, receive/dismiss notifications | Horizontal count strip uses opted-in packages and same count source as Hub/lock |
| Dial controls | Disable/enable all and each Wi-Fi/Mobile/CPU/RAM/Battery/Cell ring in each home style | Independent persisted settings; no blank row when all off |
| Theme isolation | Change from BB7 to BB10 then back | BB7-only tray settings hidden in BB10, independent display preferences restored |
| BB7 app tray/menu | Inspect open tray and Alt+Enter / long-press menu | Wallpaper visible through dark tray and menu, no white system dialog |
| Wallpaper | Change theme, restart, upgrade | Previously selected wallpaper persists |
| Lock Peek | Secure device, wake, Peek, expand, Enter/unlock and camera | No private text while locked; Android secure keyguard still controls unlock |
| Active Frames | Test launcher-owned and externally opened apps | Partial task API fallback remains working |
| Accessibility | Keyboard-only, large fonts, TalkBack and landscape | Labels readable and focused row/actions reachable |

Device qualification targets: Titan 2, Titan 2 Slim, Zinwa Q25, KEY2, and an ordinary
touchscreen Android phone. Passing CI does **not** establish a physical-device PASS.
The full multi-device matrix and further keyboard/widget work remain P3, not silently
closed by the P1 release.

## Release gate
1. GitHub Android, checks, Security, CodeQL and Sonar scans pass on the exact PR commit.
2. Signed Full passes the Mac release verifier; record file hash and signing identity.
3. User tests Hub, calendar permission, lock privacy, BB7 tray, wallpaper and both home styles.
4. Failures are repaired and retested; only then merge or issue a qualified APK.
