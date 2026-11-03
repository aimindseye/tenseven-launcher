# P2-C app summaries

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
