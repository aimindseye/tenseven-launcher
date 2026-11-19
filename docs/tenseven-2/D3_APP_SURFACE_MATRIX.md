# D3 source / surface inventory

| Surface | D3 source change | What it does not change |
|---|---|---|
| Launcher Settings | `LauncherSettingsActivity.kt`, `SettingsSections.kt`, `Settings2VisualTheme.kt` | Category storage, Android permissions, wallpapers, optional apps |
| Hub Settings | `HubSettingsActivity.kt` | Automatic notification listener, provider actions |
| Hub selected message | `ui/HubPanels.kt` | Conversation sources, read state, provider `PendingIntent` dispatch |
| Notes / Notes Tasks | `extras/notes/MainActivity.kt` with `TenSeven2AppTheme` | Notes controller, database, editor behavior, keyboard navigation |
| Tasks | `tasks/TasksActivity.kt` with `TenSeven2AppTheme` | Task store, alarm scheduling, reminders |
| Calculator + Converter | `extras/calculator/MainActivity.kt` with `TenSeven2AppTheme` | Arithmetic/native qualification, conversion code, key mappings |
| Sudoku + nine Games | `extras/games/MainActivity.kt` with `TenSeven2AppTheme` | Every game engine, board logic, hardware-key handling |
| Sound Profiles | `extras/profiles/MainActivity.kt` focus/row treatment | Audio mode changes, persistence, system permission behavior |
| Active Frames | **NO CHANGES** | Protected by existing `tools/tenseven-d0-frames-guard.py` |

PDF pages 3–5 establish style and Hub action structure. There are no approved PDF screenshots of the utility apps; visual compliance is judged by consistent tokens and owner screenshots. This deliverable changes shared appearance scope and selected source entry points, not their storage or business logic.
