# TenSeven 2.0 companion distribution, installer and migration contract

**Specification only — no wizard or separate APKs are implemented by this PR.** Governing decision: [ADR-0001](../decisions/ADR-0001-MODULAR-COMPANION-APKS.md). Release entry point: a single Full TenSeven Launcher APK; companions remain optional.

Games use a separate repository and installer under [ADR-0002](../decisions/ADR-0002-GAMES-OUTSIDE-LAUNCHER.md). They are not Launcher companions or part of the Launcher Complete ZIP.

## Artifact layout (target, not an existing release)

```text
TenSeven-2.0-Complete.zip
  launcher/TenSeven-Launcher-2.0.apk
  companions/TenSeven-Phone-2.0.apk
  companions/TenSeven-Calendar-2.0.apk
  companions/TenSeven-Tasks-2.0.apk
  companions/TenSeven-Notes-2.0.apk
  companions/TenSeven-Calculator-2.0.apk
  companions/TenSeven-SoundProfiles-2.0.apk
  release-manifest.json
  SHA256SUMS.txt
  README.md
```

Also publish the standalone Launcher APK and each companion APK for independent install/update. Exactly one Full Launcher APK; **no Lite APK**. The Complete ZIP is an offline collection of artifacts, **not an installable Android package**. Filename/version placeholders here are proposals, not built release assets.

## Required release manifest fields

- release version, source commit and timestamp; release channel; manifest format version;
- for each component: app ID, artifact path, SHA-256, signed APK signer certificate SHA-256 (and rotation lineage if present), version code/name, `minSdk`, expected exported launch intent/capabilities;
- required versus optional flag, recommended minimum companion version, compatibility with the Launcher protocol;
- applicable package ownership and installation constraints; no signing secrets.

**Important:** A SHA256SUMS file packaged in the same untrusted ZIP provides integrity consistency but **not authenticity**. The installer must verify the signing certificate against independently pinned, trusted identities and use a trusted acquisition source or verifiable signed release metadata. Reject arbitrary APK files or manifest attempts to change pinned signer/app identity.

## Installer location and user flow

1. Install the standalone Launcher APK through Android's package installer; choose TenSeven as the default Home app if desired.
2. During initial Setup, offer **Minimum Apps** and **Full Apps** as selection presets; preserve the existing setup choice wording and provide per-app toggle/Skip.
3. In Launcher Settings > Apps & Features > TenSeven Companion Apps, show each expected package as **Not installed**, **Installed**, **Update available**, **Unsupported**, or **Install failed**. Do not infer an installation succeeded until Android confirms package/version/signature.
4. Allow selecting an official release ZIP via Storage Access Framework (document picker) or official trusted download if enabled. Extract to app-private temporary storage using bounded, traversal-safe extraction; reject duplicates, symlink entries, excessive sizes and unexpected paths.
5. Display chosen components with size, purpose, permissions, existing version and what the install will change. Ask separately for Android's applicable permission to install apps from this source.
6. Hand each APK to Android's supported PackageInstaller/system confirmation UX. **No silent installs, no device-admin/root shortcuts, no restricted-setting bypass**. User can skip, cancel or continue after partial failure.
7. Verify installed packages and report per-app success/failure; offer **Open** and **Manage** actions and a path back to Setup. Preserve user-selected defaults: never seize Phone default role automatically.
8. Re-enter the same flow from Settings at any time; allow independent companion updates. Missing companions fall back gracefully to installed third-party apps.

## Compatibility and privacy contracts

- Launcher discovers explicitly named TenSeven packages plus Android launcher/default apps using scoped `<queries>` and appropriate public intents, respecting profile separation and package visibility.
- Launcher does not read private companion files, scrape other apps' screens, or bypass Android user-profile/notification access boundaries.
- Shared colors, icon tokens and animations are **source-level design contracts** implemented in each app. Launcher cannot reskin arbitrary third-party activities.
- Phone owns `ROLE_DIALER` and Telecom-related APIs only with explicit user approval; Calendar owns its event UI and required provider permissions; Tasks owns alarm/notification state; Sound Profiles owns DND-modification access as applicable.
- Hub can still display Android-delivered notifications automatically with access, and CalendarProvider events with the relevant permission. A companion may provide extra data only via a narrowly scoped and authenticated opt-in API.
- No app can claim to have a working live view of arbitrary foreground apps without the required Android platform APIs.

## App identity and signing

- Existing Launcher application ID remains `io.github.aimindseye.tenseven`. Each companion requires its own unique ID and relevant Android developer verification/registration before public distribution. This owner has confirmed **Personal (Full distribution)**; no second developer account is planned.
- Reusing an existing release signer across tightly coupled APKs is possible but **not mandatory**. Resolve separate-per-app keys versus shared signer via security review before implementing the release signer map. Key reuse widens impact of a compromised keystore. The common design module does not require common signing.
- Preserve each package's signer and `applicationId` across future upgrades. The wizard verifies signer certificate, version and app ID before installation. A package name alone is insufficient.
- The repository must contain **no keystore, private key, passwords, tokens, device IDs or personal exported data**. CI produces unsigned or safely test-signed artifacts; production signing is handled by an owner-controlled release environment.

## Existing data: migration must precede removal

Today extras and certain records live in the Launcher package sandbox, even if the embedded Activities have distinct `taskAffinity`. A new package **cannot directly read** that sandbox. Installation of a companion cannot itself transfer existing notes/reminders.

For every data-bearing companion:

1. Inventory exact old keys, databases, providers, files, notification/alarm identifiers and backup paths. Capture a deterministic read-only export snapshot and checksum.
2. Implement a temporary Launcher-side migration provider/exporter with narrowly scoped, one-time, user-authorized IPC or an explicit secure export/import flow. No world-readable or long-lived provider.
3. Install the companion, import into its new sandbox, verify counts/content/identifiers, and report success or actionable failures. Handle duplicates and replays idempotently.
4. Keep the old data available for rollback until the new owner confirms, the user can recover it, and release retention rules allow cleanup. Do not disable old alarms until replacement alarms are scheduled and verified; avoid double notifications.
5. Test from actual prior signed Launcher builds on Titan 2: upgrade, interrupted import, permission denial, app uninstall/reinstall, backup restore and rollback. A test-only fresh installation is not adequate.
6. Only after all gates pass remove embedded Activity/services and extraneous permissions from Launcher; preserve compatibility shims as required.

Phone call log and calendar events are generally Android-provider data, but UI preferences, blocking/screening settings, selected calendars, custom data and default-role state still require auditing. Do not assert no migration is needed without evidence.

## Release gate and fallback

- Unit, integration, lint, detekt, ktlint and canonical P2 checks run for Launcher **and every published companion module**, with no new findings or baseline exemptions.
- All release APKs and ZIP checksums/signers must match the release manifest; verify with `apksigner`/Gradle release verifier and clean install + update scenarios.
- Test the Complete ZIP on Titan 2 with Minimum preset, Full preset, selected subset, missing APK, malformed ZIP, signer mismatch, lower version, denied install permission, canceled install, partial success and no network.
- Run Active Frames tall-touch CI and other required Android jobs to green. Do not label any milestone complete while the regression remains.
- No optional companion may block core launcher startup or Home navigation.

## Licensing

The existing SableOS-origin code and its notices must retain source attribution. Do not copy code from GPL applications into this Apache-licensed repository without a deliberate license/redistribution review. Launching an independently installed GPL app through Android intents is distinct from incorporating its source.

See [roadmap](MODULARIZATION_ROADMAP.md) for implementation sequencing and acceptance gates.
