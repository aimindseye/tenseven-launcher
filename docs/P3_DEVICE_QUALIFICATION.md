# P3 device, keyboard, layout and widget qualification

Candidate version 17, Android 14/API 34 minimum. Builds on merged P2 PRs #109–#111.
Engineering checks and device qualification are separate evidence. The owner will build
and test the single Full APK after P2 and P3 engineering are ready. No physical P3 result
is inferred from an emulator, a product listing or earlier launcher builds.

## Active targets

| Device | Current evidence | P3 physical status |
| --- | --- | --- |
| Unihertz Titan 2 | Primary owner test device; earlier feature acceptance through version 14 | Pending on final P3 build |
| Unihertz Titan 2 Slim | Shared Android key contracts and window policy | Pending |
| Zinwa Q25 | Earlier SableOS work records 720×720/density 280; square emulator coverage | Pending on this launcher build |
| Zinwa P26 | Added at owner request; https://zinwa.com/product/p26-full-device-pre-order/ | Pending; record actual OS, window and input devices |
| Ordinary touchscreen Android | Tall/landscape emulator coverage; touch actions remain available | Pending on named physical device |

KEY2 is outside active scope. Every target must actually run Android 14 or newer.
No panel size, density, firmware or OEM scan code is guessed from a model name.

## Engineering changes

- `HomeViewport` derives usable rows/columns from the current window and text scale.
  Safe drawing insets are removed from grid width. Narrow/large-text Active Frames use
  one column with scrolling; default square layouts retain two columns and eight slots.
  BB7 columns also adapt to large text. Touch and Enter continue to launch app cells.
- Keyboard profiles distinguish touch, keyboards reporting Menu, and keyboards using
  modifier-menu fallback. Shared Android Menu, Alt/Fn+Enter and Alt/Fn+Space contracts
  work across both Homes; repeat and Ctrl/Meta chords are not treated as global shortcuts.
  Sym, touch-sensitive keyboards and OEM scan-code mappings require actual device evidence.
- Launcher Settings → Device compatibility shows OS/window/insets/font/input facts.
  Explicit foreground key capture records key/scan/meta/action/source metadata only;
  it does not read Unicode text, listen outside that Activity or send a report anywhere.
  Capture stops on pause. Copy report is an explicit user action.
- Widget bind/configure transactions are journaled before opening Android UI and survive
  controller/Activity reconstruction. Results are checked against stage/id and provider/profile.
  Cancel releases the allocated id; late results cannot finish a different transaction.
  Allocated ids are reconciled independently of temporary provider/profile availability.
  Pending/orphan ids and user removal are cleaned up; saved order and heights remain local.
- Widget views are cleared on pause/lock and unavailable profiles. Vertical resize controls
  persist a bounded height. Hosts use Android's modern list-of-sizes API. Production
  requests no signature widget-binding permission and still uses Android consent.

## Automated evidence

JVM tests cover stale/cancel/phase callbacks, orphan reconciliation, unavailable providers,
resize bounds/overflow, window/text sizing and keyboard fallbacks. API 34 and 35 integration
suites install a real provider in the separate Android test APK, bind through the OS,
create/size host views, reorder, recreate Home, cancel/save configuration and remove ids.
Only the tests temporarily grant/revoke widget binding through Android’s CTS-style shell command. The fixture is absent from Full release.
These checks do not qualify a real OEM widget, work/private-profile device or consent dialog.

Responsive CI additionally runs the P3 screen suite on square/200% text, tall-touch and
landscape/130% text windows. The existing Full build, P2 ratchet, Sonar, security and
process-restoration checks remain blocking. Exact final commit/results are recorded in the P3 PR.

## Combined physical acceptance

For each device record build SHA, APK SHA-256, installed version, registered signing
certificate, model/OS/security patch, actual window/density/insets/font and input report.
Use `tools/device_qualification.py` for read-only device metadata; it creates a pending
report, never an automatic compatibility PASS. Use a report from each actual target.

1. Upgrade the registered single Full APK; check preferences, wallpaper, frame window,
   Hub selections and existing widget order survive. Confirm the installed version.
2. Test BB7 and BB10 at default and large font, portrait/landscape where supported,
   gesture/button navigation and insets. Inspect all Home/Hub/settings/Peek touch targets.
3. Test Menu or Alt/Fn+Enter, Alt/Fn+Space, arrows/Enter/Back, Sym/Fn/Alt, IME composition,
   long press and trackpad modes. Capture unmapped events through the foreground probe.
4. Add a real third-party widget with/without configuration. Test bind denial, configuration
   cancel/save, rotate/recreate during setup, reboot, resize, move/remove and provider removal.
   If a work profile exists, pause/lock/unlock it and verify no stale content or erased ids.
5. Revoke/regrant optional notification/calendar/usage access; exercise both Homes, Hub,
   lock/Peek privacy and the eight-frame replacement rule. Qualify MediaProjection consent,
   protected content, rotation and stop/revocation separately on actual Android.

Mark scenarios PASS/FAIL/NOT AVAILABLE with evidence and observed limitations. Keep missing
hardware results pending. Merge authorization does not turn pending physical results into PASS.

## Reconciled tooling/API dependencies

The P3 integration preserves the original heads of PRs #90, #91 and #9 as merge parents
and reapplies their intended changes to the current Full-only architecture: AGP built-in
Kotlin/new DSL, explicit resValues, removed obsolete flags/report toggles, modern runtime
permission callbacks and AppOps calls, modern widget sizes, and AGP 9.5.0 with Gradle 9.8.2 (official distribution checksum pinned). Lifecycle 2.10.0 in both modules
keeps its bundled lint checks compatible with the upgraded AGP. Compile/target SDK remain 36
and the installation minimum remains 34. Version-catalog GradleDependency exceptions
are limited to the Lifecycle and Compose BOM compatibility pins: their newer lines require
SDK 37, unavailable on the CI SDK channel.
Lint correctness checks, ratchet baseline and thresholds are unchanged. API-30 widget
fallback from the old draft is unnecessary at the approved API-34 floor. The final combined
tree must pass CI before mainline merge; original heads are retained in Git history.
