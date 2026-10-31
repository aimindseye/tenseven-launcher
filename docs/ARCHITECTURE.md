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
| `ui/` | `io.github.aimindseye.tenseven.ui` | Compose: `SableStartScreen` (BlackBerry 10 home, Hub, Command, All Apps), `Bb7HomeScreen` (BlackBerry 7 home, tray, menu), `SoundProfileButton`, `LineIcon` (Tabler-style line icons drawn from path data), glyphs and status rings |
| `live/` | `io.github.aimindseye.tenseven.live` | Models for the live tiles (calendar, mail, media) |
| `extras/` | `io.github.aimindseye.tenseven.extras` | Optional apps ported from SableOS: Calc Plus, Notes, Sound Profiles, ten games. Their manifest entries are disabled with their own task affinity; `ExtraAppsControl` switches them on. Each has its own palette and strings so it does not touch the launcher's |
| (root) | `io.github.aimindseye.tenseven` | `SableStartActivity` (HOME, both layouts, the BlackBerry 10 detour), `HomeStyleActivity`, `LauncherSettingsActivity` and `SettingsSections` (the setting groups shared with Setup), `HubSettingsActivity`, `LockLayerActivity`, `BackupActivity`, `OnboardingActivity`, `LauncherTheme` |

Tenseven-only files worth knowing: `model/GridColumns`, `model/HubApps`, `model/LockLayer`, `model/ThemePresets` (themes and `WallpaperSets`), `model/WidgetList`, `platform/WidgetHostController`, `platform/WallpaperArt`, `platform/LockLayerLauncher` and `LockLayerState`, `platform/RecentLaunchesRepository`, `ui/WidgetsPage`, `ui/HubPeek`, `model/Bb7Home` (panels, grid maths), `model/LauncherSettings` (icon sizes, which widgets can be switched), `model/HomeLayout` (`GlanceWidgets`), `model/RingerProfile` (sound-mode cycle), `platform/DefaultApps` (finds the phone's own dialer, messages, mail, camera, calendar, browser, gallery and music apps), `platform/SoundProfileControl`, `platform/NextAlarm`.

### Two home layouts, one activity

`SableStartActivity` hosts both layouts in a pager (Widgets page, Hub, Home, app grid pages). The BlackBerry 7 layout is chosen in Launcher Settings. Command and Hub are BlackBerry 10 surfaces, so from BlackBerry 7 they open as a **detour**: `startBb10Detour(returnOnHome)` shows the BlackBerry 10 view, and `endBb10Detour()` returns once the user is back on Home, when the app goes to the background (`onPause`) or when Back is pressed on the Hub (`onBackToHomePage`). That ordering is what prevents a flash of the BlackBerry 10 screen when switching. The window is translucent so the BlackBerry 7 banner and tray sit over the wallpaper.

### Widgets page

`StartPages.WIDGETS` is the page left of Hub; when the page is on, every pager index is the page's own number plus one (`lead` in `SableStartScreen`). `WidgetHostController` wraps `AppWidgetHost`: allocate id, bind (Android's permission screen the first time), run the widget's setup screen, save the id (`StartStateRepository`, never backed up). The activity forwards `onActivityResult` and starts/stops listening in `onStart`/`onStop`. `ui/WidgetsPage` draws each widget in an `AndroidView` at the height it asks for (`WidgetSizing`); list policy is `model/WidgetList`. From the BlackBerry 7 home it is a detour like Hub (`widgetsGeneration`).

### Widgets: the page, and nothing on the home

One `WidgetHostController` (one `AppWidgetHost` id) serves the Widgets page (`WidgetTarget.PAGE`). The `HOME` target and `home_widget_ids` are kept for old saves but nothing draws them: the BlackBerry 10 home holds only recent-app Active Frames (empty when there are none). The built-in widgets (`GlanceWidgets`: Calendar, Alarm, Battery, Now playing; none on by default, switched in Launcher Settings) are drawn by `BuiltInWidgets` as the top of the Widgets page, passed into `WidgetsPage` as its `builtIn` slot. Edit mode dims an added widget and puts Up, Down, Remove in a row under it. Edit Home ends in a bar that stays on screen (`EditBar`: Reset, Cancel, Done).

### Settings groups shared with Setup

`SettingsSections.kt` holds one composable per Launcher Settings group (`HomeStyleSection`, `IconGridSection`, `ThemeSection`, `WallpaperSection`, `WidgetsSection`, `HubSection`, `LockLayerSection`). Each reads and saves its own choices through `StartStateRepository`, draws no heading, and is used by both `LauncherSettingsActivity` and `OnboardingActivity`. Setup has a choice step (`SetupStep.isChoice`) for each group: always offered, never "not done", applied at once. `OnboardingPlanTest` fails if the choice steps and the groups drift apart. To add a setting: put it in a section, and add a step if it is a new group.

