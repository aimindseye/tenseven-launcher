# P2-B Active Frames acceptance

Update: version 10 live-summary and consented capture acceptance supersedes the
version 9 live-tile/snapshot rows below; see [P2B_LIVE_CAPTURE_ACCEPTANCE.md](P2B_LIVE_CAPTURE_ACCEPTANCE.md).


Branch: `feature/p2b-active-frames-20261102`; PR #109. One Full APK,
versionCode **9**, versionName **0.2.0-p2b-frames.1**. Sign on the existing Mac
with the registered key using `bash tools/build-full-consolidated-test.sh`.
CI contains no signing secrets.

| Option | Ordinary Full APK | Authorized OS integration |
| --- | --- | --- |
| Live tiles | Enabled TenSeven Calendar / Now playing LIVE summaries, labelled Live tile | Same |
| New messages | Counts for opted-in Hub apps in the personal profile, with listener access | Same |
| Snapshots | Disabled with explanation; recent cards show icons | Preview only with detected task/snapshot grants and method support; secure restrictions retained |
| Open apps | Tap/Enter starts exact profile app; no cross-user fallback | Resume actual task when authorized, else exact app launch |
| Minimise into a frame | Records returned-app history; no control over other apps | Native tasks may also appear |
| Maximum | 4 / 8 / 12 total cards; two columns, two visible rows, scrolling | Same |
| Close / force-stop | X, Backspace or × dismisses card; force-stop disabled | Same safe card-close default |

Primary manual qualification device: **Titan 2**. No claim of Q25, KEY2,
Titan 2 Slim, touchscreen hardware, or privileged SableOS validation is made.

1. Update the accepted P2-A install with the same registered key, without uninstalling.
   Confirm version 9, theme/wallpaper, Hub opt-ins, Tasks and Remind persist.
2. With BB10 selected, open Launcher Settings and setup. Check Active Frames choices
   appear in both. Switch to BB7: frame choices disappear, BB7 settings remain.
3. Open apps through Launcher and external notifications. Return Home: cards merge,
   even when Android supplies an incomplete native list. With Usage access off,
   launcher-started cards remain; the access offer explains external history.
4. Change limits to 4, 8 and 12. Verify scrolling, 2-column layout, no clipped close
   controls; D-pad moves focus, Enter opens, X/Backspace closes, modifier keys do not
   accidentally close. After close, focus goes to the next/previous remaining card.
5. Close a card, restart Launcher (then reboot). It stays dismissed until explicitly
   launched again or a newer foreground event is observed. No other app is killed.
6. Toggle Open apps off: tap/Enter stops opening apps; ×/X still closes. Toggle
   Minimise into a frame off: synthetic recent cards disappear and new launches are
   not recorded. Reenable and verify normal recording resumes.
7. Enable Calendar/Now playing in existing Widgets settings with actual available
   data. Live tiles appear within remaining slots, explicitly labelled Live tile;
   toggle Live tiles off to hide them. No arbitrary app screenshot is simulated.
8. With notification access, New messages displays only opted-in personal-profile
   counts. Disable it or revoke access: counts disappear after returning Home.
9. Pause/lock/remove a work/private profile while cards exist. No preview survives
   the invalidation, and opening a stale card never starts the personal-profile copy.
10. Ordinary install: Snapshots and Force-stop are disabled with explanations.
    Authorized OS bridge testing is separate: verify real vs absent/protected
    snapshots, permission loss, safe fallback, and bounded image lifecycle.

Automated evidence is linked in PR #109 after the exact-head workflows complete.
Domain tests simulate process recreation and lifecycle races; Android instrumented
checks exercise persistent settings, Activity recreation, keyboard callbacks and
2×2 layout at 720×720. Existing Widgets page tests/physical binding qualification
are not replaced by the read-only live-summary tests.
