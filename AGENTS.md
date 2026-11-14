# TenSeven Launcher — Engineering Instructions for Coding Agents

**Scope:** This file governs all work in `aimindseye/tenseven-launcher`, including the TenSeven 2.0 modular-companion migration. Read it at the start of **every** task. This document provides working rules; the repository's executable checks and CI remain authoritative. If a rule conflicts with an executable check, investigate and report the conflict rather than weakening either silently.

## 1. Before editing anything

1. Read `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, and the diff (including untracked files). Preserve all existing work; never assume the worktree is clean.
2. Read the relevant source files **and** the current `.editorconfig`, `config/detekt.yml`, `config/DETEKT_PROVENANCE.env`, `gradle.properties`, `build.gradle.kts`, `docs/BUILD.md`, `tests/run.sh`, and applicable `.github/workflows/*.yml`.
3. Identify the task's precise file scope, parent/base commit, protected components, validation plan, and rollback strategy. Explain ambiguities before making a destructive change.
4. Make focused edits directly in the local repository. Do not create distribution ZIPs, copy whole directories over source, or stage unrelated changes as a substitute for development.
5. **Never** reset, clean, restore, stash, rebase, switch branches, or overwrite files containing existing work without explicit authorization. Do not commit, push, open/merge PRs, publish, or install an APK unless expressly asked.

## 2. Kotlin and Compose coding standards

- `.editorconfig` is authoritative: UTF-8, LF, final newline, trailing whitespace removed; Kotlin uses **4-space indentation** and `ktlint_official`.
- Use explicit imports; no new wildcard imports. Keep chain continuation, `when` entry braces/spacing, argument wrapping, and line length compliant with the installed ktlint engine, not assumptions from examples.
- New or changed Kotlin code must be free of **new** Detekt and ktlint findings. Avoid magic numbers (named constants), unnecessary returns, excessive function length/complexity, unsafe null handling, and deprecated APIs when an appropriate supported alternative exists.
- Compose functions annotated `@Composable` are intentionally PascalCase under `.editorconfig`. The inherited Detekt `FunctionNaming` rule may disagree: **do not** rename composables to camelCase merely to quiet Detekt, add blanket `@Suppress`, or edit the upstream provenance file. Investigate and propose an explicit, narrowly scoped TenSeven configuration correction with verification before applying one.
- Unit tests must obey the same naming, import, and formatting rules. Prefer testable pure policies separated from Android/Compose UI. Add focused unit or instrumentation tests for behavior changes.
- Do not run repository-wide autoformatting on a dirty worktree. Format only authorized changed files and inspect the resulting diff. `ktlintCheck` is safe for inspection; broad `ktlintFormat` can mutate unrelated source.
- Android platform changes must respect SDK/API guards, user-granted permissions, manifest visibility requirements, and platform policy. Do not assume companion package names prove signer trust.

## 3. Non-negotiable compatibility and safety boundaries

- Preserve BBOS 7/10 homes, wallpaper/themes, Hub, Peek, lock-screen notifications, Launcher Settings navigation, bundled Extras, and **Active Frames** behavior unless the approved task explicitly changes them.
- Keep the Full-only launcher model and existing package identity intact. No unsupported automatic restricted-settings approval, silent role acquisition, or privileged permissions.
- No signing-key changes, keystore regeneration, secrets exposure, release publication, silent APK installs, companion migration, or removal of embedded-app fallbacks without explicit approval and matching acceptance tests.
- New companion APK detection is informational unless a separately approved signer-verification and installation design is implemented. Package-name matching alone is not trust.
- Preserve existing configuration and persisted user data. For migrations, require a rollback design and actual device validation before promotion.

## 4. CI is the quality contract — inspect the current workflow files

Repository sources of truth:

| Check | File / command | Interpretation |
|---|---|---|
| Static repository policy | `.github/workflows/checks.yml` → `bash tests/run.sh` | Must pass; includes shell/XML/Python checks, generated icon/brand checks, Detekt provenance, manifest protections, third-party licensing, wrapper integrity and Hub invariants. |
| Release verifier tests | `python3 tests/test_release_verifier.py` | Run when release/build/manifest code is affected; CI runs mock-SDK regression tests. |
| Android build & reports | `.github/workflows/android.yml` | JDK 17, Android SDK/platform 36 and build-tools 36.0.0; debug/release assembly, JVM tests, Android Lint, Detekt, ktlint, Kover. |
| Blocking legacy-to-new findings ratchet | `.github/workflows/p2-quality.yml`, `tools/quality_ratchet.py`, `tests/test_quality_ratchet.py` | CI comparison against an accepted baseline; do not conceal or worsen legacy findings. |
| SonarCloud | `.github/workflows/sonar.yml`, `sonar-project.properties` | Sonar's own quality gate is independent of `tenseven.strictQuality`; requires CI and `SONAR_TOKEN`. |
| CodeQL | `.github/workflows/codeql.yml` | Java/Kotlin security-and-quality scanning; relevant PR CI required. |
| Security | `.github/workflows/security.yml` | Gitleaks is blocking; MobSF records findings. Never add secrets or weaken scanning. |
| Active Frames instrumented UI | `.github/workflows/frames-ui.yml` | API 34/35 plus responsive square, tall and landscape matrices when relevant; local JVM tests are not a substitute. |

**Important:** CI trigger paths and target branches differ. Some workflows only run for PRs targeting `main`; a stacked feature PR may not exercise all gates. Check which jobs *actually ran*, and reproduce or explicitly mark omitted gates **NOT RUN**. A docs-only PR may skip the Android workflow.

## 5. Mandatory local validation before declaring implementation complete

Use the repository's Gradle wrapper, not an unpinned system Gradle. On the user's Mac, the SDK and JDK must match CI. A representative full local run is:

```bash
./gradlew --no-daemon --console=plain --continue -Ptenseven.strictQuality=false \
  :design:testDebugUnitTest :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease \
  :design:lintDebug :app:lintDebug detekt ktlintCheck \
  :design:koverXmlReportDebug :app:koverXmlReportDebug
bash tests/run.sh
python3 tests/test_release_verifier.py
python3 tests/test_quality_ratchet.py
```

- `tenseven.strictQuality=false` is the repository default because legacy imported sources have findings. **Gradle exit code 0 is not a quality pass.** Read the Detekt XML/SARIF, ktlint reports, and Android Lint XML/SARIF for both `app` and `design` as applicable.
- **Zero introduced findings** are required even while global strict mode is false. New files: zero findings. Modified files: compare candidate findings with a reproducible report for the correct parent/base source and equivalent tool/config versions; do not count every unchanged finding in a touched file as new. Report rule, file, line, and evidence. Missing/stale/malformed reports fail closed.
- Run `tools/deliverable_quality_gate.py` against the actual parent SHA in a fresh external evidence directory. It must build fresh reports for both a detached parent worktree and the candidate, compare findings with unchanged-line mapping and multiplicity, and retain report hashes, source digests, commands and exit codes. Diagnostic `--reports-only` output is always HOLD. Qualify checker changes against real Gradle reports and injected regressions as well as offline tests; its green output does not replace P2 or remote gates.
- The approved `config/detekt-tenseven.yml` correction exempts only `FunctionNaming` on `@Composable`, matching `.editorconfig`. Keep other naming and quality rules active. Use `--approved-compose-policy` when comparing to a parent that predates this correction so both sides use the identical reviewed configuration. Further configuration changes require separate explicit approval.
- Do not change `gradle.properties` strictness, quality thresholds, CI workflows, baselines, or suppression lists to conceal violations. Any intentionally accepted exception needs a narrowly scoped written rationale and review. `config/detekt.yml` must remain byte-identical to the SableOS provenance blob (`tests/run.sh` checks this). TenSeven-specific Detekt overrides belong in a separate proposed file and require explicit approval and Gradle wiring.
- Verify local `git diff --check`, relevant test suites, and successful compilation **after** the last source edit. For UI/permission/notification regressions, device/emulator acceptance is separate from unit tests.
- For SonarCloud/CodeQL/Gitleaks and other remote gates, report **PENDING CI** until the actual workflow succeeds; never claim local simulation proves a remote gate passed.

### P2 workflow safety warning

`.github/workflows/p2-quality.yml` runs `ktlintFormat` and then `git restore --worktree .` in its disposable CI workspace. **Never copy or execute that sequence inside a user's dirty working tree**; it can discard the existing M2-B implementation. Run safe checks and build a disposable detached worktree for any baseline comparison instead.

## 6. Delivery receipts — mandatory for every change

Before saying "done" or "PASS", provide:

1. Base branch/SHA, changed tracked and untracked paths, and confirmation no protected paths changed unintentionally.
2. Exact validation commands, exit codes, versions/toolchain, and the corresponding report locations. Distinguish `PASS`, `FAIL`, `PENDING CI`, `NOT RUN`, and `BLOCKED`.
3. New findings grouped by Detekt/ktlint/Android Lint (target **zero**), separately from existing baseline findings; include the exact baseline ref and method.
4. Compilation and unit-test counts, static policy checks, build outputs, and any applicable instrumentation/Titan 2 outcomes. Do not invent runtime or device results.
5. Remaining risks, unresolved discrepancies, and an explicit recommendation: HOLD or READY FOR REVIEW. **READY FOR REVIEW is not merge/release approval.**

If any required check fails, repair only the authorized code, rerun the affected checks, and retain honest failure evidence. If tool access prevents qualification, stop with a precise blocker rather than issuing a purportedly verified deliverable.

## 7. Current TenSeven 2.0 migration constraints

M2-A provides read-only companion catalog/presence inspection. M2-B adds an installation-planning/selection experience but **does not authorize companion APK installation**, signer trust, embedded Extras deletion, or automatic migration. Preserve its uncommitted local work and treat it as **HOLD** until all genuine new-source issues and the baseline checker are resolved with real macOS Android toolchain reports.

For additional release, build, signing, platform, or architecture details, consult `docs/BUILD.md`, `docs/decisions/`, and `docs/2.0/` rather than relying on this compact agent contract alone.
