# Contributing

Thanks for helping. Tenseven is small and early, so the most useful contributions are: a device's key profile
captured on real hardware, a fix the first CI runs point to, and a Mink port from `third_party/minklauncher/PORTING.md`.

## Workflow

1. Branch from `main`; one change per pull request.
2. `bash tests/run.sh` and `./gradlew :design:testDebugUnitTest :app:testDebugUnitTest` before you push.
3. The CI workflows run on the pull request. A pull request that touches only docs skips the build.
4. Keep decisions in pure Kotlin under `model/` and `privacy/` with a unit test, and Android calls in `platform/`.

## TenSeven 2.0 modular-app workstream

The accepted source of truth is [ADR-0001](docs/decisions/ADR-0001-MODULAR-COMPANION-APKS.md) and its [roadmap](docs/2.0/MODULARIZATION_ROADMAP.md). PR #127 is a historical recovery checkpoint; **do not reopen or merge it** to implement modularization. The replacement draft integration PR is based on branch `architecture/tenseven-2.0-modular-companions-20261112`, rooted at the preserved PR127 head.

For modularization work, use small feature branches based on this integration branch and target it with separate, reviewable PRs. The current `main`-based rule above applies to unrelated maintenance. Keep the legacy `:app` launcher ID, user data and existing UI functional until companion migration is verified. New modules must have independent build, static-analysis, signing/release and runtime acceptance, not simply move code out of Detekt scope. Do not weaken canonical P2 quality rules or touch protected Active Frames to make a feature PR pass. Include the [design checklist](docs/2.0/DESIGN_COMPLIANCE.md), migration evidence, real device tests and explicit rollback instructions with every code change.

## Attribution rules

- Code from another project goes in with its licence, a line in `docs/SOURCES.md` and `THIRD_PARTY_NOTICES.md`, and
  (for Apache-2.0 code you changed) the header in `third_party/minklauncher/PROVENANCE.md`.
- Third-party artwork needs a row in `brand-assets/CATALOG.tsv`; icons come only from Tabler through `tools/tabler_icons.py`.
- No BlackBerry artwork, fonts, wallpapers or apps. No keys, keystores, device identifiers or personal data.

## Translations

The 18 translations are machine-assisted drafts. To correct one, edit `app/src/main/res/values-<lang>/strings.xml`
(or `design/src/main/res/values-<lang>/strings.xml`); keep the `%1$s`-style placeholders and the key names, and
single capital letters in hints that name a keyboard key. `StartTextTest` checks keys and placeholders.

## Licence

By contributing you agree your contribution is licensed under Apache-2.0, like the rest of the repository.
