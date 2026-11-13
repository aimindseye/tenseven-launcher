# TenSeven engineering delivery standard — v2 (mandatory)

[AGENTS.md](../../AGENTS.md) governs every future source deliverable. Preserve
existing work and protected launcher behavior. Work directly in the repository;
do not distribute source ZIPs, commit, publish or install APKs without approval.

## Qualification

Use the actual parent/base SHA and the repository wrapper with JDK 17, Android
SDK platform 36 and build-tools 36.0.0. `tenseven.strictQuality=false` tolerates
legacy debt; a successful build alone is insufficient.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
python3 -B -m unittest discover -s tools/tests -p 'test_deliverable_quality_gate.py' -v
python3 -B tools/deliverable_quality_gate.py \
  --repo . --base-ref <actual-parent-SHA> \
  --out /private/tmp/tenseven-quality-<unique-run> --approved-compose-policy
bash tests/run.sh
python3 tests/test_release_verifier.py
python3 tests/test_quality_ratchet.py
git diff --check
```

The gate creates a detached parent worktree under its fresh external output
folder. Alternatively, supply `--baseline-repo` pointing to a clean detached
worktree at that exact SHA. It never resets or formats the candidate worktree.
Keep evidence and baseline worktrees until review; do not delete existing work
as part of rerunning checks. Choose a new output folder for each attempt.

The approved Compose policy contains only:

```yaml
naming:
  FunctionNaming:
    ignoreAnnotated:
      - Composable
```

`--approved-compose-policy` allows precisely this file and its Gradle wiring,
and applies the identical correction to parent analysis when the parent
predates it. The upstream provenance file, normal function naming, all other
rules, thresholds and strictness remain unchanged. This is a reviewed convention
correction, not a comparator exemption. Further policy changes require approval.

The gate runs fresh parent Detekt, ktlint and Android Lint tasks, then candidate
unit tests for both modules, debug/release assembly, all three analyzers and
Kover reports. Both invocations disable report reuse with `--rerun-tasks` and
`--no-build-cache`. Each module/source set must supply its required XML report;
missing, stale, malformed, unavailable-source and outside-repository reports
fail closed. The gate also rejects a source change during analysis.

Findings match by module/report, file, unchanged source line mapped across
insertions, column, rule, message and severity, with multiplicity preserved.
Quantitative LongMethod/complexity debt may decrease but cannot increase. A new
file cannot borrow another file's debt. Lint secondary locations count as one
issue; binary resource issues match only when the file bytes are unchanged.
Changes to a finding's source or identity are conservatively new. No blanket
Compose or manifest waiver is allowed.

`QUALITY_GATE.json` retains exact base SHA, changed paths, source hashes, report
hashes/inventory, analyzer totals, each new/existing finding and mapped baseline
line. Baseline/candidate folders retain exact Gradle command arguments, logs,
exit codes and copies of the inspected reports. Only both zero Gradle exits and
zero introduced findings produce `PASS_NO_NEW_FINDINGS`. `--reports-only` is
always diagnostic HOLD and never proves freshness or qualification. There is
no unchecked CI report-reuse mode.

R1 hardens CLI Git/evidence boundaries and verifies exact historical policy
transitions. See [QUALITY_GATE_R1.md](QUALITY_GATE_R1.md) for allowed output
roots, provenance hashes, equivalent-wrapper analysis, and the unapproved
suppression audit. Unknown policy changes still fail closed; audit blockers
and new findings remain HOLD. No historical finding is exempted.

## Qualifying the checker itself

Offline tests must cover shifted manifest and naming findings, strict new-file
handling, duplicate findings, metric regressions, missing module/source-set
reports, freshness, malformed XML and unauthorized policy changes. After real
Gradle reports are generated, run the same suite with:

```sh
TENSEVEN_REAL_BASE=/path/to/detached-parent \
TENSEVEN_REAL_CANDIDATE="$PWD" \
python3 -B -m unittest discover -s tools/tests -p 'test_deliverable_quality_gate.py' -v
```

This checks the real comparison, the existing SelectedPhotoAccess manifest
warning and injected regressions. Synthetic fixtures alone do not qualify it.
The comparison checks all reports, so new findings in untouched paths also
block delivery. Retain initial failures and every validation cycle's evidence.

## CI and acceptance

Android CI runs the baseline-aware gate for PRs after its normal build, with
full history to resolve the actual merge base. Changes to the checker/tests
also trigger Android CI. The workflow additions are checked exactly; the
presence of a step name cannot authorize deleting existing checks.

The independent canonical P2 workflow still uses accepted version 15
`6036d24e2ceafc1af0b780e657a6b068d49b91f4`; do not substitute the M2-B parent
comparison for that gate. Its formatting/restore sequence belongs only in its
disposable CI workspace. Do not run it in a dirty user worktree.

Report SonarCloud, CodeQL, Gitleaks and other remote gates as PENDING CI until
they actually pass. Report Active Frames API 34/35, responsive layouts, wizard
UI/persistence and Titan 2 validation separately as NOT RUN when unavailable.
No companion installation, signer trust, migration or embedded Extras removal
is authorized by successful local analysis.

Every delivery receipt must name the base branch/SHA, tracked/untracked changes,
protected-path audit, exact commands/exit codes/toolchain, report locations,
new versus baseline findings, unit-test counts, build artifacts and remaining
risks. Recommend HOLD if a required gate remains unresolved. READY FOR REVIEW
never authorizes merging, signing, releasing or installation.
