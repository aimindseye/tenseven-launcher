# TenSeven 2.0 D1.1 — Settings correction acceptance

## Scope

- Launcher Settings > Hub & notifications goes **directly** to HubSettingsActivity; Back returns to the Launcher Settings categories.
- The intermediate Hub category is removed from the Settings path. Existing Hub Settings functions, filters and D0 audit remain intact.
- A Settings-only palette uses BlackBerry black/white/blue defaults with blue outlines; if the user selected another theme, that accent remains respected.
- A consistent Settings theme applies to Launcher Settings, Hub Settings, Setup, Frame Integrations, Home Style, Backup and Device Compatibility screens through LauncherTheme. No Active Frames implementation files or saved settings are changed.
- Appearance presents BB10 default, follow-phone and expandable optional existing themes, wallpapers and icons. No theme or wallpaper asset removed.
- The Active Frames category describes the BB10 + Notification access prerequisite, shows links for the missing prerequisites, and reveals existing FrameSettingsSection (which includes Configure app summaries) when permitted.
- Setup explains that active media-session details work when access is granted; FM radio/player notification sources and message excerpts require explicit opt-in via Configure app summaries.

## Titan 2 acceptance

1. Confirm BB10 Home selected and default appearance: Launcher Settings root, Home, Appearance, Lock, Apps, Keyboard, Permissions and Advanced have dark backgrounds, legible white text and blue outlined controls. The controls should no longer show legacy cyan-filled emphasis for new Setup primary buttons.
2. From Launcher Settings root select Hub & notifications. **Hub Settings opens immediately**, without an intermediate page. Press the Android Back key once. It returns to the root Launcher Settings list.
3. Hub Settings still exposes Automatic sources, D0 audit and Calendar selections. Test WhatsApp, email and silent notifications unchanged.
4. Switch Home to BB7. Open Launcher Settings > Active Frames. It explains that BB10 is required and offers Choose BB10 Home; no frame settings or media selections are deleted.
5. Choose BB10; with Notification access granted, Launcher Settings > Active Frames shows Configure app summaries, Screen Capture and existing frame switches. Existing radio/player choices and message excerpt consent remain saved.
6. Test optional themes: use Follow phone colors, custom theme or another preset; Settings should respect the chosen palette. Choose BlackBerry 10 · recommended to restore BB10 black/white/blue. Existing wallpapers remain available in optional customization.
7. Reopen Setup and inspect Appearance, Hub & Lock, Permissions and Review; they explain media configuration. Existing extras, permissions and settings remain saved.
8. Active Frames recent app cards, media sessions and notification summaries still work. D1.1 does not make arbitrary third-party live app previews possible.

## Gates

Run `./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease`, release signature verifier, frame-change guard and `git diff --check` before installing.

This correction ZIP is an **overlay for the already-applied D1 branch**. No GitHub PR or merge is included. Do not apply it directly on main.
