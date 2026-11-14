"""Exact historical policy transitions, never finding or filename exemptions."""

from dataclasses import asdict, dataclass
from contextlib import ExitStack, contextmanager
import hashlib
import os
import stat
from pathlib import Path
import re

from quality_gate_runtime import DIRECTORY_FLAGS, QualityError, object_id, repository_path

ROOT_BUILD = "build.gradle.kts"
COMPOSE_CONFIG = "config/detekt-tenseven.yml"
COMPOSE_POLICY = "naming:\n  FunctionNaming:\n    ignoreAnnotated:\n      - Composable\n"
OLD_WIRING = '        config.setFrom(rootProject.file("config/detekt.yml"))\n'
NEW_WIRING = (
    '        config.setFrom(\n'
    '            rootProject.file("config/detekt.yml"),\n'
    '            rootProject.file("config/detekt-tenseven.yml"),\n'
    '        )\n'
)
ANDROID_CI = ".github/workflows/android.yml"
OLD_CI_PATHS = "      - 'tools/deliverable_quality_gate.py'\n      - 'tools/tests/**'\n"
CI_PATHS = OLD_CI_PATHS + "      - 'tools/quality_gate_*.py'\n"
CI_STEP = '''      - name: Enforce changed-file quality
        if: github.event_name == 'pull_request'
        env:
          BASE_BRANCH: ${{ github.base_ref }}
        run: |
          set -euo pipefail
          BASE="$(git merge-base "origin/$BASE_BRANCH" HEAD)"
          python3 -B -m unittest discover -s tools/tests -p 'test_deliverable_quality_gate.py' -v
          python3 -B tools/deliverable_quality_gate.py \\
            --repo "$GITHUB_WORKSPACE" --base-ref "$BASE" \\
            --out "$RUNNER_TEMP/tenseven-deliverable-quality" --approved-compose-policy
'''
QUALITY_CONFIG_FILES = {
    ".editorconfig", "config/detekt.yml", "config/DETEKT_PROVENANCE.env",
    "gradle.properties", "sonar-project.properties", "settings.gradle.kts",
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar", "app/build.gradle.kts", "design/build.gradle.kts",
}
STARTING_SHA = "c18297205cae85a6a46573a65d150468a7d4e32e"


@dataclass(frozen=True)
class PolicyTransition:
    path: str
    origin: str
    before: str
    after: str
    approval: str
    analysis_effect: str


TRANSITIONS = (
    PolicyTransition(
        "gradle/wrapper/gradle-wrapper.properties",
        "df3125234c8bc6411a0d7a20397ff9f2ff9a6481",
        "b25c363d4efefc51781b9843eb1aff77e8fb8120",
        "fad1ca746fdabbc0bf94fb9e2cff33e0dfb6d8bd",
        "R1-B exact-transition review; PR127 C2 recorded in docs/2.0/PR127_HANDOFF.md",
        "Pinned Gradle URL/checksum 9.9.0 -> 9.9.1; run both analyses with candidate wrapper",
    ),
    PolicyTransition(
        "sonar-project.properties",
        "4aed0fa2ba4e7938faab75358c6e7b3ee1034aca",
        "3680653b3a2c4dac91c7076b08d6f70a5f97f99f",
        "d4fd9e36a739c3d533a2dc972c19f37404bbcdbe",
        "R1-B exact-transition review; PR127 CI-R4 rationale retained in origin commit",
        "Binary assets/Android seams only; no Gradle analyzer effect; independent Sonar gate required",
    ),
)

