# Build

## 1. Host setup

JDK 17 (Temurin recommended) and the Android SDK with `platforms;android-36` and `build-tools;36.0.0`:

```bash
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
export ANDROID_HOME=/path/to/android-sdk     # or write sdk.dir=... into local.properties (not committed)
```

The first Gradle run downloads Gradle 8.13 (checksum pinned in `gradle/wrapper/gradle-wrapper.properties`),
the Android Gradle Plugin 8.13.2, Kotlin 2.3.10 and the Compose BOM 2026.02.01. These are the versions
SableOS builds the same sources with.

## 2. Build and test

```bash
./gradlew :app:assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :design:testDebugUnitTest :app:testDebugUnitTest
./gradlew :design:lintDebug :app:lintDebug           # reports in build/reports/lint-results-debug.*
./gradlew detekt ktlintCheck                         # reports in build/reports/detekt, build/reports/ktlint
./gradlew :design:koverXmlReportDebug :app:koverXmlReportDebug
bash tests/run.sh                                    # static checks; needs python3, optionally shellcheck and xmllint
```

Install the debug build next to anything else: its id is `io.github.aimindseye.tenseven.debug`.

## 3. Quality gates

detekt (rules: `config/detekt.yml`), ktlint (`.editorconfig`) and Android Lint **report without failing** while
`tenseven.strictQuality` is `false` (the default in `gradle.properties`). Compile errors and failing unit tests
always fail. The reason is honest and temporary: the imported SableOS sources were written under a different
rule baseline, and the first run on this repository will show how far apart they are.

To make the gates fail:

- locally: `./gradlew -Ptenseven.strictQuality=true detekt ktlintCheck :app:lintDebug`
- in CI: GitHub > Settings > Secrets and variables > Actions > **Variables** > New repository variable
  `TENSEVEN_STRICT_QUALITY` = `true`

Do this once a run is clean. Anything kept on purpose goes in a detekt or Lint baseline with a reason, not in
a blanket suppression. `config/detekt.yml` is a byte-exact copy of SableOS's (checked by `tests/run.sh`
against `config/DETEKT_PROVENANCE.env`); put Tenseven's own rules in a second file and add it to `config.setFrom`
in `build.gradle.kts`.

## 4. Dependency locks and checksums

Not committed yet: they need network access to write. On a machine with internet and the SDK:

```bash
bash tools/refresh-locks.sh
git add -A '*gradle.lockfile'
```

`build.gradle.kts` turns locking on for each module that has a `gradle.lockfile`, so nothing changes until you
commit them. After that, a changed dependency fails the build until the locks are refreshed on purpose.

## 5. Icons and brand logos

```bash
python3 tools/tabler_icons.py check          # manifest, SVGs and generated files agree (CI runs this)
python3 tools/tabler_icons.py import --from <extracted @tabler/icons package>   # after adding a glyph name
python3 tools/brand_assets.py check
python3 tools/brand_assets.py fetch          # on a machine with internet: Google Play listing icons
```

See `third_party/tabler-icons/PROVENANCE.md` and [`BRAND_ASSETS.md`](BRAND_ASSETS.md).

## 6. GitHub Actions

| Workflow | File | Runs on | Needs |
|---|---|---|---|
| `checks` | `.github/workflows/checks.yml` | PR and `main`, except docs-only | nothing |
| `Android` | `.github/workflows/android.yml` | PR and `main` when `app/`, `design/`, `config/`, `gradle/` or the Gradle files change | optional variable `TENSEVEN_STRICT_QUALITY` |
| `Security` | `.github/workflows/security.yml` | every PR, `main`, and Mondays | nothing |
| `SonarCloud` | `.github/workflows/sonar.yml` | PR and `main` | secret `SONAR_TOKEN`; project `aimindseye_tenseven-launcher` in organisation `aimindseye` with Automatic Analysis **off** |

Actions are pinned by commit, as in SableOS; Dependabot proposes updates weekly. Pull requests from forks do
not get `SONAR_TOKEN`, so the SonarCloud job skips them.

### SonarCloud first-time setup

1. In SonarCloud, import `aimindseye/tenseven-launcher` into the `aimindseye` organisation. The project key must be
   `aimindseye_tenseven-launcher` (it is in `sonar-project.properties`).
2. Project > Administration > Analysis Method: turn **Automatic Analysis off**.
3. The repository secret `SONAR_TOKEN` must belong to a user who can analyse that project.
4. Run the workflow once from the Actions tab. Coverage is excluded until the Kover XML reports are imported
   (`sonar.coverage.exclusions` in `sonar-project.properties`); without that line the "Sonar way" gate's 80%
   coverage condition on new code would fail.

### What CI cannot do

Run on a phone, test the keyboard, or build a signed release. Those are manual and are tracked in
`ROADMAP.md`.
