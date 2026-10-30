# Tenseven Launcher

[![Android](https://github.com/aimindseye/tenseven-launcher/actions/workflows/android.yml/badge.svg)](https://github.com/aimindseye/tenseven-launcher/actions/workflows/android.yml)
[![Checks](https://github.com/aimindseye/tenseven-launcher/actions/workflows/checks.yml/badge.svg)](https://github.com/aimindseye/tenseven-launcher/actions/workflows/checks.yml)
[![Security](https://github.com/aimindseye/tenseven-launcher/actions/workflows/security.yml/badge.svg)](https://github.com/aimindseye/tenseven-launcher/actions/workflows/security.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=aimindseye_tenseven-launcher&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=aimindseye_tenseven-launcher)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform: Android 11+](https://img.shields.io/badge/Platform-Android_11+-167C80.svg)](docs/ARCHITECTURE.md)

Tenseven is a privacy-focused, keyboard-first Android home screen for phones with a physical keyboard.
It gives you two classic layouts to choose from, **a BlackBerry 10 style** (Hub, Active Frames and app
pages) and **a BlackBerry 7 style** (clock and status over a tray of apps), and drives them from the
keyboard and trackpad: type to find an app, hold a letter to jump to it, move with the trackpad. It is an
ordinary app you install and set as your home app; no root and no custom ROM.

The launcher comes from **Sable Start**, the home screen of
[SableOS for the Zinwa Q25](https://github.com/aimindseye/zinwaq25-sableos), rebuilt here as a standalone
Gradle project so it can run on any Android 11+ phone, with new icons
([Tabler](https://github.com/tabler/tabler-icons)) and the missing pieces filled in from
[MinkLauncher OpenSource](https://github.com/katoaapps/openminilaunch).

> **Status: early scaffold. Nothing here has been run on a phone yet.** The Sable Start sources
> (about 11,600 lines of Kotlin, 17 unit-test files) and the Sable design library (6 test files) are
> imported and wired into a Gradle build; the CI workflows are modelled on SableOS's. The first CI run
> is the first time this code is compiled outside SableOS's own build, so expect a few fix-up commits.
> There is **no release** and no installable APK except the debug build CI uploads.
> What is done and what is next: [`ROADMAP.md`](ROADMAP.md).
> Not tested on any device. The home layouts were designed for the Zinwa Q25's 411 dp square screen;
> other screens need checking (roadmap, phase 2).

```text
APP=Tenseven Launcher (io.github.aimindseye.tenseven)
PHASE=0 scaffold: sources imported, build and CI written, not yet green
BASE=Android 11+ (minSdk 30), compileSdk and targetSdk 36, ordinary install, no platform signature
HOME_STYLES=BlackBerry 10 style, BlackBerry 7 style (imported from Sable Start)
ICONS=Tabler Icons outline 3.50.0 (MIT), generated into the app by tools/tabler_icons.py
APPS_BUILD=NOT_RUN in GitHub Actions yet (Gradle build, unit tests, Lint, detekt, ktlint, Kover)
DEVICE_TESTED=NONE
QUALITY_GATES=report only until TENSEVEN_STRICT_QUALITY=true (docs/BUILD.md)
BORROWED_FROM_MINK=reference snapshot only, none ported yet (third_party/minklauncher/PORTING.md)
SABLE_KEYBOARD=separate companion app, not in this repository yet (docs/ARCHITECTURE.md)
RELEASES=NONE
```

## What you get

| | |
|---|---|
| **BlackBerry 10 style** | Hub, then Active Frames (your recent apps), then pages of app icons. Phone, Search and Camera along the bottom. Up to 8 frames, 5 icons per row |
| **BlackBerry 7 style** | Clock and status on the wallpaper over a tray with Favorites, All and Downloads panels. Alt+Left/Right or Space switches panels |
| **Type to find** | Press a letter on Home to search apps and Sable actions; prefixes such as `?app`, `@person`, `/settings`, `!action`, `/private`, `/work` narrow it |
| **Hold a letter** | Hold a key on Home to open the app you assigned to it, or call a speed-dial contact where Sable Phone is installed |
| **All Apps** | A to Z with a privacy row per app (permissions and special access), Private Space and work profile support, uninstall through the system |
| **Hub** | Unread bar, then your message and email conversations grouped per person with inline reply (Tenseven's own notification listener, in memory only, text hidden while locked), calls and contacts. Sable Hub, if installed, still supplies its own counts |
| **Edit Home** | Reorder the grid, the bottom bar and the frames with the keyboard (Enter picks up and drops, arrows move) |
| **Status dials** | Wi-Fi, mobile, CPU, RAM, battery time left |
| **Assign a letter** | In Command type `assign k camera` and pick the app: holding K on Home now opens it. `unassign k` frees the letter |
| **Most-used first** | Command ranks the apps you open most ahead of other matches, so two letters are usually enough. Counts stay on the phone and fade over time |
| **Hub filters** | Filter conversations by person, unread, messages or email. Mark read (button or `M`) and snooze for an hour or until tomorrow (button or `S`); snoozed conversations leave the list and the unread counts and come back by themselves |
| **Mail tile** | The BB10 Mail tile shows the newest unread email from your notifications (text hidden while locked) and opens your default email app. Weather stays a shortcut: Android has no weather data API, and Tenseven asks for no internet access |
| **BB7 tray** | Menu key: show or hide the Favorites, All, Downloads and Frequent panels, make the banner and tray more see-through or darker, or change the wallpaper |
| **Gestures** | Swipe down on the BB10 Home to open Command |
| **Extra apps** | Optional Calc Plus (exact and scientific calculator with unit converter; named so it does not clash with the phone's own Calculator), Notes and ten games (Sudoku, Minesweeper, 2048, Bricks, Snake, Blocks, Reversi, Solitaire, Mahjong, Words). None is on by default: tick the ones you want in the setup's Extras step, or later from Command, Extra apps. Nothing is downloaded; they ship inside Tenseven. In every extra, Esc leaves and Menu lists its keys |
| **Default apps** | BlackBerry 10 tiles open your phone's own Calendar, Weather, Music, Email and Gallery apps; next event and now-playing come from Android |

Keyboard shortcuts in the SableOS build are written, but not yet confirmed on every device; the repo
records which keys each phone sends in `docs/ARCHITECTURE.md` as they are measured.

## Privacy and permissions

Tenseven has no accounts, ads, analytics, trackers or servers, and does not use the internet.
Launcher state is in app-private storage. Recent activity is kept in memory only.

| Permission | Why | Optional |
|---|---|---|
| Home app role | Be the home screen | needed to be a launcher |
| `READ_CONTACTS` | Hub Contacts tab: names and one number per person, read on the phone, never stored | yes; the tab asks again |
| `READ_MEDIA_IMAGES` | Local photo counts on Home | yes |
| `REQUEST_DELETE_PACKAGES` | Hand uninstall to the system uninstaller, which confirms | yes |
| Usage access (a special access you grant in Settings) | Active Frames shows a row of recently used apps (names and times only; not stored or sent) | yes; off until you turn it on |
| Notification access (a special access you grant in Settings, not a manifest permission) | Counts unread messages and email per app for the Hub bar, grouped per person; read on the phone, held in memory only, never stored or sent | yes; off until you turn it on |
| Accessibility service "Tenseven double-tap to lock" (a special access you grant in Settings) | Double-tap empty Home space to lock the screen. It can only perform the lock action: it reads no screen content and receives no events | yes; off until you turn it on in Accessibility settings and in Appearance |
| `ACCESS_HIDDEN_PROFILES` | Show the Private Space; Android grants it to the default Home app only | yes |

No signature permissions: an ordinary install cannot hold them, and CI fails if the manifest asks for one
(`tests/run.sh`). Features that need them on SableOS (live thumbnails in Active Frames, closing tasks,
work-profile privacy rows) have an ordinary-install fallback; see the table in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Notification access is used by the Mink-based Hub (unread bar, conversations per person, inline reply) and
usage access by the recent-apps row on Active Frames. Neither is asked for silently: the setup screen
(first run, and Setup and permissions in the menu) explains each one, and every optional step can be skipped.

### If Android greys out Usage or Notification access

Android 13+ restricts these for apps installed from a file, and some phones (the Titan 2 included) have no menu in App info to lift it. From a computer with adb, replace the id with `io.github.aimindseye.tenseven` (release) or `io.github.aimindseye.tenseven.debug`:

```
adb shell appops set --user 0 <id> GET_USAGE_STATS allow
adb shell cmd notification allow_listener <id>/io.github.aimindseye.tenseven.conversations.ConversationListenerService
```

## Install

There is no release yet. When there is one:

1. Download the APK from the GitHub release and open it; approve installing from that source if Android asks.
2. Press Home and choose **Tenseven** as the home app (or Settings > Apps > Default apps > Home app).
3. Follow the setup screen that opens on first run: the home role first, then each optional permission with its reason.
4. Open the style chooser (menu on Home > Home screen style) and pick BlackBerry 10 or BlackBerry 7.
5. **Notification or usage access on Android 13 or newer:** Android greys these switches out for apps
   installed outside a store until you open Settings > Apps > Tenseven, tap the top-right menu and choose
   **Allow restricted settings**. The setup screen shows this step when it applies. Only do this for an APK you trust.

To try the current debug build, download `tenseven-debug-apk` from a green run of the
[Android workflow](https://github.com/aimindseye/tenseven-launcher/actions/workflows/android.yml) (it
installs next to a future release as `io.github.aimindseye.tenseven.debug`).

## Supported devices

Tenseven needs Android 11 or newer. A physical keyboard is the point, but nothing stops it running on a
touch-only phone.

| Device | Keyboard | Status |
|---|---|---|
| Zinwa Q25 (SableOS or stock Android) | QWERTY, trackpad | Layouts designed for it; not yet run |
| Unihertz Titan 2, Titan Pocket | QWERTY | Not yet run |
| BlackBerry KEY2, KEYone | QWERTY | Not yet run |
| Any other Android 11+ phone | on-screen or Bluetooth keyboard | Not yet run |

To add a device, capture its keys with the event viewer in Sable Keyboard (`KeyProbeActivity`) or
`adb shell getevent -lt`, and add the result to the key map in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#key-profiles).

## Guides

| Guide | What it covers |
|---|---|
| [Roadmap](ROADMAP.md) | Phases from this scaffold to a first release, and what is not planned |
| [Architecture](docs/ARCHITECTURE.md) | Modules, how the imported sources are organised, platform-only features and their fallbacks, key profiles, the keyboard companion |
| [Build](docs/BUILD.md) | Build and test locally, quality switch, lock files, SonarCloud setup, GitHub Actions |
| [Brand assets](docs/BRAND_ASSETS.md) | How provider logos for messaging and AI apps are handled and recorded |
| [Sources](docs/SOURCES.md) | Every upstream with its pin and licence, and every change made to imported files |
| [Porting from Mink](third_party/minklauncher/PORTING.md) | Which MinkLauncher pieces go where, in what order |
| [Contributing](CONTRIBUTING.md) | Workflow, attribution rules, what a pull request needs |
| [Security](SECURITY.md) | How to report a vulnerability |

## Build it yourself

You need JDK 17 and the Android SDK with platform 36 and build-tools 36.0.0. Nothing else.

```bash
git clone https://github.com/aimindseye/tenseven-launcher
cd tenseven-launcher
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug                 # app/build/outputs/apk/debug/
./gradlew :design:testDebugUnitTest :app:testDebugUnitTest
bash tests/run.sh                            # static checks, no SDK needed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Details, the quality switch and the lock files: [`docs/BUILD.md`](docs/BUILD.md).

## Repository layout

```text
app/                      the launcher (Gradle application module, io.github.aimindseye.tenseven)
  src/main/java/io/github/aimindseye/tenseven/   Sable Start sources, imported (renamed from org.sableos.start)
  src/main/res/                    strings (18 draft translations), theme, Tenseven launcher icon
  src/test/                        17 unit-test files from Sable Start
design/                   Sable design library (theme, tokens, shared components, 18 draft translations)
config/                   detekt rules (byte-exact SableOS copy) and their provenance
gradle/                   version catalog and wrapper
third_party/
  tabler-icons/           verbatim SVGs, licence, manifest with sha256, provenance
  minklauncher/           read-only reference snapshot of the MinkLauncher pieces to port, with attribution
  titan-ai/               MIT notice for the status rings
brand-assets/             catalog of messaging and AI provider logos (source, status, sha256)
tools/                    tabler_icons.py, brand_assets.py, refresh-locks.sh
tests/run.sh              static checks: shellcheck, XML, generated icons, catalog, manifest permissions
docs/                     guides
.github/workflows/        GitHub Actions: checks, Android, Security, SonarCloud
sonar-project.properties  SonarCloud analysis scope
```

## Continuous integration

GitHub Actions runs on every pull request and on `main`, with the same pinned actions SableOS uses:

| Workflow | What it runs |
|---|---|
| `checks` | `tests/run.sh`: shellcheck, XML well-formedness, Tabler icon manifest and generated files, brand catalog, detekt config provenance, licences and provenance of `third_party/`, no signature permissions in the manifest, no keys or APKs tracked, Gradle wrapper checksum |
| `Android` | Gradle build of the debug APK, unit tests, Android Lint, detekt, ktlint and Kover coverage (a baseline, no threshold); uploads the APK and the reports |
| `Security` | Gitleaks history scan and MobSF source scan (also weekly) |
| `SonarCloud` | SonarCloud scan of the whole repository, waiting on the "Sonar way" quality gate; needs the `SONAR_TOKEN` repository secret |

`Android` and `checks` skip documentation-only changes. SonarCloud runs as CI-based analysis, so
Automatic Analysis must stay off in the SonarCloud project. Until the imported sources are clean,
detekt, ktlint and Lint **report without failing**; compile errors and failing tests always fail. Set the
repository variable `TENSEVEN_STRICT_QUALITY` to `true` to make them fail
([`docs/BUILD.md`](docs/BUILD.md#quality-gates)).

## Boundaries

This repository must never contain signing keys, keystores, device identifiers, personal data, or
proprietary artwork or fonts. BlackBerry's own icons, wallpapers, fonts and apps are not used and not
planned: Tenseven's two layouts are original code that borrows the *arrangement* of the BlackBerry 10
and 7 home screens, nothing else. Provider logos for other companies' apps follow
[`docs/BRAND_ASSETS.md`](docs/BRAND_ASSETS.md).

## Licence

Apache License 2.0 ([`LICENSE`](LICENSE), [`NOTICE`](NOTICE)). Third-party components keep their own
licences: see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). The Sable apps' translations
(18 languages) are machine-assisted drafts, not reviewed by fluent speakers; English is the reference
text, and corrections are welcome.

BlackBerry is a trademark of its owner. Tenseven is not affiliated with or endorsed by BlackBerry Limited.

## Credits

- **Sable Start and the Sable design library:** [SableOS for the Zinwa Q25](https://github.com/aimindseye/zinwaq25-sableos).
- **Icons:** [Tabler Icons](https://github.com/tabler/tabler-icons) by Paweł Kuna (MIT).
- **Notification, usage, Private Space, onboarding, icon pack, backup and command ideas and code to port:**
  [MinkLauncher OpenSource](https://github.com/katoaapps/openminilaunch) by Katoa Apps (Apache-2.0).
- **Status rings:** [Titan AI](https://github.com/andrehafner/aiassistant) by Andre Hafner (MIT).
