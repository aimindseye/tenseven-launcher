# TenSeven 2.0 — D4r12 Settings Navigation, Lock Permission & Apps

Baseline: cumulative D1–D4r11-r4 review worktree, approved Image 1 seven-section Settings. Target: Titan 2. Q25 requires a later physical layout pass.

## Screen-by-screen checklist

Record `PASS`, `FAIL` or `NOT TESTED` plus screenshot and build SHA for each item.

### 1. Settings landing
- [ ] 01.1 Seven categories remain in order: Permissions, Appearance & Home, Hub, Lockscreen, Active Frames, Apps & Features, Review & Finish.
- [ ] 01.2 A bottom Back button exits Settings without resetting preferences.
- [ ] 01.3 Bottom Next opens Permissions, not a legacy Setup screen.
- [ ] 01.4 The footer remains visible without scrolling at Large text size and Titan 2 display scale.

### 2. Permissions
- [ ] 02.1 New **Display over other apps** row appears after Notifications, ahead of Weather location.
- [ ] 02.2 Status reflects actual Android setting: Allowed or Not allowed.
- [ ] 02.3 Selecting the row launches Android's system overlay-access screen for TenSeven where supported.
- [ ] 02.4 Denying access does not block Home, Hub, or exiting Setup.
- [ ] 02.5 After returning from Android settings, the Permissions page remains selected and status refreshes.
- [ ] 02.6 Bottom Back returns to Settings landing; Next opens Appearance & Home.
- [ ] 02.7 Original Contacts, Photos, Usage, Notifications and Weather permissions still work.

### 3. Appearance & Home
- [ ] 03.1 Existing BB7/BB10 selection, shared Dials/Text/Hidden, metrics and font controls remain intact.
- [ ] 03.2 Other Themes, Wallpapers remains selectable and retains the existing Back behavior.
- [ ] 03.3 Bottom Back returns to Permissions; Next opens Hub Settings.
- [ ] 03.4 A returning user retains chosen wallpaper visibility, colors and fonts.

### 4. Hub Settings (separate Activity)
- [ ] 04.1 Hub Settings retains all source/calendar, diagnostic, notifications and compose controls.
- [ ] 04.2 Bottom Back returns to Appearance & Home when opened from Launcher Settings.
- [ ] 04.3 Bottom Next opens Lockscreen Settings in the existing Settings activity.
- [ ] 04.4 Direct Hub Settings opening from the Hub still has a working Back action (returns to Hub).
- [ ] 04.5 Right-swipe from Hub itself still opens the Hub Settings panel; no Home-page regression.

### 5. Lockscreen Settings
- [ ] 05.1 No **Display over other apps** grant button appears inside Lock Settings.
- [ ] 05.2 Lock-layer toggle and existing notification-access, preview, privacy controls remain.
- [ ] 05.3 When overlay permission is missing, the system does not silently bypass Android's requirement.
- [ ] 05.4 Bottom Back opens Hub Settings; Next opens Active Frames Settings.

### 6. Active Frames Settings
- [ ] 06.1 Existing media/source/capture preferences remain editable.
- [ ] 06.2 Bottom Back opens Lockscreen Settings; Next opens Apps & Features.
- [ ] 06.3 Active Frames implementation and saved settings are unchanged.

### 7. Apps & Features
- [ ] 07.1 **Minimum apps · Essentials** enables exactly Calc Plus, Tasks and Notes.
- [ ] 07.2 **Full apps** enables all currently bundled tools and games, not a separate Lite/Full APK.
- [ ] 07.3 All individual Tools/Games switches remain for Custom selection.
- [ ] 07.4 Toggling an individual app updates the derived Custom state and launcher visibility.
- [ ] 07.5 Upgrading existing settings does not change previously enabled app components until selected.
- [ ] 07.6 No unbundled Calendar/Phone app is promised or activated.
- [ ] 07.7 Bottom Back opens Active Frames; Next opens Review & Finish.

### 8. Review & Finish
- [ ] 08.1 Current selections remain visible.
- [ ] 08.2 Bottom Back returns to Apps & Features.
- [ ] 08.3 Bottom Finish opens TenSeven Home and marks onboarding complete.
- [ ] 08.4 All settings remain after force-close and relaunch.

### 9. Responsive/upgrade regression
- [ ] 09.1 Bottom buttons do not overlay controls or the keyboard on Titan 2.
- [ ] 09.2 Repeat with Android font scale Large / smallest usable window; scrolling remains available.
- [ ] 09.3 Existing BB7, BB10, Peek, lock, weather, Home status and notification behavior are preserved.
- [ ] 09.4 On a fresh install, wallpaper behind Home remains off by default.
- [ ] 09.5 Q25 planned: verify footer visible on 720×720 and with landscape virtual display profiles.

## Qualification
This package is an **offline source patch**, not a signed Android APK. Full Gradle/Kotlin compilation, release signature verification, Titan 2 checks, Q25 layout tests and PR/CI remain outstanding until executed on the user's Mac/devices.
