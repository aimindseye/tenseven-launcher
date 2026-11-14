"""Exact historical policy transitions, never finding or filename exemptions."""

from dataclasses import asdict, dataclass
from contextlib import contextmanager
import hashlib
import os
import stat
from pathlib import Path
import re

from quality_gate_runtime import DIRECTORY_FLAGS, QualityError

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
    for filename in sorted(changed & QUALITY_CONFIG_FILES):
        if filename not in known:
            raise QualityError("Quality policy modified in feature scope: " + filename)
        verified.append(verify_transition(repo, base, known[filename]))
    return verified


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