# Joint owner approval: exact R2-G parent and both reviewed test-only endpoints.
TEST_HARNESS_BASE = "d2e5afc9e1863e760ce62f43eaf024b4cf6ecaac"
TEST_HARNESS_ENDPOINTS = {
    "app/build.gradle.kts": (
        "ceec97f6cd4fd736b9058dfa3884c5bdce208916",
        "db99f595caca92cb53f482027566a3213c77ded0",
    ),
    "gradle/libs.versions.toml": (
        "c6b51b3f062af8bea539141b39d63f82268b339d",
        "cef0f4dde1afc5d33114b5701ed13752f425a783",
    ),
}
TEST_FIXTURE_ROOT = "app/src/test/java/io/github/aimindseye/tenseven/features/"
TEST_HARNESS_FIXTURES = {
    TEST_FIXTURE_ROOT + "AndroidViewFixtures.kt":
        "6ce24b549dee98604198d0e829c73e0639257c15cfb66be33b86416af971fd28",
    TEST_FIXTURE_ROOT + "calendar/CalendarNavigationBehaviorTest.kt":
        "90216fd399319fd465667c33eecce4957d78c0f0d6835fe68d4cc30ef7ef6577",
    TEST_FIXTURE_ROOT + "calendar/CalendarOptionsBehaviorTest.kt":
        "61f36315c6a7f0615276f4194489157c8361230f02c7b8d760bcc9978904180f",
    TEST_FIXTURE_ROOT + "calendar/CalendarScheduleBehaviorTest.kt":
        "3c418ae7043dc917271e3a7d68935199d8997e6cc78b3b4df07a40d77dd9ce63",
    TEST_FIXTURE_ROOT + "calendar/CalendarViewBehaviorTest.kt":
        "e5545c4ef16d66d22e05cf96c18b8ee004661e6bbdb227b019f47bcf10a5522c",
    TEST_FIXTURE_ROOT + "phone/PhoneBlocklistBehaviorTest.kt":
        "5a92fe700f293063ab02ba87f5833103584d6b264ca11bc1f7adb2cf533d8355",
    TEST_FIXTURE_ROOT + "phone/PhoneContactsBehaviorTest.kt":
        "1bd8cc7e107a578aefca002501059ac18a07a9d75217fdf6e46ee819f7886726",
    TEST_FIXTURE_ROOT + "phone/PhoneDialBehaviorTest.kt":
        "a4a12691e6aa6debda08acb210452c3e101efc9fe2bd7284377c56bda0bea46b",
    TEST_FIXTURE_ROOT + "phone/PhoneFixtureRecords.kt":
        "82987db9c73491d42dc04f90264b647b1ebc25d3a664f7a06792902706db5f97",
    TEST_FIXTURE_ROOT + "phone/PhonePermissionBehaviorTest.kt":
        "621df2ece316aaf72c0108fafab74d8f733a10721195676e36771213a2470f4d",
    TEST_FIXTURE_ROOT + "phone/PhoneRecentsBehaviorTest.kt":
        "76913543cc9e77691ea0aeef3bef2f81b18c8bbeb3cfe0d8f627fb10c1c374c4",
}


def blob_id(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data, usedforsecurity=False).hexdigest()


def source_bytes(repo, filename):
    path = repo.path / filename
    current = repo.path
    for part in Path(filename).parts:
        current = current / part
        if current.is_symlink():
            raise QualityError("Symlinked source/policy: " + filename)
    return path.read_bytes()


def verify_transition(repo, base, transition):
    # Hashes bind both endpoints AND the immutable originating commit and parent.
    actual = (repo.blob_id(base, transition.path), blob_id(source_bytes(repo, transition.path)),
              repo.blob_id(repo.parent(transition.origin), transition.path),
              repo.blob_id(transition.origin, transition.path))
    expected = (transition.before, transition.after, transition.before, transition.after)
    if actual != expected:
        raise QualityError("Unknown or modified policy transition: " + transition.path)
    if not repo.ancestor(base, transition.origin) or not repo.ancestor(transition.origin, repo.head()):
        raise QualityError("Policy transition origin is outside comparison lineage: " + transition.path)
    return asdict(transition)


def verify_protected_policy(repo, changed, base):
    verified = []
    known = {item.path: item for item in TRANSITIONS}
    protected = changed & QUALITY_CONFIG_FILES
    harness_active = test_harness_active(repo)
    if harness_active:
        verified.extend(verify_test_harness(repo, protected, base))
    handled = TEST_HARNESS_ENDPOINTS.keys() if harness_active else set()
    for filename in sorted(protected - handled):
        if filename not in known:
            raise QualityError("Quality policy modified in feature scope: " + filename)
        verified.append(verify_transition(repo, base, known[filename]))
    return verified


