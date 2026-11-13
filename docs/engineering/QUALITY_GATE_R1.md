# Quality gate R1: trust boundaries and policy provenance

R1 is rooted at `c18297205cae85a6a46573a65d150468a7d4e32e`. It repairs the
checker and its integration handling; it does not repair or qualify inherited
application/UI debt. Main-relative failures must remain visible and HOLD.

## Runtime authority

CLI references and evidence destinations are untrusted. The repository and its
build scripts are explicitly selected trusted build inputs. Git/Python/JDK and
the CI/operator environment are trusted execution inputs; this utility does not
sandbox arbitrary Gradle code or a malicious process with the same user identity.

Only a restricted revision grammar reaches `git rev-parse --verify
--end-of-options`; the result must be a full commit object ID. Subsequent Git
operations use fixed command shapes and full object IDs. Literal pathspec mode
and option terminators keep filenames as data. There is no shell evaluation or
CLI-supplied executable/subcommand schema. Invalid refs fail before side effects.

R1.1 restricts `--out` to a fresh absolute destination under validated native
runtime temp, `RUNNER_TEMP`, or Downloads. A platform-wide temp pathname is no
longer an independently granted output authority. Every directory ancestry is
opened from the filesystem root using no-follow descriptors and checked for
root/current-user ownership and unsafe write permissions. Only root-owned
sticky shared ancestors are traversable; a mode-0700 current-user workspace
must establish a private boundary below shared storage before any allocation.
Missing children of shared storage are never allocated by the checker.

Explicit `RUNNER_TEMP` must already exist and be privately controlled; GitHub's
owner-controlled 0755 root remains valid with protected ancestry. Local native
private temp and Downloads are supported. For a local path elsewhere, first
securely allocate a private workspace and select it as `RUNNER_TEMP`:

```sh
REVIEW_ROOT="$(mktemp -d)"
RUNNER_TEMP="$REVIEW_ROOT" python3 -B tools/deliverable_quality_gate.py \
  --repo . --base-ref <actual-parent-SHA> --out "$REVIEW_ROOT/gate"
```

A securely allocated private workspace under `/tmp` or `/private/tmp` is also
supported when explicitly selected this way. Direct allocation into shared temp
is rejected. No CLI flag broadens authority. User-controlled symlinks, traversal,
foreign owners, unsafe ancestors, repository containment, arbitrary out-of-root
paths and existing destinations fail closed. Only root-owned system aliases
(such as macOS `/var`) may canonicalize; their ancestry and resolved target are
still verified. Evidence allocation remains atomic exclusive mkdir0700 under a
validated private parent, followed by descriptor/inode/linkage verification.

Evidence directories are reserved with exclusive mkdir and mode 0700. Existing
intermediate directories must be owned by the user and not group/world writable.
Every directory walk uses directory descriptors and `O_NOFOLLOW`; files use
fixed internal names and `O_CREAT|O_EXCL|O_NOFOLLOW`, mode 0600. Reads/writes of
receipts, commands, logs and archived reports never reopen a CLI pathname for
writing. Inode/linkage checks before and after use detect directory replacement.
A concurrent symlink/file planting cannot redirect or overwrite a victim. A
same-user actor can still deny service or rename its own inode; such changes
cause HOLD and never authorize an arbitrary symlink target.

R1.1 preserves ASCII-only entry validation using `re.ASCII`, combines the nine
reported test contexts, isolates the two overlay exception actions with
`ExitStack`, and binds policy-test loop closures explicitly. The pre-existing
S5778 in `test_stale_reports_fail_closed` is unchanged legacy debt. Local tests
and source review cannot certify remote Sonar closure; S5443/S6353/S9154/S5778/
S1515 verification remains PENDING CI until the exact R1.1 source is scanned.

## Exact historical transitions

R1-B authorizes verification of the two exact historical policy transitions,
not new exclusions, tool versions or finding exemptions. These records are
reviewed code, not a user-supplied approval file or generic bypass flag.

| Path | Origin commit | Before blob | After blob |
|---|---|---|---|
| gradle/wrapper/gradle-wrapper.properties | df3125234c8bc6411a0d7a20397ff9f2ff9a6481 | b25c363d4efefc51781b9843eb1aff77e8fb8120 | fad1ca746fdabbc0bf94fb9e2cff33e0dfb6d8bd |
| sonar-project.properties | 4aed0fa2ba4e7938faab75358c6e7b3ee1034aca | 3680653b3a2c4dac91c7076b08d6f70a5f97f99f | d4fd9e36a739c3d533a2dc972c19f37404bbcdbe |

Verification binds the actual base blob, candidate worktree bytes, origin
commit's parent blob and origin blob. The origin must be in the comparison
lineage. Altering one byte, a checksum, an exclusion, an origin or a threshold
fails closed; knowing a filename or citing an unrelated ancestor grants nothing.
Unknown protected files remain rejected. The approved Compose correction and
additive CI wiring retain their exact-content checks.

The wrapper transition pins Gradle 9.9.0 -> 9.9.1 URL/checksum, recorded as PR127
C2 in the handoff. Both parent and candidate analysis use the candidate wrapper
with explicit `--project-dir`; the parent's wrapper files stay pristine. JDK,
AGP/Kotlin, analyzer versions and configs must remain equivalent. Source and
HEAD fingerprints are checked across each analysis; reports are forced fresh.
The only temporary parent source overlay remains the exact approved Compose
naming YAML/root wiring, reversed only when it still matches our own bytes.

The Sonar transition adds binary-format exclusions and Android-dependent
coverage seams documented by the CI-R4 commit. It does not affect Detekt,
ktlint or Android Lint. The added coverage paths are D5BbDialogs,
D5PhoneRoleSetting, TenSeven2AppTheme, Settings2VisualTheme, TenSevenPhoneData,
TenSevenTelecom and D5CalendarRepository. Testable pure policies remain measured;
no threshold changes are authorized. Independent SonarCloud conditions still
block promotion, including the two reported R1-A security flows until CI
confirms the remediation. Historical provenance is not a Sonar finding waiver.

## Suppressions and unresolved integration debt

[R1_SUPPRESSION_AUDIT.json](R1_SUPPRESSION_AUDIT.json) enumerates every one of
the 103 diff-added Kotlin suppression-marker lines reported by PR132, at the
exact source SHA, with line, text, immutable blame origin, ancestry, risk and
review classification. Some were moved historical lines. None is automatically
approved or removed. FunctionNaming annotations require Compose-redundancy
review; unused-parameter markers require contract review; MissingPermission
requires a high-risk permission/control-flow review. All remain NOT_APPROVED.

Unknown protected policy changes stop analysis. Verified historical policy
changes allow the real main-parent comparison to execute; suppression review
blockers are retained alongside analyzer findings and still force HOLD. This is
fail-closed rather than fail-fast: it collects diagnostic evidence without
converting inherited suppressions or findings into a pass. Main is always the
integration base; M2-A/c182 parent qualification is reported separately.

R2 application debt (LargeClass in Phone/Calendar, SableStartScreen length and
three M2-A test names), R3 landscape execution/readiness, and the inherited
JL10 document whitespace issue are unchanged by R1. Canonical accepted version
15 remains `6036d24e2ceafc1af0b780e657a6b068d49b91f4`. No baseline, suppression,
coverage exclusion, application persistence, signing or presentation change is
part of this patch. Local security/negative tests are necessary evidence;
actual Sonar/CodeQL/remote gates must still verify the committed source in CI.
