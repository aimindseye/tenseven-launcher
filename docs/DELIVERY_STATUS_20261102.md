# Delivered work and integration status — 2026-11-02

The project owner confirmed the latest Hub/Yahoo/Snoozed fixes work on Titan 2 and requested closure of completed work before P2-B.

## Canonical integration

PR #108 is the main-targeted integration PR for the delivered single-Full-APK stack. Its functional head before this documentation update is e138201e74b05c48a798672f4d73adba36dd7b1d. Android build/test/static, CodeQL, MobSF and Gitleaks checks for that head passed. Titan 2 functionality was accepted by the owner; this is not evidence for other devices or a claim that the entire P2 architecture milestone is finished.

GitHub ancestry checks confirm #76, #86, #87, #88, #89, #93, #94, #96, #97, #101, #102, #103, #104, #105, #106 and #107 are contained in #108. Their separate open PRs are superseded by the canonical integration PR. #101 was validation-only and must not be merged separately.

Wallpaper persistence from #80 and partial-task fallback from #82 have subsequent implementations in this stack. Their old implementation PRs can be closed as superseded; issue #81 remains a P2-B device/regression acceptance item.

Delivered functional issues eligible for closure after the owner's acceptance: #77 (style-relevant settings), #79 (wallpaper persistence), #83 (expandable privacy-safe lock notification panel), #84 (unified Hub navigation), #85 (explicit notification-source opt-in).

## Work still open

- #98: accepted Tasks/Remind product behavior is delivered; remaining milestone-level profile/lifecycle and architecture/quality acceptance stays linked to #100. Do not equate product acceptance with completion of every original engineering criterion.
- #99 and #81: P2-B Active Frames extraction, typed task orchestration, supported frame settings and device qualification.
- #100: remaining screen/Activity extractions, capability coverage, lifecycle/Compose tests and progressive quality gates.
- #95: consolidated release and P3 multi-device qualification.
- #78: broader capability/privacy design, including independent lock-layer triggering, remains partial.
- #92, #90 and #91: deprecation migration is not in the delivered stack; do not merge obsolete build/runtime branches into it without separate validation.
- #9: dependency upgrade is not delivered (the current AGP catalog remains 9.3.3).

The main-targeted Sonar gate must be recorded before merging #108. Do not suppress or weaken a failing gate, mark untested devices qualified, publish a signed artifact without its existing signing workflow, or delete source branches needed for history/build access during this housekeeping.