def _source_signature(metadata):
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o111:
        raise QualityError("Approved harness source must be a regular non-executable file")
    return (metadata.st_dev, metadata.st_ino, metadata.st_size,
            metadata.st_mtime_ns, metadata.st_ctime_ns)


def _same_source_link(parent, name, descriptor):
    named = os.stat(name, dir_fd=parent, follow_symlinks=False)
    opened = os.fstat(descriptor)
    if (named.st_dev, named.st_ino, stat.S_IFMT(named.st_mode)) != (
            opened.st_dev, opened.st_ino, stat.S_IFMT(opened.st_mode)):
        raise QualityError("Approved harness source or ancestry was replaced")


def _open_harness_source(root, filename, stack, links):
    parent = root
    parts = Path(repository_path(filename)).parts
    for part in parts[:-1]:
        child = os.open(part, DIRECTORY_FLAGS, dir_fd=parent)
        stack.callback(os.close, child)
        links.append((parent, part, child))
        parent = child
    descriptor = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=parent)
    stack.callback(os.close, descriptor)
    links.append((parent, parts[-1], descriptor))
    signature = _source_signature(os.fstat(descriptor))
    data = _descriptor_bytes(descriptor)
    return data, descriptor, signature


def _assert_harness_snapshot(repo, root, links, snapshots):
    named = repo.path.stat(follow_symlinks=False)
    opened = os.fstat(root)
    if (named.st_dev, named.st_ino, stat.S_IFMT(named.st_mode)) != (
            opened.st_dev, opened.st_ino, stat.S_IFMT(opened.st_mode)):
        raise QualityError("Approved harness repository root was replaced")
    for parent, name, descriptor in links:
        _same_source_link(parent, name, descriptor)
    for _, descriptor, signature in snapshots.values():
        if _source_signature(os.fstat(descriptor)) != signature:
            raise QualityError("Approved harness source changed during verification")


@contextmanager
def _source_snapshot(repo, paths):
    # Hold selected source inodes until verification finishes; never reopen for data.
    try:
        with ExitStack() as stack:
            root = os.open(repo.path, DIRECTORY_FLAGS)
            stack.callback(os.close, root)
            links = []
            snapshots = {path: _open_harness_source(root, path, stack, links) for path in sorted(paths)}
            _assert_harness_snapshot(repo, root, links, snapshots)
            yield {path: item[0] for path, item in snapshots.items()}
            _assert_harness_snapshot(repo, root, links, snapshots)
    except OSError as exc:
        raise QualityError("Cannot securely read approved test harness sources") from exc


@contextmanager
def _test_harness_snapshot(repo):
    paths = TEST_HARNESS_ENDPOINTS.keys() | TEST_HARNESS_FIXTURES.keys()
    with _source_snapshot(repo, paths) as snapshot:
        yield snapshot


def _committed_harness_ids(repo, commit, paths):
    # All revision values are full validated OIDs; all path values are literal data.
    names = sorted(repository_path(path) for path in paths)
    raw = repo._read(["ls-tree", "-z", object_id(commit), "--", *names])
    result = {}
    for entry in raw.split(b"\0"):
        if entry:
            metadata, filename = entry.decode().split("\t", 1)
            mode, kind, identifier = metadata.split()
            if mode != "100644" or kind != "blob":
                raise QualityError("Unexpected committed harness file mode or type")
            result[filename] = object_id(identifier)
    if set(result) != set(names):
        raise QualityError("Committed harness files are missing")
    return result


def _verify_harness_fixtures(repo, commit):
    _committed_harness_ids(repo, commit, TEST_HARNESS_FIXTURES)
    for filename, expected in TEST_HARNESS_FIXTURES.items():
        if hashlib.sha256(repo.blob(commit, filename)).hexdigest() != expected:
            raise QualityError("Committed harness fixture differs from owner approval")


