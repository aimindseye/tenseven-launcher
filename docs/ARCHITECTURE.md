# Architecture

## Modules

```text
:app      application, io.github.aimindseye.tenseven (Compose UI, activities, repositories, generated Tabler glyphs)
:design   library, io.github.aimindseye.tenseven.design (theme, tokens, shared components, string tables), used by :app
```

`:app` contains the whole Sable Start source tree, the activities included. SableOS builds the same files as
`SableLauncher` with its Android.bp (system SDK); Tenseven builds them with Gradle against the public SDK, so
anything that needs a system API is isolated behind a small helper (below).

### Source layout inside `:app`

Imported from SableOS, directories and packages unchanged until the rename in `ROADMAP.md` phase 1
(directory `app/src/main/java/io/github/aimindseye/tenseven`; renamed from upstream `org.sableos.start`):

| Directory | Package | What |
|---|---|---|
| `model/` | `io.github.aimindseye.tenseven.model` | Pure Kotlin policy, unit tested: home styles and layouts, app glyphs, quick bar, letter shortcuts, speed dial, command queries, Hub bar, text tables |
| `privacy/` | `io.github.aimindseye.tenseven.privacy` | The All Apps privacy row: the evaluator, summary policy and snapshot cache (pure Kotlin) |
| `platform/` | `io.github.aimindseye.tenseven.platform` | Android access: `LauncherAppsRepository`, `RecentTasksRepository`, `StartStateRepository`, `LiveSurfaceRepository`, `PeopleRepository`, `PrivacyFactsReader`, `DeviceStatsSampler`, and Tenseven's `PermissionChangeSignal` and `UserContexts` |
| `conversations/` | `io.github.aimindseye.tenseven.conversations` | Notification access: `ConversationListenerService` reads message and email notifications into `ConversationHub` (in memory only); feeds the Hub bar counts; ported from Mink, see `third_party/minklauncher/PORTING.md` |
| `ui/` | `io.github.aimindseye.tenseven.ui` | Compose: `SableStartScreen`, `Bb7HomeScreen`, glyphs and status rings |
| `live/` | `io.github.aimindseye.tenseven.live` | Models for the live tiles (weather, calendar, mail, media) |
| (root) | `io.github.aimindseye.tenseven` | `SableStartActivity` (HOME) and `HomeStyleActivity` |

Rule that makes this testable: decisions live in `model/` and `privacy/` with unit tests; `platform/` and
`ui/` only gather facts and draw. Keep it that way for anything added.

## Features that need the system, and what an ordinary install does

SableOS gives Sable Start signature permissions because the launcher is platform-signed. An ordinary install
cannot hold them, so each such feature has a fallback, and the Mink port named in the last column is what
replaces the missing data.

| Feature | On SableOS | Ordinary install today | Plan |
|---|---|---|---|
| Active Frames: list and live pictures of recent apps | `ActivityManager` recents plus hidden `IActivityTaskManager` calls (`READ_FRAME_BUFFER`, `REAL_GET_TASKS`) | Only this app's own tasks; frames are mostly empty | Recent-apps strip from usage stats, icons only (Mink wellbeing, usage access) |
| Close a frame (X / Backspace) | `removeTask` | Hides it from the list | Same |
| Recents key opens Active Frames | The system's Recents is redirected to `SHOW_ACTIVE_FRAMES` | Android keeps its own Recents; SableOS's key notes report the system opens its Overview even when an accessibility service consumes the key | A home key or letter opens Active Frames |
| Runtime-permission changes refresh the privacy rows | `OnPermissionsChangedListener` (system API) | `PermissionChangeSignal.register()` returns false; rows refresh on package events only | Refresh when returning to Home |
| Privacy row for work and private profile apps | `createContextAsUser` for that profile | `UserContexts.forUser` returns null; the row says "permissions · unavailable" | Accept; document |
| Lock and unlock Private Space | `MODIFY_QUIET_MODE` | Android decides whether a non-system launcher may do it; the call is already wrapped, so a refusal leaves the space as it was | Mink Private Space port; test on Android 15+ |
| Hub (messages, calls, contacts, notifications) | Sable Hub, Messages and Phone publish snapshot providers | Empty unless those apps are installed | Notification-based Hub (Mink conversations) |
| Appearance (theme, accent) | Settings hosts `org.sableos.appearance` | Falls back to the defaults | Launcher's own preferences |
| Speed dial | Sable Phone provider and `org.sableos.permission.SPEED_DIAL` | Not available; letter shortcuts for apps still work | Launcher-owned letter-to-contact map |
| Live tiles: weather, calendar, mail, media | Sable app providers | Shown only when those apps are installed (their providers are in `<queries>`) | Optional; not required |

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
| Unihertz Titan 2 | not captured | not captured | not captured | not captured | not captured | no |
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
