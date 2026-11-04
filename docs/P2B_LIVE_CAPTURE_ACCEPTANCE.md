# P2-B live summaries and user-approved capture

> Historical acceptance record for the build named below. Current release behavior and setup:
> [1.0.0 user guide](USER_GUIDE.md); current delivery state: [P2/P3 status](P2_P3_STATUS_20261103.md).

Version 10 / 0.2.0-p2b-frames.2. Follow-up to #99 and PR #109.

Calendar and media summaries now appear inside matching personal-profile recent-app cards.
They no longer require Glance widgets or unused grid slots. Calendar uses Hub's selected
CalendarProvider calendars; a running calendar app alone does not create events. Media
uses the actual active session package, including YT Music, with notification-listener access.
Home refreshes these summaries every five seconds while resumed. Live tiles off hides them.

Launcher Settings → Active Frames → User-approved screen capture starts Android's
consent flow after an explanation. Choose one app where Android offers that choice, then
open the shared app, wait at least one second and return Home. A separate Shared screen
card shows the last captured preview. This is one consented shared region, not automatic
Recents snapshots or a per-app/profile screenshot cache. Entire-screen sharing can include
any visible content while away from Home. Home visibility freezes the last preview to
avoid a recursive mirror. Close that card, Stop in settings, the notification Stop action,
Android's sharing controls or locking the phone ends sharing and clears all images.
A fresh session always needs fresh consent; neither reboot nor process recreation resumes
capture. No audio, files, network or persistent screenshot storage is used. Protected content
remains subject to Android restrictions. Thumbnails are bounded to 640 pixels and one update
per second. Capture uses a foreground notification and a non-exported service/activity.

## Titan 2 acceptance

1. Update with the registered signing key; confirm version 10 and retained P2-A data.
2. Enable Calendar access; select calendars in Hub and create a current/upcoming event.
   Open Calendar and return Home: its card shows the event, even with four recent cards.
   No upcoming events shows the empty-state text rather than pretending an app is live.
3. With notification access, play YT Music. Open it and return Home: its card shows track
   and artist within five seconds. Pause/change track while Home is visible: summary updates.
   A different music app or work-profile copy must not receive YT Music's summary.
4. Disable Live tiles or revoke access; return Home and confirm summary removal. Widgets
   selections must have no effect on Active Frames summaries.
5. Start screen capture, cancel Android consent: no sharing service/preview starts.
6. Approve single-app sharing where available; open the selected app, wait, return Home.
   Confirm a Shared screen preview, stable while Home is visible, and the notification.
7. Rotate the shared app; return Home. Confirm bounded preview and no session-token reuse.
8. Test each Stop control and screen lock: images clear, notification disappears, session
   cannot resume without another Android consent. Restart launcher and repeat fresh consent.
9. Check protected app screens stay blank/redacted as Android determines. Check whole-screen
   mode is clearly shared-screen content and never attributed to a recent app or profile.

Automated checks cover package/profile/permission summary selection, thumbnail dimensions,
Home/lock/throttle decisions, memory clearing and late pixels after stop, grid limits and
capture close control. Real Android consent, screen rotation, revocation and Titan 2 behavior
still require the manual checks above. Exact-commit CI results are recorded separately.