def _direct_harness_origin(repo, commit):
    raw = repo._read(["rev-list", "--parents", "-n", "1", object_id(commit), "--"])
    return [object_id(value) for value in raw.decode().split()] == [commit, TEST_HARNESS_BASE]


def _harness_origin(repo, head):
    commits = _harness_commits(repo, head)
    before = {path: pair[0] for path, pair in TEST_HARNESS_ENDPOINTS.items()}
    after = {path: pair[1] for path, pair in TEST_HARNESS_ENDPOINTS.items()}
    states = {commit: _committed_harness_ids(repo, commit, before) for commit in commits}
    origins = [commit for commit in commits
               if _direct_harness_origin(repo, commit) and states[commit] == after]
    if len(origins) != 1:
        raise QualityError("Approved harness needs one immutable origin directly after its approved base")
    origin = origins[0]
    for commit in commits:
        descended = repo.ancestor(origin, commit)
        if states[commit] != (after if descended else before):
            raise QualityError("Unknown or reverted harness transition in immutable history")
        if descended:
            _verify_harness_fixtures(repo, commit)
    return origin


def _harness_commits(repo, head):
    revision = object_id(TEST_HARNESS_BASE) + ".." + object_id(head)
    raw = repo._read(["rev-list", "--ancestry-path", "--reverse", revision, "--"])
    return [object_id(line) for line in raw.decode().splitlines()]


def _harness_history_active(repo, head):
    raw = repo._read(["rev-list", object_id(head), "--"])
    if TEST_HARNESS_BASE not in {object_id(line) for line in raw.decode().splitlines()}:
        return False
    for commit in _harness_commits(repo, head):
        identifiers = _committed_harness_ids(repo, commit, TEST_HARNESS_ENDPOINTS)
        if any(identifiers[path] == pair[1] for path, pair in TEST_HARNESS_ENDPOINTS.items()):
            return True
    return False


def test_harness_active(repo):
    # Activation is source/history state, never a changed-file optimization.
    head = repo.head()
    current = _harness_worktree_active(repo)
    active = current or _harness_history_active(repo, head)
    if repo.head() != head:
        raise QualityError("Candidate HEAD changed during harness activation detection")
    return active


def _harness_worktree_active(repo):
    for path, pair in TEST_HARNESS_ENDPOINTS.items():
        if (repo.path / path).exists():
            with _source_snapshot(repo, (path,)) as snapshot:
                matches = blob_id(snapshot[path]) == pair[1]
            if matches:
                return True
    return False


def _harness_comparison_endpoints(repo, protected, base):
    before = {path: pair[0] for path, pair in TEST_HARNESS_ENDPOINTS.items()}
    after = {path: pair[1] for path, pair in TEST_HARNESS_ENDPOINTS.items()}
    if _committed_harness_ids(repo, TEST_HARNESS_BASE, before) != before:
        raise QualityError("Approved harness parent endpoint differs")
    previous = _committed_harness_ids(repo, base, before)
    if previous not in (before, after):
        raise QualityError("Unknown or partial harness comparison-base endpoints")
    if previous == before and protected & before.keys() != before.keys():
        raise QualityError("Approved harness transition requires both exact protected files")
    return previous


def verify_test_harness(repo, protected, base):
    head = repo.head()
    if not repo.ancestor(base, head) or not repo.ancestor(TEST_HARNESS_BASE, head):
        raise QualityError("Approved harness base is outside comparison lineage")
    previous = _harness_comparison_endpoints(repo, protected, base)
    with _test_harness_snapshot(repo) as snapshot:
        for path, pair in TEST_HARNESS_ENDPOINTS.items():
            if blob_id(snapshot[path]) != pair[1]:
                raise QualityError("Approved harness candidate endpoint differs")
        for path, expected_digest in TEST_HARNESS_FIXTURES.items():
            if hashlib.sha256(snapshot[path]).hexdigest() != expected_digest:
                raise QualityError("Approved harness fixture differs")
        origin = None if head == TEST_HARNESS_BASE else _harness_origin(repo, head)
    if repo.head() != head:
        raise QualityError("Candidate HEAD changed during harness verification")
    return [{"path": path, "before": pair[0], "after": pair[1], "origin": origin,
             "origin_parent": TEST_HARNESS_BASE,
             "comparison_base": base, "comparison_base_blob": previous[path],
             "verification_mode": "pre_commit" if origin is None else "immutable_origin",
             "approval": "Owner-approved R2.1 exact joint test harness and eleven fixture hashes",
             "analysis_effect": "Robolectric 4.18 JVM tests/resources only; no analyzer overlay"}
            for path, pair in TEST_HARNESS_ENDPOINTS.items()]


