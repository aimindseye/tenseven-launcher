# D4 — BB10 / BB7 Home design compliance (Titan 2)

**Approved reference:** `TenSeven-BB10-Titan2-Q25-Design-Review (1)(1).pdf`, 18 pages. Mark each result **PASS / FAIL / NOT TESTED** and reference a screenshot or observation. PDF example names, dates, message content and telemetry values are synthetic; actual installed inventory and Android-provided metrics must be used.

**Scope:** BB10 and BB7 Home shell, selected-status display, shared reference bar, favorites tray presentation; `ActiveFramesPage.kt` and all Active Frames/media/capture implementation files must remain byte-for-byte unchanged. D4 is not an authorization to add simulated screenshots.

## Page-to-screen compliance table

| ID | PDF page | Requirement | Result | Screenshot / observation |
|---|---|---|---|---|
| D4-01 | p1 | BB10 Home black default / white readable / blue accent, flat rather than filled-cyan cards | NOT TESTED | |
| D4-02 | p1 | Existing Active Frames media and task cards behave unchanged | NOT TESTED | |
| D4-03 | p1 | BB10 top status **Dials** shows CPU, RAM and Battery as thin grey/blue outlined indicators with readable labels | NOT TESTED | |
| D4-04 | p1 | BB10 top status **Text** shows readable compact CPU/RAM/BAT percentages | NOT TESTED | |
| D4-05 | p1 | BB10 top status **Hidden** shows no performance readout | NOT TESTED | |
| D4-06 | p1 | Status readout does not fabricate values when Android hides a metric (shows dash) | NOT TESTED | |
| D4-07 | p1,17 | Shared bottom bar: Hub, optional Phone, current page, All Apps / page markers, optional Command/Search, Camera | NOT TESTED | |
| D4-08 | p1,17 | Current page has thin blue **underline**; no bright rectangular border around every icon | NOT TESTED | |
| D4-09 | p1,17 | Tap Hub / Frames / All Apps / Camera routes correctly, gestures continue to function | NOT TESTED | |
| D4-10 | p1,17 | Existing quick-slot customizations and command access survive upgrade | NOT TESTED | |
| D4-11 | p2 | BB7 retains a large readable clock, date and Favorites tray | NOT TESTED | |
| D4-12 | p2 | BB7 status **Dials**, **Text**, **Hidden** behave independently of BB10 | NOT TESTED | |
| D4-13 | p2 | BB7 lower bar and selected underline match BB10 / All Apps styling | NOT TESTED | |
| D4-14 | p2 | BB7 tray panel cycle, favorites, keyboard navigation and touch menu remain functional | NOT TESTED | |
| D4-15 | p2 | Hold empty BB7 Home to add shortcut; no lost shortcut or accidental launcher reset | NOT TESTED | |
| D4-16 | p1,2 | Settings per-Home style choice persists through restart and BB7/BB10 switching | NOT TESTED | |
| D4-17 | p1,2 | Saved dial metrics remain preserved; legacy users who disabled dials are not forced back on until opting in | NOT TESTED | |
| D4-18 | p1,2 | No unnecessary device polling while Home is backgrounded or in Hidden mode | NOT TESTED | |
| D4-19 | p1,2 | Titan 2 physical QWERTY, Alt+Space, menu/fallback actions remain functional | NOT TESTED | |
| D4-20 | p1,2 | Q25 narrower layout is not asserted as validated until separately tested | NOT TESTED | |
| D4-21 | p15,16 | Command and Home menu are touch accessible, not hardware-only | NOT TESTED | |
| D4-22 | p17 | All Apps layout and quick bar remain coherent with BB10 Home | NOT TESTED | |
| D4-23 | p3–10 | Hub, Peek, lock rail/redaction and selected-message actions still work | NOT TESTED | |
| D4-24 | p1,2 | No Active Frames source change and media/notifications still operational | NOT TESTED | |
| D4-25 | p1,2 | User-selected phone theme / alternate wallpaper are not reset by upgrade | NOT TESTED | |

## New-user and upgrade checks

1. Upgrade over D3 using `adb install -r`. Preserve existing app preferences, wallpapers, shortcut positions and media app selections.
2. In Launcher Settings → Home Screens / Home status, set **BB10 → Dials** and verify three thin gauges. Switch to **Text**, then **Hidden**, and back. Restart launcher and repeat.
3. Change Home style to BB7, choose a *different* status mode. Switch back to BB10; its mode should be unchanged.
4. Test every lower navigation destination from BB10 Home, All Apps and BB7; verify the current-page underline moves correctly.
5. Capture BB7 clock / Favorites / add-shortcut behavior, including an empty-desktop long press.
6. Verify notification unread changes do not remove bar controls; test physical keys and touch fallback.
7. Verify accessibility labels for icons and avoid tiny touch targets; use landscape/small font and large font as available.

## Release blocking policy

No PR promotion if source/Gradle tests fail, if Home navigation is broken, if saved configuration resets, if privacy regresses, or if Active Frames source/media behavior changes. Visual differences from PDF must be noted, fixed, or explicitly deferred by owner approval. **This checklist is a test artifact, not evidence of device acceptance.**
