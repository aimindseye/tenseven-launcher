# D3 design compliance — Titan 2 validation sheet

**Document authority:** `TenSeven-BB10-Titan2-Q25-Design-Review (1)(1).pdf` (18 pages).
**D3 scope:** Launcher Settings, setup/design tokens, TenSeven utility-app presentation, Hub selected-message/right panel and Remind reveal.
**D4:** BB10/BB7 Home and unified bar. **D5:** Calendar/Phone. **D6:** release gate.
**Test base:** existing D1+D1.1+D2+D2r2-r3 worktree, *not* `main`.

Result codes: **PASS / FAIL / NOT TESTED / DEFERRED**. Do not call a build a visual acceptance. Replace `NOT TESTED` with measured outcomes; record full-size Titan 2 screenshot filenames.

| ID | PDF page(s) | D3 inspection / test | Expected | Result | Screenshot / notes |
|---|---:|---|---|---|---|
| D3-01 | 3–5 (design language) | Launcher Settings landing | Black background; muted text; thin neutral grey boundaries; blue only focus/selection | NOT TESTED | |
| D3-02 | 4 (design language extension) | Settings categories and subpages | Flat rows with clear hierarchy, no cyan-filled or heavily boxed inactive controls | NOT TESTED | |
| D3-03 | 4 (design language extension) | Hub Settings via Launcher Settings | Direct entry; Back returns to settings with no intermediate screen | NOT TESTED | |
| D3-04 | 3–5 | Hub right panel | On selection, message panel opens to right; Hub remains visible | NOT TESTED | |
| D3-05 | 5 | Selected-message box | **SELECTED MESSAGE** label, sender, preview, crisp blue outline; sufficient contrast | NOT TESTED | |
| D3-06 | 5 | Mark-as-read safety | Opening/closing selected-message panel must not mark it read | NOT TESTED | |
| D3-07 | 5 | Provider action selection | Only supported Reply and provider actions; no invented Reply All/Archive; failed action shows error | NOT TESTED | |
| D3-08 | 5 + owner change | Tap Remind near bottom of Titan 2 panel | Expanded choices automatically scroll into view; no manual scrolling | NOT TESTED | |
| D3-09 | 5 + owner change | Remind keyboard navigation | Focus/Back/close/collapse functional; actions perform exactly once | NOT TESTED | |
| D3-10 | 3–5 (design language extension) | Notes list/editor/tasks | Shared black/white/blue, muted borders, legible labels; note contents unchanged | NOT TESTED | |
| D3-11 | 3–5 (design language extension) | Tasks + Remind app | Shared palette, task data persisted; keyboard controls preserved | NOT TESTED | |
| D3-12 | 3–5 (design language extension) | Calculator + Converter tab | Shared palette, keypad/convert and scientific actions preserved | NOT TESTED | |
| D3-13 | 3–5 (design language extension) | Games: Sudoku and one other game | Shared palette, board controls, score, game state and hardware keys preserved | NOT TESTED | |
| D3-14 | 3–5 (design language extension) | Sound Profiles | Flat dark rows; blue focus outlines, not filled blue rows; system audio unaffected | NOT TESTED | |
| D3-15 | 3–5 (design language extension) | Non-default/phone/theme choice | Presets/wallpaper/icon preferences survive restart; chosen colors still respected | NOT TESTED | |
| D3-16 | 6–10 | Lock rail/glance regression | Per-app source details; redaction until Android unlock; selecting a source never reads it | NOT TESTED | |
| D3-17 | 3–5 | Hub, Peek, silent mail/WhatsApp | All Android-delivered active eligible sources remain visible; messaging actions preserved | NOT TESTED | |
| D3-18 | 1–2,17 | Active Frames and media regression | Existing Titan 2 behavior, radio choices, summaries and lower bar unchanged | NOT TESTED | |
| D3-19 | 11–14 | Calendar/Phone placeholder check | No empty fake app icon; standalone implementations remain D5 | NOT TESTED | |
| D3-20 | all | App icons | TenSeven/Tabler when selected; original app icons after explicit opt-out | NOT TESTED | |
| D3-21 | all | Night, daylight and keyboard readability | All text high contrast, tap targets accessible, no clipped controls at Titan 2 density | NOT TESTED | |
| D3-22 | — | Debug and release builds, tests, lint, APK signature | All pass before installation; no stale APK is installed after build failure | NOT TESTED | |
| D3-23 | — | Source guard | No Active Frames implementation, media integrations, manifest permissions, keystore or version changes | NOT TESTED | |

## Design-document boundaries

- PDF page 4 illustrates **Hub's swipe-in settings panel**, not a dedicated Launcher Settings mock. D3 applies the PDF's flat row, muted divider and accent focus rules to Launcher Settings; it does not claim pixel-exact comparison for that screen.
- PDF page 5 explicitly depicts the selected-message blue context outline and separated action rows; **automatic Remind scroll is an additional owner-approved usability requirement**, not a PDF feature.
- PDF does not contain screenshots of every bundled utility. D3 extends its visual language to those screens while preserving their existing app behavior and user preferences.
- PDF page 6–10 lock and page 1–2/17 Home styles are **regression-only** in D3, with D4 doing the Home redesign.
- PDF pages 11–14 Calendar and Phone are D5 deliverables. No privileged API, false live task preview or call-handling capability is implied.

## Acceptance notes

Run all affected apps using their real Launcher icons and also via their existing keyboard entry points. Capture D3-01, D3-05, D3-08 (before/after expansion), D3-10, D3-12, D3-13, D3-14. Record any differences from the approved PDF and report them before creating the D3 PR.

**Release rule:** D3 is not accepted until build + Titan 2 functional tests + visual checklist are complete. Keep D1/D2/D3 off `main` until CI gates and explicit owner approval.
