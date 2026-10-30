# Contributing

Thanks for helping. Tenseven is small and early, so the most useful contributions are: a device's key profile
captured on real hardware, a fix the first CI runs point to, and a Mink port from `third_party/minklauncher/PORTING.md`.

## Workflow

1. Branch from `main`; one change per pull request.
2. `bash tests/run.sh` and `./gradlew :design:testDebugUnitTest :app:testDebugUnitTest` before you push.
3. The CI workflows run on the pull request. A pull request that touches only docs skips the build.
4. Keep decisions in pure Kotlin under `model/` and `privacy/` with a unit test, and Android calls in `platform/`.

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
