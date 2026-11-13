# D1 — Launcher Setup 2.0 + Settings 2.0 acceptance

## Scope

- Settings landing screen with Appearance, Home, Hub, Lock, Apps, Keyboard, Permissions, Active Frames and Advanced categories. Every baseline setting remains reachable via its existing control.
- First-run or reopened Setup is reorganized into seven stages, still using the same Home, theme, grid, wallpaper, Hub, lock and permissions functions. A targeted Setup deep link continues to show the preexisting one-step workflow.
- Apps & Features offers Essentials (Calculator, Tasks, Notes), Full (currently bundled extras), or individual toggles. The choices are saved through ExtraAppsControl, not a parallel preference key.
- Calendar and Phone are clearly labeled as D3/D4 upcoming code ports. D1 does NOT add fake Calendar/Phone launcher entries, APK modules, dialer roles, or privilege requests.
- Existing user themes, icon packs, wallpapers, Home style, Hub filters, notification permissions, Tasks/Remind and Active Frames preferences must survive in-place install.
- No code in Active Frames, media/capture, frame settings or task tracking is changed. The existing FrameSettingsSection is still presented unmodified from its own Settings category.

## Mandatory local checks

1. Apply on a clean review branch descended from D0 PR #126 head, never directly to main.
2. Run D1's dry-run and apply scripts; inspect diff; run `python3 tools/tenseven-d0-frames-guard.py`.
3. Run `:app:testDebugUnitTest`, `:app:assembleRelease`, `:app:lintRelease` and the registered certificate verifier.
4. Install on Titan 2 with `adb install -r`; do not uninstall or clear data.
5. Open Launcher Settings: verify all nine categories are visible, Back returns to category list, and existing values remain unchanged.
6. Change Appearance/Wallpaper/Icon presentation and verify Hub/Peek and Home follow the saved setting. Switch BB7/BB10 Home and confirm settings separation.
7. Reopen Setup: verify exactly seven stages, Back/Next, real Home role and restricted-settings guidance, one-step notification deep link and final review summary.
8. Select Essentials, Full and then a custom set; verify the correct currently shipped apps appear/hide under All Apps, with no Calendar/Phone entry in D1.
9. Verify WhatsApp, Gmail, silent notification counts, Hub D0 clipboard audit, Peek/lock and current Active Frames functionality still work.
10. Take before/after screens for Settings landing, Appearance, Apps, Permission stage and Setup final review. Report any CI formatter changes separately without touching frame files.

## Non-goals

D1 does not address the intermittent missing WhatsApp defect (D2), add a standalone Calendar (D3), add a standalone Phone or floating dialpad (D4), change release identity/signature, or import broken SableOS 2.0 Active Frames.
