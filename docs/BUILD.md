# Build

> **2.0 modular release is a future target, not yet buildable here.** Today only `:app` (Full Launcher with embedded extras) and `:design` are configured. See [ADR-0001](decisions/ADR-0001-MODULAR-COMPANION-APKS.md) and [companion release spec](2.0/COMPANION_DISTRIBUTION.md). New companion Gradle modules, separate APK IDs/signing verification, Complete ZIP and wizard will be introduced and qualified in stages. Do not infer production companion APKs or registered package names from these docs. Keep the existing Launcher signing identity and verifier intact during the transition.

## 1. Host setup

JDK 17 (Temurin recommended) and the Android SDK with `platforms;android-36` and `build-tools;36.0.0`:

```bash
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
export ANDROID_HOME=/path/to/android-sdk     # or write sdk.dir=... into local.properties (not committed)
```

The first Gradle run downloads Gradle 9.8.2 (checksum pinned in `gradle/wrapper/gradle-wrapper.properties`),
Android Gradle Plugin 9.5.0, Kotlin 2.4.21, Lifecycle 2.10.0 and the Compose BOM 2026.02.01.
Compile and target SDK are 36; minSdk is 34. Lifecycle 2.10 is pinned for compatibility
because 2.11 requires SDK 37, which the CI SDK channel does not provide.

## 2. Build and test

```bash
./gradlew :app:assembleDebug                         # app/build/outputs/apk/debug/Tenseven-Launcher-debug.apk
./gradlew :app:assembleRelease                       # R8-minified: Tenseven-Launcher-release.apk when signed (section 2a), else -release-unsigned.apk
./gradlew :design:testDebugUnitTest :app:testDebugUnitTest
./gradlew :design:lintDebug :app:lintDebug           # reports in build/reports/lint-results-debug.*
./gradlew detekt ktlintCheck                         # reports in build/reports/detekt, build/reports/ktlint
./gradlew :design:koverXmlReportDebug :app:koverXmlReportDebug
bash tests/run.sh                                    # static checks; needs python3, optionally shellcheck and xmllint
```

Install the debug build next to anything else: its id is `io.github.aimindseye.tenseven.debug`.

### 2a. Signing a release build

Android installs only signed APKs. The build signs the release when it finds a key; the key stays on your computer.

```bash
# Official release keystore: ~/tenseven-release-2.jks (PKCS12), alias tenseven.
# It is already registered in Android Developer Console. Never regenerate or commit it.
```

Then give the build the key, in either of two ways:

- a file `keystore.properties` at the repository root (git-ignored), with absolute paths:

  ```properties
  storeFile=/Users/you/tenseven-release-2.jks
  storePassword=...
  keyAlias=tenseven
  keyPassword=...
  ```

- or environment variables `TENSEVEN_KEYSTORE`, `TENSEVEN_KEYSTORE_PASSWORD`, `TENSEVEN_KEY_ALIAS`, `TENSEVEN_KEY_PASSWORD`.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/Tenseven-Launcher-release.apk
```

With signing credentials present, Gradle verifies the keystore certificate against the registered fingerprint before packaging. Older alpha APKs built before this certificate was created may not update in place; back up settings and reinstall if the signing identities differ. A different key fails closed. Without a key the build still succeeds and writes `Tenseven-Launcher-release-unsigned.apk` (what CI does). Use the same key
for every release: Android refuses to update an app signed with a different one. The release app id is
`io.github.aimindseye.tenseven` (no `.debug`), so it installs beside the debug build as a separate app.

### 2b. Mandatory Full-only signing and APK verification

The Lite variant is retired. A local Mac build with the *existing* registered keystore
produces one signed `Tenseven-Launcher-release.apk`; CI intentionally produces an unsigned
candidate and never receives signing secrets.

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
python3 tests/test_release_verifier.py
bash tools/verify-release-apks.sh app/build/outputs/apk/release/Tenseven-Launcher-release.apk
```

For a complete local verified build and copied test package, use:
`bash tools/build-full-consolidated-test.sh`. The helper checks the release certificate and
delegates to `tools/build-release.sh` and writes versioned release files to
`~/Downloads/tenseven-release/1.0.0/`.

The registered release certificate SHA-256 is
`9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292`.
Never generate a replacement certificate or commit a signing key.
Android's sideload/restricted-settings checks still apply; no manifest can auto-approve
these privileged user choices.

## 2c. Version 1.0.0 on the owner’s Mac

Google uses manifest metadata, not a mandatory APK filename: `versionName = "1.0.0"`,
`versionCode = 18` (greater than the preceding code 17), and the unchanged application ID.
Before a Play upload, also confirm code 18 has not already been used for this application.
The Git tag is `v1.0.0`; the distribution APK is `TenSeven-Launcher-1.0.0.apk`.
The stable internal Gradle APK name is retained for existing automation.

After the release preparation PR has merged and its checks are green:

```bash
cd /Users/piyushdaiya/Documents/projects/tenseven-launcher
git switch main
git pull --ff-only origin main
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
bash tools/build-release.sh
```

