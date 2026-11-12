# ADR-0001: TenSeven 2.0 modular companion APKs

- **Status:** Accepted for the TenSeven 2.0 architecture workstream (2026-11-12).
- **Decision owner:** TenSeven project owner.
- **Replaces:** Bundling independent Phone, Calendar, Tasks/Remind, Notes, Calc Plus, Sound Profiles and games as switchable Activities inside the Launcher APK.
- **Implementation status:** Planned, not shipped. This ADR changes documentation only; the existing embedded Activities remain operational until validated migrations.
- **Source checkpoint:** PR #127 at commit `6d463d3e7f4c1c2d2b029b678f09ed271611acda` (not merged). The replacement integration PR carries this history; nothing from D0-D6 may be silently dropped.

## Decision

1. **One Full Launcher APK, no Lite SKU.** Keep `io.github.aimindseye.tenseven` as the stable launcher package and signing identity. Launcher owns BBOS7/BBOS10 Home, Hub, Peek, lock layer, Active Frames, app discovery, themes, launcher-level animations, keyboard/trackpad, Settings, widget hosting, notification integration and onboarding.
2. **Independent optional companion APKs** own full-app UIs, data, permissions and updates: Phone (including floating dialpad and Telecom services), Calendar, Tasks/Remind, Notes, Calc Plus, Sound Profiles and one Games Pack containing the ten existing games initially. Install/uninstall of one companion must not disable basic Launcher operation.
3. **Interoperability first.** The Launcher supports any compatible third-party Phone/Calendar/default applications through Android public APIs and the user's defaults. A TenSeven companion is an optional richer BBOS10-branded option, never an enforced replacement.
4. **Shared design, separate processes.** Shared BBOS10 visual components and design tokens live in reusable library code (currently `:design`); companion APKs must implement their own internal screens and transitions. Launcher-level animations do not impose visuals on third-party apps.
5. **One monorepo, multiple Android application modules** is the initial implementation plan: retain `:app` for Launcher, `:design` for reusable design, and add independently buildable companion application modules incrementally. Avoid premature repository splits or renaming `:app`. Shared interfaces may form a small contract module if demonstrably needed.
6. **Release a standalone Launcher APK and a complete ZIP.** An integrated setup wizard in Launcher Settings > Apps & Features > TenSeven Companion Apps offers Minimal/Full presets and per-app choices. The ZIP is not an executable installer. Android remains the authority that confirms APK installation and grants permissions.
7. **Never use modularization to bypass static analysis.** Keep frozen quality comparison and CI enforcement; separately build, lint, test and analyze each companion. Fix structural findings in actual code. Do not exclude moved source trees, lower thresholds, add blanket suppressions, or accept hidden regressions.
8. **No premature removal.** Preserve and regression-test every existing D0-D6 behavior on Titan 2. Embedded apps remain until install, update, data migration, rollback and consent flows have passed the gates in the roadmap.

## Current reality (do not describe as already delivered)

At this checkpoint `settings.gradle.kts` includes only `:app` and `:design`. The Launcher manifest contains embedded Phone, Calendar, Tasks/Remind, Notes, Calc Plus, Sound Profiles and ten games. The optional extra Activities are disabled by default and turned on through Setup. Their code still ships inside the Launcher APK. Phone also includes Telecom components. Separation is **not implemented** by this ADR.

## Proposed package names (NOT registered yet)

| Artifact | Proposed application ID | Notes |
|---|---|---|
| Launcher | `io.github.aimindseye.tenseven` | Existing package ID; stable |
| Phone | `io.github.aimindseye.tenseven.phone` | Calls, call history, default dialer role and floating keypad; role request stays Android-owned |
| Calendar | `io.github.aimindseye.tenseven.calendar` | Android CalendarProvider and multi-calendar UI |
| Tasks | `io.github.aimindseye.tenseven.tasks` | Tasks, reminders, notifications and alarms |
| Notes | `io.github.aimindseye.tenseven.notes` | Note data and share targets |
| Calculator | `io.github.aimindseye.tenseven.calculator` | Calc Plus and unit conversion |
| Sound Profiles | `io.github.aimindseye.tenseven.profiles` | Sound/DND-specific permissions |
| Games Pack | `io.github.aimindseye.tenseven.games` | Sudoku, Minesweeper, 2048, Bricks, Snake, Blocks, Reversi, Solitaire, Mahjong, Words |

All companion IDs and signing decisions require registration/verification before public release. The owner has confirmed an Android Developer Console account of type **Personal (Full distribution)**. That account does not require creating a separate developer account per companion. Reusing one signing certificate across apps is technically possible but **not yet a release decision**; security isolation versus shared-signature integration must be reviewed. Never commit secrets, keystores or credentials.

## UX invariants

- Preserve BBOS7 and BBOS10 configuration isolation, wallpaper/theme behavior and launcher settings navigation.
- Preserve Hub/Peek/lock notification behavior, dynamic Android notification actions, multi-mail/multi-calendar integration, silent-notification handling and permission guidance.
- Preserve Active Frames behavior **unchanged** during the architecture transition, including its protected test/source boundaries.
- Preserve Phone's BBOS10 dialer, call identity, private caller fallback, floating dialpad; Calendar's Month/Week/Day/Today correctness, event editing and calendar selection **inside their future APKs**.
- Preserve Tasks/Remind alarms, snoozed items, Notes data and Games; do not claim a migration is done until verified on upgrade and rollback.
- Preserve fallback to user-selected/default third-party apps when a TenSeven companion is absent; do not introduce hard dependencies.
- Existing users keep their current launcher, settings and embedded apps until replacement-install and migration acceptance is proven.

## Consequences and non-goals

Benefits: independent app updates, permission minimization, smaller Launcher payload, separately testable modules and optional installation. Costs: package ownership, migration/backup tooling, installer UX, signature registration, IPC/integration contracts and more release artifacts.

Non-goals for this documentation-only PR: coding the wizard, generating signing keys, registering package IDs, deleting embedded sources, adding build modules, changing runtime behavior, merging PR #127, or waiving CI failures.

## Decision hierarchy

For TenSeven 2.0, this accepted ADR and the linked roadmap/distribution specification supersede older language describing embedded Extras as the future design. Historical 1.0.0 documentation and acceptance results remain factual records. Any deviation requires a new explicit decision, not ad-hoc style-fix churn.

See [Modularization roadmap](../2.0/MODULARIZATION_ROADMAP.md), [Distribution and migration](../2.0/COMPANION_DISTRIBUTION.md), and [PR127 handoff](../2.0/PR127_HANDOFF.md).