def verify_test_harness_analysis(repo, parent):
    if not test_harness_active(repo):
        return {}
    previous = {path: source_bytes(parent, path) for path in TEST_HARNESS_ENDPOINTS}
    current = {path: source_bytes(repo, path) for path in TEST_HARNESS_ENDPOINTS}
    base = parent.head()
    records = verify_test_harness(repo, repo.changed(base) & QUALITY_CONFIG_FILES, base)
    return {item['path']: _harness_analysis_record(item, previous[item['path']], current[item['path']])
            for item in records}


def _harness_analysis_record(item, previous, current):
    if (blob_id(previous), blob_id(current)) != (item['comparison_base_blob'], item['after']):
        raise QualityError("Analysis bytes differ from the exact approved harness endpoints")
    return {**item, 'parent_sha256': hashlib.sha256(previous).hexdigest(),
            'candidate_sha256': hashlib.sha256(current).hexdigest()}


def verify_compose(repo, changed, base, approved):
    if not changed & {ROOT_BUILD, COMPOSE_CONFIG}:
        return False
    before = repo.blob(base, ROOT_BUILD).decode()
    after = source_bytes(repo, ROOT_BUILD).decode()
    if (not approved or before.count(OLD_WIRING) != 1
            or after != before.replace(OLD_WIRING, NEW_WIRING)
            or source_bytes(repo, COMPOSE_CONFIG).decode() != COMPOSE_POLICY):
        raise QualityError("Only the explicitly approved Compose correction is supported")
    return True


def ci_bootstrap(before):
    expected = before.replace("          persist-credentials: false\n",
                              "          persist-credentials: false\n          fetch-depth: 0\n", 1)
    expected = expected.replace("      - 'tools/verify-release-apks.sh'\n",
                                "      - 'tools/verify-release-apks.sh'\n" + CI_PATHS)
    return expected.replace("      - name: Verify compiled Full APK manifest\n",
                            CI_STEP + "      - name: Verify compiled Full APK manifest\n")


def verify_workflows(repo, changed, base):
    for filename in sorted(p for p in changed if p.startswith(".github/workflows/")):
        if filename != ANDROID_CI:
            raise QualityError("Unexpected CI policy change: " + filename)
        before = repo.blob(base, filename).decode()
        expected = ci_bootstrap(before)
        if CI_STEP in before:
            expected = before.replace(OLD_CI_PATHS, CI_PATHS)
        if source_bytes(repo, filename).decode() != expected:
            raise QualityError("Unexpected CI policy change: " + filename)


MARKERS = ("@Suppress", "@file:Suppress", "//noinspection", "ktlint-disable", "detekt-disable")


def added_lines(repo, base, filename):
    if filename not in repo.tree_files(base):
        return list(enumerate(source_bytes(repo, filename).decode().splitlines(), 1))
    raw = repo.diff(base, filename)
    result, line_number = [], 0
    for line in raw.splitlines():
        header = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)", line)
        if header:
            line_number = int(header.group(1))
        elif line.startswith("+") and not line.startswith("+++"):
            result.append((line_number, line[1:]))
            line_number += 1
        elif line.startswith(" "):
            line_number += 1
    return result


def suppression_risk(line):
    if "MissingPermission" in line:
        return "high", "permission-contract-review-required"
    if "FunctionNaming" in line:
        return "medium", "compose-naming-redundancy-review-required"
    if "UnusedParameter" in line or "UNUSED_PARAMETER" in line:
        return "medium", "unused-parameter-contract-review-required"
    return "high", "unknown-suppression-review-required"


