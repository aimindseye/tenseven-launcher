# TenSeven 2.0 — D0 source compatibility audit

**Audit status:** Source assessment, paired with owner-confirmed Titan 2 testing.
**TenSeven baseline:** 50f8b1a6a5e22dfd9ac6189fc1301a421984808d (PR #125).
**SableOS reference:** a3300bd96f4cb7a70c980ec50191edff0907c8ca.

## Reuse matrix

| Area | Baseline / source | Decision |
|---|---|---|
| Hub | ConversationListenerService, HubSourceRepository, NotificationGroupSelection | D0 diagnostic per-app aggregate; D2 handling of intermittent missing chats and silent notifications |
| Setup / Settings | OnboardingActivity, LauncherSettingsActivity | D1 shared schema, presets, themes, permissions and migration |
| Calendar | HubCalendarRepository reads CalendarProvider; Sable calendar is a separate Compose app | D3 embed an optional launchable TenSeven Calendar inside the existing Full APK, removing Sable-only providers; calendar read/write consent independent |
| Phone | System dialer handoff; Sable Phone is a ROM-integrated default dialer with minSdk 36 | D4 optional TenSeven Phone with Calls, Favorites, Contacts and floating in-app dialpad; public API only; default-dialer role separately qualified |
| Active Frames | Current Titan 2 implementation works; SableOS 2.0 frames are unsuitable | Freeze implementation, presentation, task/media and capture integrations |
| Lock / Peek | Existing TenSeven Android-keyguard cover and notification rail | D2 refine redaction and per-source details; no ROM SystemUI bypass |

## Evidence and diagnostic boundaries

The Titan 2 Android shade once displayed two WhatsApp chats while Hub showed one; later messages appeared. The listener has no explicit sound filter. Conversion and group summary replacement can affect which rows reach Hub. D0 counts outcomes without retaining message text, senders, keys or contacts. One summary replaced by a usable child is normally correct. Silent candidates reflect Android ranking/channel hints only, not a complete DND report. Android may withhold items from listeners.

The owner reported 21 observed, 14 visible: five group summaries replaced and two other-profile items. No parse failures or user-filter exclusions; Active Frames remained functional.

## Non-goals

No D0 behavioral fix, new permission, signature change, version bump, app role request, or Active Frames source change.
