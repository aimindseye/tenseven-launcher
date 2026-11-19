# ADR-0002: Games maintained outside TenSeven Launcher

- **Status:** Product scope approved by the owner on 2026-11-13; R2-G implementation remains HOLD FOR MERGE pending independent review, CI and device acceptance.
- **Parent source:** Qualified R2 `632fb38c606477272e477d246c7ba361738baf18`.
- **Supersedes:** Games-only portions of ADR-0001, the companion roadmap, distribution contract and acceptance checklist. Other companion decisions remain in force.

The ten games (Sudoku, Minesweeper, 2048, Bricks, Snake, Blocks, Reversi, Solitaire, Mahjong and Words) belong in a separate repository with a separate installer. Launcher contains no bundled game implementation, game activities, game tests, Games setup category or Games Pack companion entry. This change does not create or publish the new repository, installer, package identity, signing identity or migration protocol.

Phone, Calendar, Tasks, Notes, Calc Plus and Sound Profiles remain embedded optional tools with their existing Minimum, Full and individual setup choices. Minimum remains Calculator, Tasks and Notes. Full selects all six tools. Existing third-party apps, including games, remain discoverable and launchable through the unchanged Android LauncherApps path. External Sable game icon mappings remain used by that path; shared icon assets and third-party attribution are retained.

## Data and shortcut audit before removal

- The removed screens keep boards, scores, cursor, undo and pause state in Compose `remember` values. They do not write game-specific preferences, files or databases and do not use `rememberSaveable`. No persisted game records were found in this source; this does not assert anything about unrelated apps or unknown historical binaries.
- Bundled app visibility uses Android PackageManager component-enabled settings through ExtraAppsControl. Removing manifest components makes their old overrides obsolete. No preference clearing, database deletion, file deletion, reset or migration runs on upgrade.
- Home/grid/favorite/quick-bar and hold-letter stores remain unchanged. Live app resolution already ignores unavailable entries; letter launching catches missing targets and returns false. Stale game keys are retained for rollback and are never redirected to an unverified external package. Users can change shortcuts normally. Device upgrade acceptance must check existing pinned entries and keyboard shortcuts.
- The companion wizard's existing normalization ignores obsolete `games` IDs, preserving the six valid tool IDs. Reading selection does not eagerly rewrite its store; an explicit user Save writes the normalized choice. There is no automatic APK installation or migration.
- Tests verify six-tool presets, obsolete selection handling, unchanged saved input, shortcut round trips, unavailable grid targets, manifest entry points and Notes sharing.

## Source history and rollback

The parent commit preserves all 21 game source and five game test files under `app/src/{main,test}/java/io/github/aimindseye/tenseven/extras/games/`, including original provenance comments. The removal is a descendant commit; history is not rewritten or squashed. The separate Games repository can import those exact paths and their Git history from the parent and earlier commits, retaining Apache/SableOS attribution. No source ZIP or copied distribution is a substitute for that history.

Launcher retains all existing stored user settings. Rollback requires an explicitly approved source/build rollback with the same existing package/signing identity and compatible versioning, followed by device acceptance; no downgrade, APK install or data purge is performed here. Game process state was already transient and cannot be migrated after process death.

## Qualification boundaries

Keep every analyzer, accepted-version-15 P2 baseline, coverage exclusion, quality threshold, project identity and scanner policy unchanged. Report actual APK-size and Kover coverage changes, including loss of coverage from removed tested game code. Do not claim coverage restoration by removing source from analysis. Remaining Calendar/Phone coverage debt, security issues, main-relative findings and suppression reviews are separate integration holds. Local tests do not establish OEM upgrade, device, Active Frames instrumentation or remote scanner acceptance.
