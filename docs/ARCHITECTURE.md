# Architecture

## Modules

```text
:app      application, io.github.aimindseye.tenseven (Compose UI, activities, repositories, generated Tabler glyphs)
:design   library, io.github.aimindseye.tenseven.design (theme, tokens, shared components, string tables), used by :app
```

`:app` contains the Sable Start source tree, the activities included, plus Tenseven's own code (BlackBerry 7 home,
Launcher Settings, default-app lookup, sound control, extra apps). SableOS builds the Sable files as
`SableLauncher` with its Android.bp (system SDK); Tenseven builds them with Gradle against the public SDK, so
anything that needs a system API is isolated behind a small helper (below).

### Source layout inside `:app`

Directory `app/src/main/java/io/github/aimindseye/tenseven` (packages renamed from upstream `org.sableos.start`):

| Directory | Package | What |
|---|---|---|
| `model/` | `io.github.aimindseye.tenseven.model` | Pure Kotlin policy, unit tested: home styles and layouts, app glyphs, quick bar, letter shortcuts, speed dial, command queries, Hub bar, text tables |
| `privacy/` | `io.github.aimindseye.tenseven.privacy` | The All Apps privacy row: the evaluator, summary policy and snapshot cache (pure Kotlin) |
| `platform/` | `io.github.aimindseye.tenseven.platform` | Android access: `LauncherAppsRepository`, `RecentTasksRepository`, `StartStateRepository`, `LiveSurfaceRepository`, `PeopleRepository`, `UsageRecentsRepository` (recent apps from usage events), `PrivacyFactsReader`, `DeviceStatsSampler`, and Tenseven's `PermissionChangeSignal` and `UserContexts` |
| `onboarding/` | `io.github.aimindseye.tenseven.onboarding` | Setup: `OnboardingPlan` (step order and when to explain Allow restricted settings, pure and tested) and `SetupStatus` (what is granted); the screen is `OnboardingActivity` |
| `iconpacks/` | `io.github.aimindseye.tenseven.iconpacks` | Nova and ADW icon packs: discovery, `appfilter` parsing, drawable loading; `IconPacks` holds the chosen pack (Appearance) and `loadLauncherIcon` uses it; mapping rules are pure and tested; ported from Mink |
| `conversations/` | `io.github.aimindseye.tenseven.conversations` | Notification access: `ConversationListenerService` reads message and email notifications into `ConversationHub` (in memory only); feeds the Hub bar counts; ported from Mink, see `third_party/minklauncher/PORTING.md` |
| `ui/` | `io.github.aimindseye.tenseven.ui` | Compose: `SableStartScreen` (BlackBerry 10 home, Hub, Command, All Apps), `Bb7HomeScreen` (BlackBerry 7 home, widget strip, tray, menu), `SoundProfileButton`, `LineIcon` (Tabler-style line icons drawn from path data), glyphs and status rings |
| `live/` | `io.github.aimindseye.tenseven.live` | Models for the live tiles (calendar, mail, media) |
| `extras/` | `io.github.aimindseye.tenseven.extras` | Optional apps ported from SableOS: Calc Plus, Notes, Sound Profiles, ten games. Their manifest entries are disabled with their own task affinity; `ExtraAppsControl` switches them on. Each has its own palette and strings so it does not touch the launcher's |
| (root) | `io.github.aimindseye.tenseven` | `SableStartActivity` (HOME, both layouts, the BlackBerry 10 detour), `HomeStyleActivity`, `LauncherSettingsActivity`, `BackupActivity`, `OnboardingActivity` |

