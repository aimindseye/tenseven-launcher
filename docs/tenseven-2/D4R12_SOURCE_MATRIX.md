# D4r12 source boundaries

| File | Change | Persistence / invariants |
|---|---|---|
| `LauncherSettingsActivity.kt` | Sticky bottom Back/Next for seven pages and Themes, flow-aware Hub navigation, overlay access row in Permissions, restored Minimum/Full/Custom app picker | Uses existing `ExtraAppsControl` and D1 `Setup2Plan` presets; never installs other APKs |
| `HubSettingsActivity.kt` | Sticky Back/Next and origin-aware return to Appearance & Home or Lockscreen Settings | Hub content and notification-source state unchanged |
| `SettingsSections.kt` | Remove overlay-access Card from `LockLayerSection` | Keep `overlayAllowed` for readiness and don't bypass Android permission enforcement |
| `model/Settings2Flow.kt` | Pure seven-page previous/next map | No storage or migration |
| `model/Settings2FlowTest.kt` | Page order and transition tests | JVM unit tests |

## Protected files

No files inside `frames/`, Active Frames implementation, media integration, signing, AndroidManifest, permission manifests, lock layer runtime or other launcher runtime are modified.

The SYSTEM_ALERT_WINDOW declaration already exists; permission is requested only through Android's system overlay Settings screen. Existing Hub Settings and onboarding entry points are preserved.

## Security and migration

- No permission can be self-granted by TenSeven.
- No new persistent preference key: the existing `ExtraAppsControl` component-enabled state is authoritative.
- Minimum is the original D1 Essentials set: calculator, tasks, notes.
- Full is D1 `Setup2Plan.full()` i.e., all currently bundled `ExtraApps.ALL` components.
- Custom is inferred from a selection not exactly equal to Minimum or Full, and remains configurable.
- Previous enabled bundled app choices remain until the user taps a preset or a switch.
- The new footer is inside the safe drawing region and outside the scrollable content.

## Validation limits

Source-anchor contracts, isolated transactional Git-worktree tests, `git diff --check`, and pure Kotlin flow compilation may run offline. Only the user's Mac Gradle tasks and Titan 2 can establish Android compilation and functional acceptance.
