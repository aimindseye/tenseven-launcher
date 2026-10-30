# Porting plan: MinkLauncher pieces into Tenseven

Each row is one unit: port it, its tests and its attribution header together, in one pull request, with
the package renamed to `io.github.aimindseye.tenseven.<area>` and Mink's stores replaced by Tenseven's
(`StartStateRepository` for launcher state). Status: row 1 is partly ported (data layer and unread counts); everything else is not. This snapshot is the starting point.

Tenseven's own rule for every row: **nothing leaves the phone, nothing is kept that the feature does not
need**, as in Mink's PRIVACY.md.

| # | Mink code | What it gives Tenseven | Tenseven target | Needs on the phone | Tests to bring |
|---|---|---|---|---|---|
| 1 | `features/conversations/` (`NotificationHub`, `MinkNotificationListenerService`) | The Hub page without Sable Hub: message and email notifications grouped per person across apps, newest first, inline reply where the app offers `RemoteInput` | `app/.../platform/ConversationsRepository`, feeding `HubBar` and the Hub page | Notification access. On a GitHub or F-Droid install Android 13+ blocks it until **Allow restricted settings** is chosen in App info; onboarding must say so | `NotificationHubTest` (ported in part as `ConversationHubTest`; demo-data cases dropped) |
| 2 | `features/wellbeing/` (`UsageInsights`, `UsageTimelineAnalysis`) | The usage-stats queries only: which apps ran recently, in what order. Drives Active Frames as a recent-apps strip (icons, no live thumbnails) where task access is unavailable | `app/.../platform/RecentTasksRepository` fallback path | Usage access. **Not ported:** daily limits, the app pause policy and Mink's Day. Tenseven does not ship a screen-time feature | `UsageInsightsTest` (trimmed to what is ported) |
| 3 | `features/privatespace/`, `features/workprofile/` | Change monitoring for Private Space, the OEM "private area" gateway for phones that have their own, work-profile classification. Compare with `LauncherAppsRepository` (which already handles Private Space) and take only what it lacks | `app/.../platform/` next to `LauncherAppsRepository` | Home role (`ACCESS_HIDDEN_PROFILES`) | none upstream; write tests for the classifier |
| 4 | `ui/onboarding/` | First run: choose the home app, then each optional permission with an honest explanation, with the restricted-settings steps | `app/.../ui/onboarding/`, replacing the Sable setup hand-off | none | none upstream; test the step order as a pure function |
| 5 | `platform/LockScreenAccessibilityService.kt` + `res/xml/lock_accessibility_service.xml` | Double-tap empty Home to lock, with an accessibility service that cannot read the screen or perform gestures | `app/.../platform/` and a Settings switch, off by default | Accessibility service enabled by the user. Review Google Play's accessibility policy before a Play release | none |
| 6 | `features/iconpacks/` | Nova and ADW-compatible icon packs, mapped locally; unsupported apps keep their own icon | Appearance setting, applied in `LauncherAppsRepository` icon loading | none | `IconPackMappingTest` |
| 7 | `features/backup/` | A versioned, readable JSON export and import of launcher settings. Portable fields only; permissions and grants are excluded and set up again | Settings > Backup | none | `LauncherBackupCodecTest` |
| 8 | `features/magic/` (`MagicBoxInput`, `QueryRouting`) | Prefix commands (`@` message, `#` call, `-` to-do, `/` note, `+` event, `?` app) and instant typing from Home. Extends SableStart's `CommandQuery` rather than replacing it | `app/.../model/CommandQuery.kt` | none for parsing; each action asks for its own permission | `MagicBoxInputTest`, `QueryRoutingTest` |

## Not borrowed, on purpose

Mink's Day, MinkSpace mini-apps, MinkPad (Bluetooth keyboard and mouse), the AI provider catalog, the
Virtual Contact Card, widget pages and the GitHub update check. They are good features that do not belong
in a keyboard-first launcher's first releases, and several (SMS sending, Bluetooth, internet) would
widen the permission list a great deal. The messaging and AI provider *icons* follow the policy in
[`docs/BRAND_ASSETS.md`](../../docs/BRAND_ASSETS.md), but Tenseven does not copy Mink's image files.

## Order

1 (Hub), 4 (onboarding), 2 (Active Frames fallback) first: they close the biggest gaps between the
SableOS build and an ordinary install. Then 8, 6, 7, 3, 5.