Tenseven-only files worth knowing: `model/Bb7Home` (panels, grid maths), `model/LauncherSettings` (icon sizes, which widgets can be switched), `model/HomeLayout` (`GlanceWidgets`), `model/RingerProfile` (sound-mode cycle), `platform/DefaultApps` (finds the phone's own dialer, messages, mail, camera, calendar, browser, gallery and music apps), `platform/SoundProfileControl`, `platform/NextAlarm`.

### Two home layouts, one activity

`SableStartActivity` hosts both layouts in a pager (Home, Hub). The BlackBerry 7 layout is chosen in Launcher Settings. Command and Hub are BlackBerry 10 surfaces, so from BlackBerry 7 they open as a **detour**: `startBb10Detour(returnOnHome)` shows the BlackBerry 10 view, and `endBb10Detour()` returns once the user is back on Home, when the app goes to the background (`onPause`) or when Back is pressed on the Hub (`onBackToHomePage`). That ordering is what prevents a flash of the BlackBerry 10 screen when switching. The window is translucent so the BlackBerry 7 banner and tray sit over the wallpaper.

### Default apps, not Sable apps

Starting icons, BlackBerry 10 tiles, Hub actions and the Mail, Calendar and Music glance cards resolve through `DefaultApps` (category intents, the default SMS and dialer roles, an image-capture intent) and need `<queries>` entries for package visibility. Nothing requires a Sable app. There is no weather tile: Tenseven makes no network requests, so the only route to live weather is hosting an Android widget (not built yet, see `ROADMAP.md`).

### Settings and storage

Preferences live in `StartStateRepository` (SharedPreferences). `LauncherBackup.ALLOWED` lists the keys a backup may hold; `LauncherBackupTest` fails if the two sets differ, so adding a preference means adding it to both. Booleans are stored as numbers 0/1.

### Sound

`SoundProfileControl` reads and sets `AudioManager.ringerMode`. Silent and vibrate-only need Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`); changing tones, vibration and light in Sound Profiles needs `WRITE_SETTINGS`. Without access the button skips Silent rather than failing. The Sound Profiles extra opens from a tap on the speaker when it is switched on; a long press cycles quickly.

Rule that makes this testable: decisions live in `model/` and `privacy/` with unit tests; `platform/` and
`ui/` only gather facts and draw. Keep it that way for anything added.

## Features that need the system, and what an ordinary install does

SableOS gives Sable Start signature permissions because the launcher is platform-signed. An ordinary install
cannot hold them, so each such feature has a fallback, and the Mink port named in the last column is what
replaces the missing data.

| Feature | On SableOS | Ordinary install today | Plan |
|---|---|---|---|
| Active Frames: list and live pictures of recent apps | `ActivityManager` recents plus hidden `IActivityTaskManager` calls (`READ_FRAME_BUFFER`, `REAL_GET_TASKS`) | Recent apps from usage stats, icons only (needs usage access) | Done |
| Close a frame (X / Backspace) | `removeTask` | Hides it from the list | Same |
| Recents key opens Active Frames | The system's Recents is redirected to `SHOW_ACTIVE_FRAMES` | Android keeps its own Recents; SableOS's key notes report the system opens its Overview even when an accessibility service consumes the key | A home key or letter opens Active Frames |
| Runtime-permission changes refresh the privacy rows | `OnPermissionsChangedListener` (system API) | `PermissionChangeSignal.register()` returns false; rows refresh on package events only | Refresh when returning to Home |
| Privacy row for work and private profile apps | `createContextAsUser` for that profile | `UserContexts.forUser` returns null; the row says "permissions · unavailable" | Accept; document |
| Lock and unlock Private Space | `MODIFY_QUIET_MODE` | Android decides whether a non-system launcher may do it; the call is already wrapped, so a refusal leaves the space as it was | Mink Private Space port; test on Android 15+ |
| Hub (messages, calls, contacts, notifications) | Sable Hub, Messages and Phone publish snapshot providers | Notification-based Hub (Mink conversations), in memory only | Done |
| Appearance (theme, accent) | Settings hosts `org.sableos.appearance` | Launcher's own preferences (Launcher Settings) | Done |
| Speed dial | Sable Phone provider and `org.sableos.permission.SPEED_DIAL` | Not available; letter shortcuts for apps still work | Launcher-owned letter-to-contact map |
| Live tiles: calendar, mail, media | Sable app providers | The phone's default apps; calendar entries via `READ_CALENDAR`, Now Playing via notification access | Done; weather removed (see above) |

The SableOS build of the same sources keeps the manifest and Android.bp in the SableOS repository; the
ordinary-install manifest is `app/src/main/AndroidManifest.xml`, and `tests/run.sh` fails if it ever asks for
a signature permission.

## Icons

`AppGlyph` entries name Tabler outline icons (`AppGlyphs.kt`). `tools/tabler_icons.py` copies the SVGs from the
pinned npm package into `third_party/tabler-icons/`, writes `TablerGlyphPaths.kt` (one path string per SVG
element; elements are never joined, because a relative moveto starts from the previous element's last point)
and the adaptive launcher icon. `SableGlyphIcon` strokes those paths (width 2 in a 24-unit box, round caps and
joins) under the Sable lighting: white shading to an accent-tinted silver, over a soft shadow. An app that has
no glyph draws its own icon (its themed monochrome layer when it has one). Third-party app logos are covered in
[`BRAND_ASSETS.md`](BRAND_ASSETS.md).

## Key profiles

Sable Start reads keys by Android key code. The devices differ in what the trackpad sends (D-pad keys or a
pointer), where Sym, Menu and the convenience key are, and whether Alt-plus-letter gives a digit. A key profile
records those facts per device so the shortcuts can be trusted. Capture with `adb shell getevent -lt` or the
event viewer in Sable Keyboard, then add a row here:

| Device | Keyboard name (`InputDevice`) | Trackpad | Sym | Menu | Convenience key | Verified |
|---|---|---|---|---|---|---|
| Zinwa Q25 | `Q25_keyboard` | D-pad keys (kernel mode 1) or pointer (mode 0); SableOS wants D-pad | Right Alt | `KEYCODE_MENU` (kl 139) | scan code unknown | no |
| Unihertz Titan 2 | not captured | Touch-sensitive keyboard (not captured in detail) | not captured | No Menu key: Alt+Enter opens the menu | not captured | Alt+Space (Command) and Alt+Enter (menu) work in hand testing |
| BlackBerry KEY2 | not captured | touch-sensitive keyboard | not captured | not captured | programmable | no |

The Q25 row is from SableOS's `device-profile/KEYMAP.md` and `docs/KEYBOARD_AND_INPUT.md` (device tree and
kernel source, not measured on hardware).

## Sable Keyboard (companion app, separate)

Sable Keyboard is an input method (about 4,800 lines of Kotlin with unit tests, no permissions, works before first
unlock) with per-device Alt layers, AutoText, a candidate strip and optional clipboard history. It stays a
**separate APK** from the launcher:

- A keyboard sees everything you type. Keeping its permission list empty is its privacy promise; merging it into
  a launcher that holds contacts and notification access would break it.
- Updates and crashes of one must not restart the other, and people use one without the other.
- The two cooperate without depending on each other: on first run the launcher offers the system keyboard
  picker, and type-to-find on Home works with any keyboard.

To make it standalone: lower `minSdk` from 36 to 30 and find the calls that need 36; detect the device from the
keyboard it reports (`InputDevice`, model) instead of the `ro.sable.profile.id` property, with a manual choice
in settings; read the accent from its own preference when the SableOS appearance provider is absent; and give
it its own package and signature. Its layouts for Titan 2 and Q25 are marked unverified upstream.

## Backup, quick actions and the lock service

- `model/LauncherBackup` is the backup format: JSON with `format`, `version` and an allow-list of preference keys. A file with an unknown key, a wrong type or another version is refused whole, before anything changes. `BackupActivity` uses the system file picker, so no storage permission is requested. Restoring replaces the listed settings and restarts the launcher. Not included: setup state, letter shortcuts and speed dial (they hold contact names), permissions.
- `model/QuickActions` parses `-` (to-do), `/` (note) and `+` (event) in Command and recognises web addresses. To-dos and notes go to an app the user picks through the share sheet; events open the calendar's own new-event screen. Tenseven keeps nothing.
- `LockScreenAccessibilityService` is declared with no event types, no window content and no gestures. Double-tap detection sits on empty Home space only (tiles and rows consume their own taps). **Google Play:** an accessibility service that is not an accessibility tool must be declared and justified in Play Console; review that policy before any Play release. GitHub and F-Droid builds are not affected.

