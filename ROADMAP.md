# Roadmap

## 1.0.0 release preparation

Core functionality has owner testing on Titan 2. P2 engineering (PR #111) and P3 engineering
(PR #112) are merged. Engineering completion is distinct from physical qualification.

- [x] Both home styles, Command, Hub, themes, wallpaper, optional lock layer and bundled extras
- [x] Last-eight-opened-app Active Frames with available summaries; no automatic history-only cards
- [x] Optional notification access, calendar selection, inline reply, snooze and reminders
- [x] P2 screen/lifecycle boundaries, capability handling and blocking quality ratchet
- [x] P3 responsive/device/keyboard policies, diagnostics and real widget-provider regression coverage
- [ ] Validate the final one-row Hub correction on the signed release build
- [ ] Refresh current user-guide screenshots with the existing capture script
- [ ] Build the signed 1.0.0 APK and publish versioned assets and checksum on GitHub

## Physical qualification

Targets: Titan 2, Titan 2 Slim, Zinwa Q25, Zinwa P26 and an ordinary touchscreen Android device.
KEY2 is excluded. Android 14 is the minimum; a named profile is not proof of hardware certification.
Record OS/build, screen/density/font settings, keyboard events, permissions, upgrade persistence,
widget bind/configure/cancel/resize and navigation results in [the qualification matrix](docs/P3_DEVICE_QUALIFICATION.md).

## Continued maintenance

- Reduce inherited lint/detekt/format findings without relaxing the no-new-findings gate.
- Enable global strict quality enforcement when the inherited baseline is clean.
- Generate and review dependency locks and verification metadata across supported build hosts.
- Continue incremental Activity/UI simplification and accessibility/layout testing.
- Qualify translations with fluent speakers; English remains the reference.
- Keep screenshot evidence and release instructions aligned with visible functionality.

## Boundaries

An ordinary launcher cannot read another app's private document or force-stop it. Media
summaries depend on the app's published session/notification data. Screen capture needs user
consent. Sable Keyboard is a separate companion project. No network permission is added.
