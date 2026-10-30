# MinkLauncher OpenSource: reference snapshot

| | |
|---|---|
| Upstream | https://github.com/katoaapps/openminilaunch (MinkLauncher OpenSource) |
| Commit | `b9b26f733e1eb2e26dca1c7f816766332c6252ce` ("2026-10-28. MT. Prepare 3.1.1 release", version 3.1.1) |
| Licence | Apache License 2.0 (`LICENSE` in this folder, copied unchanged) |
| Notice | `NOTICE` in this folder, copied unchanged. It also says the MinkLauncher name and branding are **not** licensed for unrestricted use: Tenseven uses none of Mink's name, mascot, artwork or provider icons. |
| Imported | 2026-10-29, byte for byte, nothing edited |

## What this folder is

A **read-only snapshot** of the parts of MinkLauncher OpenSource that Tenseven plans to port. It is
**not compiled**, not linted and not scanned by SonarCloud (`sonar-project.properties` excludes
`third_party/`). Its packages (`com.katoaapps.openminilaunch.*`) depend on Mink's own stores and models,
so they do not build on their own; each one is ported into `app/` as a unit, with its tests, following
[`PORTING.md`](PORTING.md).

## Why a snapshot and not a fork

Tenseven's home screen comes from SableStart (BlackBerry 10 and BlackBerry 7 layouts); Mink's is a
to-do panel with a command box. Forking Mink would mean deleting most of it. Taking only the pieces
that fill Tenseven's gaps keeps the diff reviewable, and the pinned commit says exactly which Mink
code each ported file came from.

## Files

| Path | Lines of Kotlin: what it does | Ported to |
|---|---|---|
| `features/conversations/` | 635: notification listener, conversation grouping, inline reply | see PORTING.md |
| `features/wellbeing/` | 511: usage-stats insight and timeline analysis, app pause policy | see PORTING.md |
| `features/privatespace/`, `features/workprofile/` | 442: Private Space and work-profile discovery | see PORTING.md |
| `features/iconpacks/` | 346: Nova and ADW icon-pack discovery and mapping | see PORTING.md |
| `features/backup/` | 659: versioned JSON backup and restore | see PORTING.md |
| `features/magic/` | 86: Magic Box input rules and prefix query routing | see PORTING.md |
| `ui/onboarding/` | 821: first-run permission flow | see PORTING.md |
| `platform/LockScreenAccessibilityService.kt`, `src/main/res/xml/lock_accessibility_service.xml` | double-tap to lock | see PORTING.md |
| `src/test/` | the unit tests of the packages above | ported with them |

## Licence obligations when porting (Apache-2.0, section 4)

1. Keep `LICENSE` and `NOTICE` here, and the Mink entry in the repository's `NOTICE` and `THIRD_PARTY_NOTICES.md`.
2. A ported file carries a prominent note that it was changed, and where it came from. Use this header:

   ```kotlin
   // Adapted from MinkLauncher OpenSource (katoaapps/openminilaunch @ b9b26f7), Apache-2.0.
   // Changed for Tenseven: <what changed>.
   ```
3. Do not carry over Mink's name, mascot, "Mink" identifiers in user-visible text, or any of its artwork.
