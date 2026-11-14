# PR127 handoff and immutable recovery checkpoint

**Date:** 2026-11-12. **Purpose:** close PR127 as superseded, not merged; preserve all validated D0-D6 work in a successor **draft** architecture PR.

## Old PR

- Repository: `aimindseye/tenseven-launcher`
- PR: [#127](https://github.com/aimindseye/tenseven-launcher/pull/127), base `main`.
- Head branch: `release/tenseven-2.0-d0-d6-consolidation`.
- Pinned source commit: `6d463d3e7f4c1c2d2b029b678f09ed271611acda` (2026-11-12).
- This branch contains D0-D6 consolidation plus qualified C1/C2/C3 corrections. PR127 was **not merged into main**.
- Preserve the old branch/ref and commit history for rollback; never delete the branch automatically or force-push over it.

## Successor lineage

- New integration branch: `architecture/tenseven-2.0-modular-companions-20261112`, forked **directly from the pinned PR127 head**.
- Successor PR: [#128](https://github.com/aimindseye/tenseven-launcher/pull/128) is a draft against `main`, opened after the architecture docs were committed.
- Its first commits are **documentation only**. The inherited code and unresolved findings are intentionally visible; do not mistake a new PR number for passing quality tests.
- Subsequent feature PRs should branch from and target this integration branch, with independent ZIP/local qualification and real build/test evidence before merge. Rebase or retarget only with explicit tracking of SHAs and acceptance.

## What is actually qualified

- C1: detekt constant/long line fix; 7 -> 5 canonical new findings; pushed.
- C2: Gradle wrapper 9.9.1; 5 -> 4 canonical new findings; pushed.
- C3 R5: StartStateRepository preference-domain split; 4 -> **3** canonical new findings; local compilation, unit/static checks and comparator qualified; pushed as the pinned commit.
- Remaining at the pinned checkpoint: `SableStartScreen` LongMethod; `TenSevenPhoneActivity` LargeClass; `TenSevenCalendarActivity` LargeClass.
- Baseline: `6036d24e2ceafc1af0b780e657a6b068d49b91f4` / 2063 findings; C3 R5 candidate reported 1522 current and exactly three unmatched. P2 gate remained **HOLD**, not PASS.
- Separate Active Frames UI tall-touch job failed on C2-era GitHub CI (two window-focus timeouts and one launch-count mismatch). The failure is **not proven fixed or caused by Gradle**; latest acceptance must be rechecked on the successor PR.
- Titan 2 is the physically tested primary device; no new device or end-to-end modular migration acceptance is implied by the documentation change.

## New decision and required gates

[ADR-0001](../decisions/ADR-0001-MODULAR-COMPANION-APKS.md) changes the destination: separate optional APKs, shared BBOS design library, Launcher-led installer, Full-only Launcher, one Games Pack and verified app migration.

**Do not:** merge PR127, discard D0-D6 code, remove embedded Phone/Calendar/Tasks/Notes yet, update quality baselines to pass CI, force-push old PR history, assert the installer is built, or claim Android verification registrations are complete.

**Do:** inventory shared state first; preserve settings, media, notification and Active Frames behavior; implement migrations before removals; keep each module's own static and functional gates; verify Titan 2 manually before any production release.

## Supersession ledger

| Reference | Status |
|---|---|
| [#127](https://github.com/aimindseye/tenseven-launcher/pull/127) | **Closed without merge** 2026-11-12 after #128 opened; pinned head/branch retained |
| `6d463d3e7f4c1c2d2b029b678f09ed271611acda` | Immutable PR127 recovery commit |
| New architecture branch | Continues from that exact commit |
| [#128](https://github.com/aimindseye/tenseven-launcher/pull/128) | Draft integration workstream; no auto-merge |
| M0 docs | ADR, roadmap, installer/release spec, README/architecture/contribution guidance |
| M1-M7 code | Pending local build, device/CI acceptance |

See [roadmap](MODULARIZATION_ROADMAP.md) and [distribution spec](COMPANION_DISTRIBUTION.md).
