# D2 — Hub, Peek and Lock Integration (Titan 2)

Based on the approved 18-page *TenSeven-BB10-Titan2-Q25-Design-Review* PDF:

- Pages 3–5: flat Hub rows, rightward Hub Settings panel and selected-message action pane.
- Pages 6–10: app icon rail, per-app lock detail and authentication-respecting redaction.
- The reference PDF **does not** depict a Launcher Settings landing-page design. The D2 root grey border reflects explicit November 7 owner feedback; blue remains the focus/selected control color in subpages.

## D2 source changes

1. Launcher Settings root categories use subtle neutral outlines only; detail settings retain the BB10 blue accent and custom themes.
2. Direct Hub route from Settings (including Permissions subsection) marks its caller. Hub Settings displays **Back to Launcher Settings** and the Android back action returns to the original Settings activity. Hub's in-page swipe panel is unchanged.
3. Selected notification panel displays sender/conversation title and a short preview only when unlocked. Provider-defined Reply/Archive/etc. actions are unchanged and no selected item is automatically marked read.
4. Regression tests cover simultaneous WhatsApp chats vs summary, grouped-summary fallback, silent candidate inclusion and lock privacy. The existing notification listener, Hub source policy, on-device Peek and lock-rendering code are intentionally preserved.
5. All Active Frames implementation, integration, capture and media-source preference files are explicitly frozen.

## Titan 2 acceptance (perform with Notification access and BB10 Home)

- [ ] Open Launcher Settings: light grey, thin root category borders; no blue rectangle around each category when unfocused.
- [ ] Open Appearance and another inner settings page: white on black with blue focused/selected controls; custom theme remains honored.
- [ ] Tap Hub & notifications from Launcher Settings: opens Hub Settings **directly**.
- [ ] **Back to Launcher Settings** appears at top, tap returns to Launcher Settings root; physical/system Back also returns.
- [ ] Tap Permissions & integration > Hub settings: same direct navigation and Back.
- [ ] Open Hub: left-edge right swipe shows the original inline panel; close with Back/left swipe.
- [ ] Select one WhatsApp message: right pane includes selected title, excerpt (when unlocked), Open app and provider-supplied actions; selection alone does not mark read.
- [ ] Test two WhatsApp chats with a group summary: both child notifications remain visible.
- [ ] Test silent Gmail and WhatsApp channels: Android-delivered entries still appear; do not change device notification sound settings.
- [ ] Test source provider without reply: no invented Reply button.
- [ ] Lock/PIN: only per-app count appears while locked when preview disallowed; selecting a rail icon shows redaction (or details only when Android permits).
- [ ] Verify snooze, reminders, calendar Peek, active frames media summaries, and previously opted-in media apps remain unaffected.
- [ ] Run `python3 tools/tenseven-d0-frames-guard.py` and `./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease`; verify signed APK prior to install.

### Known limits

Android's listener delivers a current *snapshot*, not all previous messages or the entire Android notification shade. Group summaries replaced by converted children are deliberately not duplicated. A muted channel is not an exclusion reason. Lock screen remains a launcher cover; it cannot bypass SystemUI authentication.

This is a **D2 integration and regression deliverable**, not the standalone Calendar (D3) or Phone (D4) applications. No new permissions, signers, app IDs, version codes, or privileged task APIs are introduced.
