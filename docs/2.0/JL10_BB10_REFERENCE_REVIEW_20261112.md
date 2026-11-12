# JL10 v0.6 — BBOS10 design and behavior reference (2026-11-12)

**Reference only, not implementation authorization.** TenSeven's accepted [modular APK architecture](../decisions/ADR-0001-MODULAR-COMPANION-APKS.md), [roadmap](MODULARIZATION_ROADMAP.md), existing D0–D6 behavior and [design checklist](DESIGN_COMPLIANCE.md) take precedence. Do not copy JL10's code, assets, artwork or root hooks.

## Evidence reviewed

Owner-supplied `JL10v0.6.apk`, earlier static/decompilation evidence, guided ZIP `JL10-guided-20261112-105128.zip` (16 screenshots at 720x720; listed SHA-256 digests match) and a 220.7-second camera video `20261112_105711.mp4` at 1920x1080. Captures are intentionally **not committed**: they may include sensitive notifications and third-party imagery. This is not a device benchmark, a security audit, or an acceptance PASS for TenSeven on Titan 2.

Static inspection exposed Xposed/System UI and Quickstep integrations. JL10's own `System` page describes root-installed Quickstep, an LSPosed module and an `Active, with problems` System UI status. Do not infer that an ordinary unrooted Launcher can reproduce privileged task or system UI behavior.

## Observed features and TenSeven disposition

| JL10 evidence | Observation | TenSeven decision / constraint |
|---|---|---|
| `hub_01_all.png`, `hub_02_accounts.png` | One dated Hub inbox; unread indicators; source drawer for Priority Hub and individual Android notification sources with counts | **Useful design reference:** retain auto-inclusion of accessible Android notifications; add a clearer, optional source drawer with counts and filters; no mandatory manual app assignment |
| `hub_03_filters.png` | Priority Hub filter and clear empty state | Use explicit user-controlled priority, not invented classification |
| `hub_04_item_actions.png` | Open, Mark Unread, Mark Prior Read, prioritize item/source, Delete in a context panel | Separate **Hub-local** read/priority bookkeeping from real Android `Notification.Action` capabilities; do not invent Reply, Reply All, Forward or Delete capabilities unsupported by the source app |
| `hub_05_hub_settings.png` | Email/Calendar accounts, Instant Previews, All notifications, Every message on its own, Keep messages, Excluded apps | **Useful information architecture** for Hub Settings; default to privacy-aware retention and consent; do not presume grouped notifications can always be split into messages |
| `hub_06_sources.png` | Captured source drawer, not an exclusion editor | **Evidence gap:** source exclusion page and category/channel controls not shown |
| `hub_08_email_accounts.png`, `hub_09_additional_settings.png` | Mail address/credential form and advanced IMAP/SMTP ports/security | Direct email syncing is **not a Launcher responsibility**. Existing TenSeven Hub remains notification-based; a Mail companion would need a separate decision/security review, not implicit 2.0 scope |
| `settings_01_home.png` | 5-column/3-row grid, app labels, hidden apps, BB10 icon mapping, icon-pack/shape options | Retain responsive BB7/BB10-specific configurations; do not force 5x3 on other devices or copy third-party icons |
| `settings_02_display.png` | Independent Display, lock and status text sizes; Dark theme | Reuse clear per-surface scaling and theme/contrast behavior in TenSeven-owned surfaces |
| `settings_03_frames.png` | Frame reorder, metadata summaries, message excerpts, snapshots, resume from frame, minimize, maximum-frame control | **High-value reference** for user-facing controls. Keep TenSeven's existing eight-card contract and protected Active Frames source pending separate approval; ordinary APIs do not give live arbitrary task surfaces |
| `settings_04_status.png` | Home versus every-app status choices; cross-app feature explicitly says System UI module needed | Home/lock-owned status styling is feasible; replacing the Android bar in other apps is privileged and excluded |
| `settings_05_lock.png` | BB10 lock screen requires System UI; locked Hub gesture, unread-app column, camera/music buttons | TenSeven's optional overlay must **not** bypass Android's real keyguard; control notification privacy and preserve permissions |
| `settings_06_gestures.png` | System-wide BB10 gestures, swipe up to minimize, up-right Hub peek, customizable edge region, Type & Go | Launcher-owned navigation, trackpad/keyboard and Peek gestures are appropriate; globally intercepting gestures in unrelated apps is not a normal unrooted Launcher capability |
| `settings_07_system.png` | Quickstep root module and LSPosed System UI integration; diagnosis/debug mode | Do **not** require root, LSPosed, privileged recents hooks, or permission auto-grants in the Full release |
| `settings_08_setup.png` | Guided setup and status of notification, accessibility, usage and default Home permissions; separate root-assisted path | Keep guided, honest, user-approved Android permissions; do not emulate root-assisted grants |

## Video findings and limitations

The video shows switching between BB10 app grid, Hub, search and foreground media, followed by returns to a card-style Active Frames view (~02:50–03:41). Cards include app-content imagery rather than icons alone. This supports studying the **look and navigation sequence**, but does **not** establish that background frames are continuously live, that thumbnails update without restoring an app, or that the animation can be implemented without JL10's Quickstep/System UI hooks. No frame-accurate timing or Titan 2 performance claim is supported.

## Recommended priority for TenSeven

1. **Hub UX (P1):** improve the source/account drawer, unread/priority feedback, contextual actions and empty states. Distinguish Android-delivered actions from Hub-owned state. Keep silent notifications and multiple client sources working, with privacy-conscious retention.
2. **Hub settings (P1):** group `Sources & exclusions`, `Notifications & previews`, `Priority / unread`, `Privacy & retention` and `Accounts / companions`. Avoid direct credential management inside Launcher.
3. **Visual consistency (P2):** compact hierarchy, typography, responsive grids, icon options, theme-following contrast, keyboard/focus behavior. Do not enforce JL10's black/cyan palette or third-party assets.
4. **Frames/Peek (P2):** adopt only behavior supported by public Android APIs, independently tested and accepted; do not disturb Active Frames or hide the open tall-touch CI regression.
5. **Explicitly out of scope:** normal APK globally controlling Android secure lock, all-app status bar, privileged Quickstep/Recents transitions, continuously live third-party app surfaces, or silently granting permissions.

## Missing reference evidence

Actual Excluded Apps subpage, Instant Previews in action, message with real inline Reply/Forward, lock-screen Hub interaction, and comparison of continually live vs saved frame previews were not captured. These are **optional future design research**, not blockers for the architecture inventory. No screenshots/video are stored in this repository.

**Scope guard:** The approved Phone, Calendar, Tasks/Remind, Notes, Calculator, Sound Profiles and Games Pack independent APK plan is unchanged. Do not add a Mail APK or new root mode without a separately approved architecture decision. Implementation requires own tests, canonical P2 static analysis, Titan 2 acceptance and license review.