def suppression_record(repo, base, filename, number, text):
    risk, category = suppression_risk(text)
    head = repo.head()
    origin = None
    if filename in repo.tree_files(head):
        lines = repo.blob(head, filename).decode().splitlines()
        if number <= len(lines) and lines[number - 1] == text:
            origin = repo.line_origin(head, filename, number)
    inherited = origin is not None and repo.ancestor(origin, STARTING_SHA)
    return {"file": filename, "line": number, "text": text, "origin_commit": origin,
            "risk": risk, "classification": category, "inherited_before_r1": inherited,
            "origin_already_in_base": origin is not None and repo.ancestor(origin, base),
            "approval": "NOT_APPROVED", "result": "HOLD"}


def audit_suppressions(repo, changed, base):
    audit = []
    for filename in sorted(p for p in changed if p.endswith((".kt", ".kts"))):
        if not (repo.path / filename).exists():
            continue
        for number, text in added_lines(repo, base, filename):
            if any(marker in text for marker in MARKERS):
                audit.append(suppression_record(repo, base, filename, number, text))
    return audit


def review_policy(repo, changed, base, approved_compose=False):
    transitions = verify_protected_policy(repo, changed, base)
    overlay = verify_compose(repo, changed, base, approved_compose)
    verify_workflows(repo, changed, base)
    return {"verified_transitions": transitions, "compose_overlay": overlay,
            "suppression_audit": audit_suppressions(repo, changed, base)}


def _descriptor_bytes(descriptor):
    os.lseek(descriptor, 0, os.SEEK_SET)
    with os.fdopen(os.dup(descriptor), "rb") as stream:
        return stream.read()


def _replace_descriptor(descriptor, expected, replacement):
    if _descriptor_bytes(descriptor) != expected:
        raise QualityError("Parent overlay changed; refusing to overwrite unrelated work")
    os.lseek(descriptor, 0, os.SEEK_SET)
    os.ftruncate(descriptor, 0)
    with os.fdopen(os.dup(descriptor), "wb") as stream:
        stream.write(replacement)


def _assert_named_descriptor(parent, name, descriptor):
    actual = os.stat(name, dir_fd=parent, follow_symlinks=False)
    owned = os.fstat(descriptor)
    if not stat.S_ISREG(actual.st_mode) or (actual.st_dev, actual.st_ino) != (owned.st_dev, owned.st_ino):
        raise QualityError("Parent overlay file was replaced")


@contextmanager
def temporary_compose_overlay(baseline, enabled):
    if not enabled:
        yield
        return
    root = os.open(baseline, DIRECTORY_FLAGS)
    descriptors = [root]
    try:
        config = os.open("config", DIRECTORY_FLAGS, dir_fd=root)
        descriptors.append(config)
        build = os.open(ROOT_BUILD, os.O_RDWR | os.O_NOFOLLOW, dir_fd=root)
        descriptors.append(build)
        with _compose_files(root, config, build):
            yield
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


@contextmanager
def _compose_files(root, config, build):
    original = _descriptor_bytes(build)
    if original.count(OLD_WIRING.encode()) != 1:
        raise QualityError("Unknown parent Gradle wiring")
    transformed = original.replace(OLD_WIRING.encode(), NEW_WIRING.encode())
    flags = os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
    name = "detekt-tenseven.yml"
    descriptor = os.open(name, flags, mode=0o600, dir_fd=config)
    try:
        _replace_descriptor(descriptor, b"", COMPOSE_POLICY.encode())
        _assert_named_descriptor(root, ROOT_BUILD, build)
        _replace_descriptor(build, original, transformed)
        try:
            yield
        finally:
            _assert_named_descriptor(root, ROOT_BUILD, build)
            _assert_named_descriptor(config, name, descriptor)
            if _descriptor_bytes(descriptor) != COMPOSE_POLICY.encode():
                raise QualityError("Parent Compose overlay contents changed")
            _replace_descriptor(build, transformed, original)
            os.unlink(name, dir_fd=config)
    finally:
        os.close(descriptor)
