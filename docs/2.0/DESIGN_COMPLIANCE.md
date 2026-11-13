# Design-compliance and architecture acceptance checklist

**M0 documents only; no changed UI or Android binary is claimed.** Every subsequent implementation PR must copy or link this checklist and report actual PASS / FAIL / NOT RUN with reproducible evidence, not prefilled PASS.

## Invariants

- [ ] BBOS7 panels, wallpaper/transparency, lower bar, icon sizes and home configuration persist unchanged.
- [ ] BBOS10 Home, Hub, Peek, Active Frames, app grid, Widgets and navigation are preserved.
- [ ] Hub automatically displays authorized Android-delivered notifications; silent notifications, actions, reply and multiple mail clients remain correct.
- [ ] Lock notification counts and detailed borderless Peek work, with correct restricted-settings and overlay onboarding.
- [ ] Launcher Settings category navigation has Back/Next and direct Hub Settings access.
- [ ] Color contrast follows themes and wallpaper; shared BBOS tokens are implemented inside TenSeven companions, not forced onto third-party apps.
- [ ] Phone keeps actual contact caller ID/photo or neutral anonymous presentation, floating keypad, call controls and Android role consent.
- [ ] Calendar keeps multi-calendar selection, correct Today navigation from Month/Week/Day and event operations.
- [ ] Tasks and Remind snooze/alerts/data migration preserve scheduling; Notes data survives upgrade.
- [ ] All ten games can be opened from the optional Games Pack, with focus, touch and saved-state parity as applicable.
- [ ] Launcher works normally with zero companion APKs installed; third-party Phone/Calendar defaults work.
- [ ] Minimum/Full presets select optional companions without requiring multiple Launcher SKUs.
- [ ] Wizard is accessible during first Setup and later in Settings, with app-by-app status, system install consent and error recovery.
- [ ] Release ZIP and APKs pass artifact hash, package ID, pinned signer, version and hostile-input checks.
- [ ] Previously installed launcher users can migrate data without loss; deny/cancel/retry/rollback are tested.
- [ ] Separate APK module CI, Detekt, ktlint, lint and canonical P2 comparator pass; no baseline mutation or blanket suppressions.
- [ ] Active Frames code remains protected through M0-M5, and tall-touch CI is explicitly resolved before merge/release.
- [ ] Titan 2 signed-install end-to-end acceptance is recorded; other hardware is marked unverified until tested.

M0 status: **documentation updated, runtime acceptance NOT RUN.** Implementers update statuses per PR after executing relevant tests.
