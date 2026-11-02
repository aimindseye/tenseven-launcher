# P2-A foundation — adaptive Hub and optional TenSeven icon styling

This branch starts P2-A on top of P1.2 (#102). It is **not** a completed Tasks/Remind release.

## Adaptive Hub + Peek

- Use the user's Launcher appearance (including Phone/Material You) for Hub and Peek.
- Do not replace the user's palette. Only repair unreadable foreground contrast where the selected surface and text fail a 4.5:1 ratio. Conversation rows use a near-opaque theme-colored surface so arbitrary wallpaper colours cannot obscure messages.
- Verify sender names, preview text, time and counts in Hub and Peek on Q25 with vivid/light/dark wallpapers, custom theme, phone Light/Dark and BlackBerry theme. Preserve privacy, keyboard focus and accessibility.

## Icons

- Normal installed application icons are the default, without Tabler replacements.
- External installed Nova/ADW icon packs keep their existing selection and take precedence over launcher glyphs for other apps.
- Launcher Settings > Grid offers a separate **Use TenSeven icon styling** switch. Bundled TenSeven extra apps retain their own Tabler icons independently of this switch.
- No automatic icon-pack installation, forced app replacements, or extra APK. Preference is included in the launcher backup.

## P2-A architecture foundation

- `tasks/TaskRecord.kt`: local typed tasks, with independent due/reminder times, priority, recurrence, tags and optional linked Note ID; no Android Calendar or Google services.
- `tasks/TaskStore.kt`: versioned, app-private per-task files with atomic replacement; does not rewrite legacy Notes files or retain notification message content.
- `platform/PlatformCapabilities.kt`: centralized ready/permission/unavailable contract; normal Full APK never claims privileged third-party app snapshot access.
- Unit tests: contrast, icon precedence, task CRUD/roundtrip, DST times and capability fallbacks.

## P2-A release blockers (not yet implemented)

- [ ] Tasks list/editor with optional entry, Notes link, and Hub Remind shared repository.
- [ ] Background scheduling, boot/reboot recovery, in-app due items, optional system notifications and recurrence.
- [ ] User-consented notification capture with private storage, redaction, expiration and delete controls; never persist third-party PendingIntent.
- [ ] Extract Hub/Notes presentation from `SableStartScreen.kt`, reduce Activity orchestration, add lifecycle/Compose UI tests.
- [ ] Pass Android, CodeQL, Security and Sonar quality gates on exact integrated head. Baseline Sonar validation PR #101 failed; do not weaken or blanket-suppress rules.
- [ ] Complete Q25 hardware keyboard and ordinary touchscreen tests; keep one Mac-signed Full APK.

Build identity for **foundation testing only**: versionCode 6, versionName `0.2.0-p2a-foundation.1`. Mac helper: `bash tools/build-full-consolidated-test.sh`.
