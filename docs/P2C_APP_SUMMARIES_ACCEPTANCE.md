# P2-C app summaries

Current: version 13 / 0.2.0-p2c-summaries.3. File linking and Office previews have been removed. The version-13 rules below supersede the historical version-11/12 sections.

One Full APK: version 11 / 0.2.0-p2c-summaries.1. Builds on accepted Titan 2
version 10 from PR #109. No signing key or network permission is added.

## Access and sources

| Integration | Source | Access |
| --- | --- | --- |
| Music, podcasts, audiobooks | Active media sessions, including paused metadata | Android notification access |
| Titan 2 FM Radio / player fallback | Notification text from explicitly selected apps | Notification access plus source opt-in |
| Calendar | Hub-selected Android CalendarProvider events | Calendar permission |
| Mail / messaging | Unread, unsnoozed Hub notifications | Notification access, enabled Hub app and excerpt opt-in |
| Word / Excel / PowerPoint | Individually selected saved files, bounded OOXML excerpts | Android file picker read grant |
| Notes / Tasks / Remind / Calculator | Latest saved note, next open task, last successful calculation | Local summary toggle; no notification access |

Configure in Launcher Settings → Active Frames → App integrations.
Live tiles is the overall display switch. Each source is labelled honestly: media,
notification, local or selected file. Summaries appear inside matching personal-profile
app cards, or separate actionable tiles when no matching recent card exists.
Calendar remains the existing live event summary. Work/private profile content is excluded.
The selected-file card opens the selected URI; it is not the current unsaved editor state.

FM Radio must expose a media session or notification text. No station/frequency is guessed,
no tuner/hardware API or system privilege is claimed. Choose the actual installed FM Radio
package by its label under radio/player sources. A private OEM tuner with no exposed data
cannot supply a summary. Physical Titan 2 compatibility needs confirmation.

Files: choose one per Office type. DOCX document text, XLSX shared strings/first-sheet text,
and PPTX first-slide text plus sheet/slide counts are supported. Formula calculation,
formatting, macros, passwords, unsaved edits, legacy binary parsing and Microsoft account
sign-in are not implemented. Unsupported/oversized/inaccessible files show a fallback.
Only explicit content URIs are retained; Remove releases unused grants. Excerpts stay in memory,
refresh within 30 seconds, and are cleared from display on access failure. Archive reads are
bounded by entries, per-part size and total decompressed bytes; nothing is extracted to disk.
Document types/external entities are rejected. Other integrations refresh on Home's existing
five-second resumed lifecycle loop. No summaries are supplied while the device is locked.

## Titan 2 acceptance

1. Confirm version 11, retained Tasks, Hub choices, wallpaper and prior capture controls.
2. Confirm snapshot wording and Icon size label match the selected BB7/BB10 style.
3. With notification access, test music, podcast/audiobook playback and pause/track changes.
4. Select installed FM Radio in radio/player sources, play a station, return Home. Check the
   notification-derived detail matches the app's exposed text. Unselect source/revoke access:
   no radio text remains after refresh. Private/other-user notifications must not appear.
5. Enable Hub app and excerpt opt-in; post mail/message. Verify sender/text in frame. Mark read,
   snooze, remove notification, disable excerpt or source and verify removal after refresh.
6. Change selected Calendar IDs, grant/revoke Calendar access and verify the existing summary.
7. Pick a DOCX, XLSX and PPTX; verify bounded excerpts and open-selected-file action. Edit/save,
   return Home and wait 30 seconds. Remove/revoke/delete file: no old excerpt survives refresh.
   Cancel picker, rotate while picker is open, and test an unsupported/large/protected file.
8. Save a note, create a task/reminder, calculate a result. Verify separate local tiles and correct
   open actions, including with notification access disabled. Disable local summaries to hide.
9. Check 4/8/12 limits, scrolling, keyboard actions, open-apps off, lifecycle and capture regressions.

Automated checks and exact-commit CI evidence are recorded in the PR. No physical FM tuner,
Microsoft Office UI, account/cloud or Titan 2 hardware qualification is implied by emulator tests.

## Version 12 repair: opened-app window

The app grid now contains at most eight app frames, created only by actual openings in the
new frame session. The ninth distinct app replaces the oldest retained frame. Reopening an
app moves its existing frame to the newest position; it never duplicates that app. Evicted or
closed entries cannot be recreated by repeatedly reading old usage history. Clear app frames
or reboot starts a fresh window; process recreation preserves the current window. Version 12
starts fresh rather than importing version 11's twelve-hour history.

Calendar, notifications, selected files and local records enrich existing app frames; they
never create cards by themselves. Media uses that same rule. The consented shared-screen
preview is displayed separately, outside the app grid, and does not consume one of eight slots.
TenSeven Notes/Tasks/Calculator are tracked by their individual activity identities.

An unlinked Word/Excel/PowerPoint frame now explains that a saved file must be chosen and
provides a Choose file action. This opens Android's picker directly. The normal card-open
action remains available. Word is not a private-editor screen API: unsaved editor contents
still cannot be read automatically. A real Android content-provider test verifies the selected
DOCX loader; MIME type can identify OOXML when the provider omits its filename extension.
The integration Activity now draws a full themed Surface matching Launcher Settings.

Titan 2: after update, open nine apps in sequence. Only apps 2–9 should remain. Close one:
no old app should refill its place. Notifications or calendar changes must not add entries.
Reopen the closed app and verify it returns. Clear frames and confirm an empty grid until
another app is opened. Reboot/recreate the Activity and verify the documented window behavior.
Open Word, use Choose file, pick a saved DOCX and return Home. Confirm the selected-file
summary and themed setup screen. Media metadata and capture Stop controls must still work.


## Version 13: no file links; BB10 and notification-first setup

- Word, Excel and PowerPoint are ordinary opened-app frames. There is no file picker,
  linked-file preview, document parser, Choose file action or selected-file setting.
- Upgrade forgets legacy Office links and releases their retained read grants. It never
  deletes documents or clears media, Hub, Notes, Tasks or Calculator choices/data.
- Active Frames setup is absent from the style step. It appears after granted notification
  access in the wizard/overview, and after Hub in Launcher Settings. It is shown only for
  BB10 Home with notification access granted. Basic BB10 app frames still work without
  notification access; that access is needed for media and notification-derived summaries.
- BB7 Home cannot enter the frame grid through a Recents/SHOW_ACTIVE_FRAMES action or
  by swiping through a BB10 Hub detour. The integration screen also guards direct reentry.
- The last-eight-app window, ninth-app eviction and dismissal semantics are retained.

Titan 2: upgrade with linked Office files and confirm links/settings/previews are gone while
files still exist. Test BB10 with access denied/granted/revoked in first-run and reopened
setup; frame controls must follow the granted notification section and disappear on denial.
Switch BB10 to BB7 in Launcher Settings and setup: frame settings and the frame grid must
be absent. Invoke the prior Recents route and verify BB7 stays on BB7 Home. Switch back to
BB10 and verify normal eight-frame behavior and media/Calendar/Hub/local summaries.
