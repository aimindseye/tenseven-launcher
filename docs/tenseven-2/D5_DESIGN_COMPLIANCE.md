# TenSeven D5 design and device compliance — Titan 2 / Q25

Basis: approved 18-page TenSeven BB10 Design Review; pages **11–12 Phone** and **13–14 Calendar**. The source is a **companion-app D5 first slice**, not yet a default dialer.

Each scenario must be marked PASS / FAIL / NOT TESTED. Attach device screenshots, Android version, chosen Home style, theme, and font size. Do not merge until the mandatory gates pass.

## D4r12 Settings corrections

- [ ] S01. Appearance & Home status choices display **Dials / Icons / Hidden** (not Text).
- [ ] S02. Switching Icons updates BB7 and BB10 header while saved `Text` mode remains valid after upgrade.
- [ ] S03. Status-size Default help text does not mention "Previous Large".
- [ ] S04. On fresh new profile Storage is enabled alongside existing Wi-Fi, Mobile, CPU, RAM, Battery, Cell metrics.
- [ ] S05. On upgrade, previously saved metric selections are unchanged (including explicitly disabled Storage).
- [ ] S06. Existing 7-category Settings Back/Next, Permissions and theme choices still work.

## Optional app activation / permissions

- [ ] O01. Existing Full APK in-place upgrade preserves package ID and signing cert.
- [ ] O02. New optional Calendar/Phone launchers are initially disabled; no surprise icons on upgrade.
- [ ] O03. Apps & Features > Custom enables each app; icon appears in All Apps and Home grid.
- [ ] O04. Disabling one hides its launcher entry; the other remains enabled.
- [ ] O05. Minimum preset still selects only Calc Plus, Tasks and Notes.
- [ ] O06. Full preset includes both D5 apps and all other bundled tools/games.
- [ ] O07. No extra READ_CALL_LOG, CALL_PHONE, privileged Telecom or new overlay permission requested by D5.
- [ ] O08. Existing notification/HUB configuration unaffected by enabling either app.

## Calendar 2.0 (PDF pages 13–14)

- [ ] C01. Calendar displays month and selected-day agenda on Titan 2 without clipping.
- [ ] C02. Q25 720x720 layout: week labels, 7-column month, selected date and bottom actions fit.
- [ ] C03. Without READ_CALENDAR, a clear grant request appears; declining leaves app functional but empty.
- [ ] C04. Granting READ_CALENDAR reveals the visible calendars synced to CalendarProvider.
- [ ] C05. Account calendar selections show/hide corresponding month and agenda events.
- [ ] C06. Hub's multi-calendar selection and Calendar companion selection remain consistent.
- [ ] C07. Selecting a day updates agenda; previous/next day navigates across month boundary.
- [ ] C08. Busy arc increases with occupied time and does not double count overlapping meetings.
- [ ] C09. An all-day event receives a separate thin outer indicator.
- [ ] C10. Schedule opens a 7-day selector, selected day content stays legible.
- [ ] C11. Tapping an event opens the user's calendar provider/viewer.
- [ ] C12. New Event opens Android's configured calendar editor with selected date/time.
- [ ] C13. Hub button opens the existing TenSeven Hub page.
- [ ] C14. No fake "Design review"/"Planning call" rows; real event content only.
- [ ] C15. No calendar data sync, upload or persistent event history caused by D5.

## Phone 2.0 companion (PDF pages 11–12)

- [ ] P01. Phone app offers Calls, Contacts, Keypad tabs with no truncated labels on Titan 2.
- [ ] P02. Phone app usable on square Q25 without assuming physical scan codes.
- [ ] P03. Keypad digits 0–9, *, # and delete function using touch.
- [ ] P04. Titan 2 hardware digit/delete input works when keypad focused.
- [ ] P05. Continue in Phone sends tel: number to Android ACTION_DIAL; user confirms call in default Phone app.
- [ ] P06. Emergency number opens default Phone app, without TenSeven directly invoking ACTION_CALL.
- [ ] P07. Contacts tab handles permission denial, grant and contact-number search.
- [ ] P08. Selecting contact opens Android Phone with selected number; TenSeven does not call directly.
- [ ] P09. Floating dialpad opens as a separate translucent companion activity and Close returns.
- [ ] P10. Calls tab clearly delegates real call history to system Phone app; no invented log entries.
- [ ] P11. No default dialer role / InCallService switch; Android in-call and emergency UX unchanged.
- [ ] P12. Disabled Phone companion does not alter system Phone role or speed-dial associations.

## Regression / release gates

- [ ] R01. `:app:testDebugUnitTest` PASS including new D5 busy/pad policies.
- [ ] R02. `:app:assembleRelease` PASS; registered signing key verified.
- [ ] R03. `:app:lintRelease` PASS; Gradle dependency warnings triaged separately.
- [ ] R04. `tools/tenseven-d0-frames-guard.py` PASS and Active Frames unchanged.
- [ ] R05. Wallpaper, Hub swipe, notifications/Peek/lock, weather, icon choices and status labels regressions absent.
- [ ] R06. Source PR/CI/SonarCloud remains pending until device acceptance; no assumed merge.

**Boundary:** D5 first slice does not implement recent-call history, voicemail, favorites backed by call log, or default-dialer/in-call service. Review those separately as an explicitly opted-in Phone role milestone. Do not claim D5 fully replaces Android Phone.
