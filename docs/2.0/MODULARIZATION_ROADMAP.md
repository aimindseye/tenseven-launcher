# TenSeven 2.0 modularization roadmap

**Tracking:** [GitHub issue #129](https://github.com/aimindseye/tenseven-launcher/issues/129) is the milestone checklist; [draft integration PR #128](https://github.com/aimindseye/tenseven-launcher/pull/128) contains the inherited code and architecture docs.

**Status: Approved target, implementation pending.** This document is the delivery contract for the new architecture workstream. See [ADR-0001](../decisions/ADR-0001-MODULAR-COMPANION-APKS.md).

## Fixed rules for each deliverable

- Every change has an isolated branch/PR, exact source commit and evidence; use standard-chat ZIP/local Mac qualification when requested. No developer workstation, keystore or device access is assumed.
- Verify formatting stability (format -> check), debug/release compilation, module unit tests, lint, detekt, ktlint, Android static/security checks and the **canonical P2 comparator**; quality baseline and policies remain frozen. Detect new findings in **every** new application module.
- Maintain a design-compliance checklist mapping changed screens to the approved BBOS7/BBOS10 UI, theme, animation, accessibility, landscape/square and Titan 2 behavior.
- Active Frames source and behavior are protected. A separate tall-touch CI regression is unresolved and must be investigated/retested; never mark it PASS based on unrelated jobs.
- Ensure all new modules have independent CI and packaging tests before removing any code from `:app`.
- Track migration, checksum/signature verification, companion detection, Android install consent and default-app fallback as explicit checks.
- Do not merge or ship merely because a lint finding disappears. All affected features require user/device acceptance.
- Do not modify `main`, merge an unqualified PR, force push, or close a PR without documenting its successor and source SHA.

## M0 — Architecture reset / freeze (this PR)

- Preserve PR127 D0-D6 state via a new long-lived architecture branch rooted at `6d463d3e7f4c1c2d2b029b678f09ed271611acda`.
- Publish ADR, packaging, migration and release acceptance docs, plus README and contribution guidance.
- Open a **draft** replacement integration PR targeting `main`; close PR127 as **superseded, not merged**, after linking its replacement.
- No source, manifest, Gradle, keystore or release artifact changes in M0.
- Gate: documents agree, the replacement branch contains the exact PR127 ancestry, and GitHub verifies both PR states.

## M1 — Contract and inventory audit

- Inventory embedded Activities, receivers, services, resources, preferences, databases, backup schemas, tasks/alarms, Android role dependencies and icon/intent wiring. Write a source-to-module move matrix.
- Design stable app IDs, discovery intents, public API/ContentProvider or explicit exported service contracts, capabilities and signed/permission-scoped IPC; default to **no exported data surface** unless required.
- Audit current D0-D6 acceptance matrix and build baseline, identify historical P2 structural findings, tall-touch CI failure and unrelated pre-existing warnings.
- Prove backup/restore and downgrade/recovery on a copied test profile before changing ownership.
- Gate: reviewed migration matrix, no unowned data, tested fallback design, no secret material.

## M2 — Companion framework + wizard shell

- Retain `:app` and `:design`; add companion package discovery, version/signature verification and minimal/full selection model. New setup should list Missing/Installed/Update/Unavailable status.
- Wizard in Setup and Launcher Settings > Apps & Features > TenSeven Companion Apps; preserves existing Minimum Apps/Full Apps choice as presets.
- Accept an offline release ZIP or official trusted download; validate expected artifact filenames, digests, package IDs, signing certificates and version direction. Reject unknown APKs and ZIP traversal/bombs.
- Use Android `PackageInstaller` or the system package-install UI with required user permission and confirmations; **never claim silent installs**. Allow retry/cancel and continuing without optional companions.
- Gate: fake-package and signer-mismatch tests, no unexpected permission prompts, no install without user action, no regressions in existing Setup.

## M3 — Phone companion

- Independently buildable `phone` APK with BBOS10 styling, call history, contact name/photo/private-caller handling, floating dialpad and Android Telecom qualification.
- Launcher supports the installed TenSeven companion **and** the device-selected dialer. UI interactions use supported Android intents/roles; default dialer requires user action.
- Move Phone-specific permissions/services **only after** successful role, in-call, upgrade and reinstall testing. No implicit call-log transfer.
- Gate: real call flow on Titan 2, anonymous caller, incoming/outgoing calls, hangup identity, dialpad overlay and fallback with companion absent.

## M4 — Calendar companion

- Independently buildable Calendar APK; supports multi-account/calendar selection, Month/Week/Day/Today and event editing.
- Launcher retains Hub calendar strip and permission-gated CalendarProvider access, while navigation launches user-selected/default calendar where possible.
- Gate: Today correctness from each view, multi-calendar event isolation, permission denial, offline behavior, third-party calendar fallback, upgrades.

## M5 — Tasks/Remind, Notes, Calculator, Sound Profiles, Games Pack

- Extract in dependency order, not in one big uncontrolled move: Tasks/Remind and Notes first (data-bearing), then Calculator, Sound Profiles, and Games Pack.
- Preserve reminders/alarm scheduling, snooze, note records, share targets, user settings and game state via verified migration; data ownership must shift transactionally.
- Choose a single optional Games Pack initially; each game has its own launcher entry/activity inside the Games APK if desired, but no games code remains in the Launcher artifact when complete.
- Gate: install/upgrade/uninstall, data equivalence and rollback per app, zero missing/remapped launch icons, isolated permissions.

## M6 — Launcher cleanup and structural refactor

- Once companion acceptance passes, remove migrated embedded Activities, manifest entries, resources and permissions from Launcher. Do not erase stored data until safe migration and retention requirements are met.
- Refactor `SableStartScreen` into state, navigation and screen composition with behavior-preserving tests. Keep Active Frames protected.
- Ensure `StartStateRepository`'s C3 preference-domain separation remains in place, preserving stable keys and backup compatibility.
- Gate: No new canonical quality findings, all module quality gates green, UI parity, optional apps absent/uninstalled scenarios pass.

## M7 — Multi-APK release qualification

- Produce standalone Launcher APK, individually signed companion APKs, Complete ZIP, SHA256SUMS, release manifest, verification and migration documentation.
- Validate app IDs, signer lineage, version codes, APK signature verification, installer wizard, full and minimal presets, bad ZIP handling, independent updates and rollback.
- Run all GitHub CI (including tall-touch Active Frames), SonarCloud/CodeQL/security, canonical quality comparison **and Titan 2 hands-on acceptance**. Other device claims require their own evidence.
- Gate: no merge/release before every blocking issue and CI regression is resolved.

## Scope and progress tracking

| Deliverable | Status at M0 |
|---|---|
| M0 architecture docs / successor PR | **COMPLETE 2026-11-12:** docs committed, PR #128 opened as draft, PR #127 closed without merge and recovery head retained |
| M1 inventory/contracts | Not started |
| M2 installer wizard | Not started |
| M3 Phone APK | Not started |
| M4 Calendar APK | Not started |
| M5 additional companion APKs | Not started |
| M6 Launcher extraction/refactor | Not started |
| M7 release validation | Not started |

## Change control / definition of done

Each subsequent PR documents: original and expected head SHAs; exact files moved; package/data/permission migration; user-visible changes; signing implications; all checks and their real results; Titan 2 checklist; blockers; rollback steps. **No placeholder PASS** and no auto-promotion from synthetic-only tests.

Architecture changes must update ADR-0001 (or create ADR-0002 if it reverses a decision) and adjust this roadmap before code.
