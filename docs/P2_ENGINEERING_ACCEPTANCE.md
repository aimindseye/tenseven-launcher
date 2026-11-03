# P2 engineering deliverable — candidate version 16

Builds on the accepted feature behavior in PR #110 / version 15. The owner has deferred
building/testing the Full APK until P2 and P3 are both ready. This engineering delivery
must not be represented as physical-device qualification.

## Boundaries

SableStartScreen is now the navigation/composition host (about 730 lines instead of 5,700).
HubPage, CommandScreen, AppsScreen, HomeAppGrid, EditHomeScreen, AppPrivacyScreen,
AppearanceScreen, HomeActionBar, HomeWidgets, Bb10HomeChrome, palette, shared primitives
and immutable argument models have separate cohesive files and explicit action callbacks.
The eight-frame coordinator/window and accepted Hub/Peek/Home behaviors remain intact.

SableStartActivity is about 625 lines instead of 866. LauncherRuntime owns lifecycle-bound
package/profile/permission/content/conversation registrations, bounded inventory/profile
executors and inventory delivery. InventoryCoordinator rejects stale and post-stop results;
profile transitions clear old inventory before reading fresh state. LauncherHomeState owns
Compose state and preference reloads. The Activity retains Android navigation/window hooks,
framework result forwarding and view composition.

PlatformCapabilities/Probe now cover contacts/photos, camera availability, lock state,
secure keyguard and lock-service presence as well as existing notification/usage/calendar,
overlay/task/snapshot/keyboard/widget access. Screens consume immutable, injectable snapshots
from a resumed-lifecycle monitor; grant/revoke/resume and profile signals trigger fresh reads.
Hub/Peek subscribe to the same conversation revision stream while started. Peek text is
removed immediately on denied access or locked capability snapshots. The Hub wake timer
also stops with the screen lifecycle. Providers retain their own defensive Android checks.

## Automated evidence and limits

InventoryCoordinator JVM tests exercise churn/latest-wins delivery, queued results after
stop, restart, destroy/late callbacks and failed reads. Capability tests exercise independent
permission/lock/availability states and ordinary-install snapshot fallbacks. Screen tests cover
Command direct launch, editor Done parity, app-grid direct launch, private-profile filtering,
permission revoke/lock/notification replacement in Hub+Peek, actual Activity recreation and
stop/resume. A two-phase emulator test seeds a frame/style, force-stops the target package,
then asserts restored state and a different OS PID. Existing frame/theme/notification/capture/persistence tests remain.

Injected profile/permission tests verify presentation contracts; they do not claim a real
work/private-profile device, OEM process-pressure behavior, MediaProjection consent-dialog qualification,
or third-party widget/provider qualification. Actual provider binding/configuration/resize
and multi-device keyboard/layout qualification belong to the subsequent P3 delivery.

## Progressive enforcement

The P2 quality workflow formats changed Kotlin sources and fails if a source-only formatter
patch remains. It then generates Lint/detekt/ktlint reports for accepted version-15 commit
6036d24e2ceafc1af0b780e657a6b068d49b91f4 and the candidate. The blocking comparison fails
on added findings or missing/malformed reports. Finding fingerprints use rule/message/source
anchors and multiplicity rather than paths/line numbers so moving legacy code neither hides
findings nor manufactures new ones. Existing debt remains visible; it is not a license to add
new debt. The baseline must not be advanced simply to make a failure disappear.

Compile/JVM/security/UI checks and the existing main-targeted Sonar gate remain required.
Sonar thresholds and coverage exclusions are unchanged. Whole-project legacy static reports
remain available; new/changed P2 code is subject to the additional ratchet.

## Delivery order

P2 engineering first, followed by P3. Active P3 targets: Titan 2, Titan 2 Slim, Zinwa Q25, Zinwa P26 and
an ordinary touchscreen Android device. KEY2 is removed from active scope. Current minSdk
is 34 (Android 14+). No signed APK is published and no additional phone test is requested
before the combined P2/P3 delivery. One Full APK remains the distribution target.

CI results and exact candidate SHA are recorded in PR #111. Keep this candidate open until
all required workflows, including the quality ratchet and expanded emulator tests, pass.

## Android 14 floor and P26 scope revision

Owner approved minimum Android 14 (API 34). API-35 profile availability actions are added
to the receiver filter only on Android 15+; API-34 actions remain available on Android 14.
Receiver registration uses ContextCompat with NOT_EXPORTED. Release verification rejects
an APK declaring the old API-30 floor. This is an implementation fix, not a baseline change.

P3 also includes [Zinwa P26](https://zinwa.com/product/p26-full-device-pre-order/). The
official listing describes a Passport with the P26 motherboard; it does not state the OS
version or establish launcher compatibility. Capture actual OS/display/keyboard data in P3.