### Themes, wallpapers and the home backdrop

`LauncherTheme` replaces `SableGlobalTheme` in every Tenseven activity: the theme chosen in Launcher Settings (`appearance_mode`, `appearance_accent`, backed up) wins when set; otherwise the Sable global provider is read, which only exists on SableOS. `ThemePresets` and `WallpaperSets` are pure models (unit tested). `WallpaperArt` paints each wallpaper in code (gradient, soft disc, layered ridges, seeded per set), so no image files ship, and sets it through `WallpaperManager` for Home, Lock or both (`SET_WALLPAPER`). The BlackBerry 7 home always draws on the wallpaper; the BlackBerry 10 home does so when `home_wallpaper` is on (default), by showing the wallpaper window flag and painting its background at 60% (`LocalHomeBackdrop`).

### Hub controls and the peek

`ConversationListenerService` drops notifications from apps in `hub_excluded_apps` (`HubApps`) before they reach `ConversationHub`; `ConversationHub.clearNow()` is the "Clear the Hub now" button. Nothing about message text is persisted. `hub_peek_text` (off by default) decides whether the lock layer and the peek may show the latest message. The peek (`ui/HubPeek`) is an overlay on the BlackBerry 10 Start destination; a pointer handler on the Initial pass opens it from a drag that starts in the left 20 dp of Home and consumes the rest of that drag so the pager does not also move.

### Lock layer

The system keyguard cannot be replaced by a launcher, so the lock layer is a cover: `LockLayerActivity` is `showWhenLocked` and `turnScreenOn`, in its own task, with a translucent wallpaper theme. `ConversationListenerService` registers for `ACTION_SCREEN_ON` while notification access is connected and calls `LockLayerLauncher.showIfLocked`, which needs the setting on, `SYSTEM_ALERT_WINDOW` granted and the keyguard up; it shows a one-pixel overlay window around `startActivity` because Android 14+ only allows a background start while the app has a visible window. Counts come from `LockLayer.tally` over the active notifications (never their text). Unlocking calls `KeyguardManager.requestDismissKeyguard`, so the real lock is always what lets the user in; the camera uses the secure camera intent. A preview from Settings or Setup carries `EXTRA_RETURN_TO` and returns to the screen it came from.

### Default apps, not Sable apps

Starting icons, BlackBerry 10 tiles, Hub actions and the Mail, Calendar and Music glance cards resolve through `DefaultApps` (category intents, the default SMS and dialer roles, an image-capture intent) and need `<queries>` entries for package visibility. Nothing requires a Sable app. There is no weather tile: Tenseven makes no network requests, so the only route to live weather is hosting an Android widget (the Widgets page above).

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
| Active Frames: list and live pictures of recent apps | `ActivityManager` recents plus hidden `IActivityTaskManager` calls (`READ_FRAME_BUFFER`, `REAL_GET_TASKS`) | Recent apps from launches and usage stats as icon cards in the same frame grid (usage access adds apps opened elsewhere) | Done |
| Close a frame (X / Backspace) | `removeTask` | Hides it from the list | Same |
| Recents key opens Active Frames | The system's Recents is redirected to `SHOW_ACTIVE_FRAMES` | Android keeps its own Recents; SableOS's key notes report the system opens its Overview even when an accessibility service consumes the key | A home key or letter opens Active Frames |
| Runtime-permission changes refresh the privacy rows | `OnPermissionsChangedListener` (system API) | `PermissionChangeSignal.register()` returns false; rows refresh on package events only | Refresh when returning to Home |
| Privacy row for work and private profile apps | `createContextAsUser` for that profile | `UserContexts.forUser` returns null; the row says "permissions · unavailable" | Accept; document |
| Lock and unlock Private Space | `MODIFY_QUIET_MODE` | Android decides whether a non-system launcher may do it; the call is already wrapped, so a refusal leaves the space as it was | Mink Private Space port; test on Android 15+ |
| Hub (messages, calls, contacts, notifications) | Sable Hub, Messages and Phone publish snapshot providers | Notification-based Hub (Mink conversations), in memory only | Done |
| Appearance (theme, accent) | Settings hosts `org.sableos.appearance` | Launcher's own preferences (Launcher Settings): `LauncherTheme` uses the saved choice when there is one and the Sable provider otherwise; `ThemePresets` and `WallpaperSets` are pure models | Done |
| Lock layer | Not applicable (the phone's keyguard is Android's) | `LockLayerActivity` (show-when-locked cover, started on screen-on by the notification listener when the overlay permission is granted); it never replaces the keyguard and asks Android to dismiss it before opening anything | Written, not yet run on a phone |
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

