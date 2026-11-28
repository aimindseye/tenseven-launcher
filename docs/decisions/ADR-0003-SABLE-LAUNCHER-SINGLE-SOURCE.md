# ADR-0003: Sable Launcher is the single launcher source

- **Status:** Accepted by the owner and merged in PR #153 on 2026-11-27.
- **Supersedes:** the in-app tool parts of ADR-0001 and ADR-0002 (Minimum/Full tool presets), and the R1 quality gate (`docs/engineering/QUALITY_GATE_R1.md`).

## Decision

TenSeven Launcher and SableOS's Sable Launcher are built from one source tree. The `:app` and `:design` sources are the Sable 2.0 launcher (`sableos-project/sableos-android`, `apps/launcher`), held here as ordinary source. Nothing is patched on top of them. TenSeven keeps only what Sable does not have:

- **Install wizard.** The companion catalog, setup planner and Apps & Features status section.
- **Package identity.** The app ID `io.github.aimindseye.tenseven`, the signing setup and the companion package names. Package registration is unchanged.
- **`HubCountsProvider`.** Sable Watch reads Hub counts through it.
- **`:phone` and `:calendar` modules.** Their profile and theme support is a later step.

## What changed for users

| Area | Behaviour |
|---|---|
| Companion modes | **Base**: launcher only. **Lite**: Tasks, Notes and Calculator. **Full**: every companion, including Sable Watch discovery. The planner shows what to install; it does not install APKs. |
| Accent | Sable Blue (`#2E9BD6`) is the default accent. |
| Home status dials | A setting again. On for TenSeven, off for SableOS builds. |
| Weather | Requests go through the pinned HTTPS transport. |
| Root and platform signature | Not needed. Permissions that need a platform signature (for example `REAL_GET_TASKS` and `WRITE_SECURE_SETTINGS`), the self-granting home access, extra dim, the system menu and the lock-screen chooser are removed. |
| Removed with the swap | User-approved screen capture for Active Frames pictures, TenSeven's bundled extras, and Hub reminder changes made after the Sable fork. |

## Device profiles

`DeviceProfile` (`design/.../model/DeviceProfile.kt`, moved from `:app` so Phone and Calendar share it), modelled on Sable Reader and Sable Games, reads `Build.*` in one place:

| Profile | Navigation | Status |
|---|---|---|
| Zinwa Q25 | Trackpad gives arrow keys | Supported |
| Zinwa Q27 | Navigation row with trackpad key | Profile only, not verified on a device |
| Unihertz Titan 2 | No arrows: I J K L move | Supported, tested on hardware |
| Unihertz Titan 2 Elite | No arrows: I J K L move | Profile only, not verified on a device |

Zinwa P26, other keyboard phones and touchscreen phones fall back to generic profiles.

## Branding

The visible name comes from `@string/tenseven_app_name` (Kotlin: `appName()`), and the icon comes from `@mipmap/launcher_icon`. Strings that show the name take it as `%1$s`. SableOS builds add `distribution/sableos/identity.init.gradle`, which overlays the name, the icon (`ic_sable_home`), the status-dial default and the lock-service labels, and sets `org.sableos.launcher`. No SableOS setting lives in the Gradle files.

## CI

CI matches what Sable 2.0 runs for this code:

- **Android build:** unit tests, assemble, Lint, Detekt, ktlint and Kover.
- **Other checks:** static checks, CodeQL, Gitleaks, MobSF and Sonar.
- **Active Frames emulator:** API 34/35 and the square/large-text, tall-touch and landscape layouts. This workflow stays because the launcher has no root, so on-device behaviour has to be checked on an emulator before device testing.

The hash-pinned deliverable gate and the P2 findings ratchet are retired. They rejected the swap because of their own pins, not because of defects. The 34 structural `@Suppress` markers that came with the Sable source (TooManyFunctions, LongMethod, UNUSED_PARAMETER and similar) are accepted. The redundant `FunctionNaming` markers on composables were removed, because `config/detekt-tenseven.yml` already exempts `@Composable`.

## Follow-ups

- Bump Gradle to 9.9.3 and AGP 9.5.3. This clears 3 Lint version warnings and needs the Gradle 9.9.3 sha256.
- Phone and Calendar profile and theme support.
- Decide whether to restore screen capture for Active Frames pictures on non-ROM builds.