The helper requires clean tracked sources and the existing signing configuration. It runs
unit tests, release lint and the registered-key check, builds the signed APK, verifies its
compiled package/minSdk/version/signature, then copies the APK, checksum, release notes and
`SOURCE_COMMIT.txt` into `~/Downloads/tenseven-release/1.0.0/`. No manual signing command is needed.
`TENSEVEN_RELEASE_DIR` can override the output folder. Do not publish CI unsigned APKs.

Install and test the exact versioned asset:

```bash
adb install -r "$HOME/Downloads/tenseven-release/1.0.0/TenSeven-Launcher-1.0.0.apk"
```

## 2d. GitHub release after the signed build

Install/authenticate GitHub CLI (`brew install gh`, then `gh auth login`) if needed.
Publish only after testing the signed asset. Target the recorded source commit, so later
changes to main cannot change the release source:

```bash
cd /Users/piyushdaiya/Documents/projects/tenseven-launcher
release_dir="$HOME/Downloads/tenseven-release/1.0.0"
(cd "$release_dir" && shasum -a 256 -c TenSeven-Launcher-1.0.0.apk.sha256)
gh release create v1.0.0 \
  --repo aimindseye/tenseven-launcher \
  --target "$(cat "$release_dir/SOURCE_COMMIT.txt")" \
  --title "TenSeven Launcher 1.0.0" \
  --notes-file "$release_dir/RELEASE_NOTES.md" \
  "$release_dir/TenSeven-Launcher-1.0.0.apk" \
  "$release_dir/TenSeven-Launcher-1.0.0.apk.sha256" \
  "$release_dir/SOURCE_COMMIT.txt"
```

This creates the tag and release. If the tag/release already exists, inspect it first;
do not overwrite a published artifact or reuse its version code for a different build.

## 2e. Google Play bundle (separate distribution)

New Google Play applications require an Android App Bundle. A GitHub APK filename is not a
Play requirement. With the same signing configuration:

```bash
./gradlew --no-daemon :app:verifyRegisteredReleaseKey :app:bundleRelease
```

The AAB is under `app/build/outputs/bundle/release/`. The registered-key guard also runs for
release bundle signing. Confirm Play App Signing/upload-key configuration in Play Console
before uploading; APK verification does not verify an AAB or submit anything to Play.

Official references: [Android versioning](https://developer.android.com/studio/publish/versioning),
[Play app bundles](https://developer.android.com/guide/app-bundle).

## 3. Quality gates

detekt (rules: `config/detekt.yml`), ktlint (`.editorconfig`) and Android Lint **report without failing** while
`tenseven.strictQuality` is `false` (the default in `gradle.properties`). Compile errors and failing unit tests
always fail. The reason is honest and temporary: the imported SableOS sources were written under a different
rule baseline, and the gates have not yet been brought clean (`ROADMAP.md` phase 1).

To make the gates fail:

- locally: `./gradlew -Ptenseven.strictQuality=true detekt ktlintCheck :app:lintDebug`
- in CI: GitHub > Settings > Secrets and variables > Actions > **Variables** > New repository variable
  `TENSEVEN_STRICT_QUALITY` = `true`

Do this once a run is clean. Anything kept on purpose goes in a detekt or Lint baseline with a reason, not in
a blanket suppression. `config/detekt.yml` is a byte-exact copy of SableOS's (checked by `tests/run.sh`
against `config/DETEKT_PROVENANCE.env`); put Tenseven's own rules in a second file and add it to `config.setFrom`
in `build.gradle.kts`.

## 4. Dependency locks and checksums

Not committed yet (`ROADMAP.md` phase 1): they need network access to write. On a machine with internet and the SDK:

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
| `CodeQL` | `.github/workflows/codeql.yml` | PR (code changes), `main`, and Mondays | nothing; code scanning must be enabled for the repository |
| `SonarCloud` | `.github/workflows/sonar.yml` | PR and `main` | secret `SONAR_TOKEN`; project `aimindseye_tenseven-launcher` in organisation `aimindseye` with Automatic Analysis **off** |

Actions are pinned by commit, as in SableOS; Dependabot proposes updates weekly. Pull requests from forks do
not get `SONAR_TOKEN`, so the SonarCloud job skips them.

The P2 quality ratchet is blocking for new findings even while global strict mode is off.
The Frames UI workflow runs API 34/35 instrumentation and three responsive configurations.
Sonar imports Kover coverage with the exclusions documented in `sonar-project.properties`.

### SonarCloud first-time setup

1. In SonarCloud, import `aimindseye/tenseven-launcher` into the `aimindseye` organisation. The project key must be
   `aimindseye_tenseven-launcher` (it is in `sonar-project.properties`).
2. Project > Administration > Analysis Method: turn **Automatic Analysis off**.
3. The repository secret `SONAR_TOKEN` must belong to a user who can analyse that project.
4. Run the workflow once from the Actions tab. Kover XML reports are imported by the workflow; retain the documented coverage exclusions and quality thresholds.

### What CI cannot do

Run on a phone, test the keyboard, or build a signed release. Those are manual and are tracked in
`ROADMAP.md`. A debug build goes onto a phone with `adb install -r app/build/outputs/apk/debug/Tenseven-Launcher-debug.apk`
(the Titan 2 shows as one adb device; the app id is `io.github.aimindseye.tenseven.debug`).
