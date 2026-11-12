#!/usr/bin/env python3
"""Compare fresh real Gradle reports against a pinned, detached parent worktree.

A successful Gradle exit alone never qualifies a deliverable. Report-only mode
is diagnostic and always returns HOLD. Remote and device gates are separate.
"""

import argparse
from collections import Counter, defaultdict
from difflib import SequenceMatcher
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

QUALITY_CONFIG_FILES = {
    ".editorconfig", "config/detekt.yml", "config/DETEKT_PROVENANCE.env",
    "gradle.properties", "sonar-project.properties", "settings.gradle.kts",
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar", "app/build.gradle.kts", "design/build.gradle.kts",
}
COMPOSE_CONFIG = "config/detekt-tenseven.yml"
COMPOSE_POLICY = "naming:\n  FunctionNaming:\n    ignoreAnnotated:\n      - Composable\n"
OLD_WIRING = '        config.setFrom(rootProject.file("config/detekt.yml"))\n'
NEW_WIRING = (
    '        config.setFrom(\n'
    '            rootProject.file("config/detekt.yml"),\n'
    '            rootProject.file("config/detekt-tenseven.yml"),\n'
    '        )\n'
)
CI_PATHS = (
    "      - 'tools/deliverable_quality_gate.py'\n"
    "      - 'tools/tests/**'\n"
)
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
QUALITY_TASKS = (":design:lintDebug", ":app:lintDebug", "detekt", "ktlintCheck")
GRADLE_TASKS = (
    ":design:testDebugUnitTest", ":app:testDebugUnitTest",
    ":app:assembleDebug", ":app:assembleRelease", *QUALITY_TASKS,
    ":design:koverXmlReportDebug", ":app:koverXmlReportDebug",
)
REPORT_FAMILIES = ("detekt", "ktlint", "lint")
METRICS = {
    "detekt.LongMethod": r"(?<=too long \()\d+",
    "detekt.CyclomaticComplexMethod": r"(?<=complexity: )\d+",
}


class QualityError(ValueError):
    """Missing evidence or an unauthorized quality policy change."""


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, check=False)
    if result.returncode:
        raise QualityError("git check failed: " + " ".join(args) + "\n" + result.stderr)
    return result.stdout.rstrip("\n")


def changed_paths(repo: Path, base_ref: str) -> set[str]:
    git(repo, "merge-base", "--is-ancestor", base_ref, "HEAD")
    tracked = git(repo, "diff", "--name-only", "--no-renames", "--diff-filter=ACMD", "-z", base_ref, "--")
    untracked = git(repo, "ls-files", "--others", "--exclude-standard", "-z")
    return set(filter(None, (tracked + "\0" + untracked).split("\0")))


def relative_path(repo: Path, source: str) -> str:
    if not source:
        raise QualityError("Finding has no source path")
    path = Path(source)
    path = path if path.is_absolute() else repo / path
    try:
        return path.resolve().relative_to(repo.resolve()).as_posix()
    except ValueError as exc:
        raise QualityError("Finding points outside analyzed repository: " + source) from exc


def parse_xml_report(repo: Path, path: Path, family: str) -> list[dict]:
    root = ET.parse(path).getroot()
    findings = []

    def append(source, line, column, rule, message, severity):
        if not rule or message is None or not severity:
            raise QualityError("Malformed finding in " + str(path))
        try:
            line_number, column_number = int(line or 0), int(column or 0)
        except ValueError as exc:
            raise QualityError("Malformed finding location in " + str(path)) from exc
        if line_number < 0 or column_number < 0:
            raise QualityError("Negative finding location in " + str(path))
        filename = relative_path(repo, source)
        source_path = repo / filename
        if not source_path.is_file() or source_path.is_symlink():
            raise QualityError("Finding source unavailable: " + filename)
        if line_number and line_number > len(source_path.read_text(encoding="utf-8").splitlines()):
            raise QualityError("Finding line outside source: " + filename)
        findings.append({
            "family": family, "file": filename, "line": line_number, "column": column_number,
            "rule": rule, "message": message.replace(str(repo), "<repo>"), "severity": severity,
            "report": path.relative_to(repo).as_posix(),
        })

    if root.tag == "checkstyle" and family in {"detekt", "ktlint"}:
        for file_element in root:
            if file_element.tag != "file":
                raise QualityError("Unknown checkstyle element in " + str(path))
            for error in file_element:
                if error.tag != "error" or not error.get("line"):
                    raise QualityError("Malformed checkstyle error in " + str(path))
                append(file_element.get("name"), error.get("line"), error.get("column"),
                       error.get("source"), error.get("message"), error.get("severity"))
    elif root.tag == "issues" and family == "lint":
        for issue in root:
            if issue.tag != "issue":
                raise QualityError("Unknown lint element in " + str(path))
            # Secondary locations explain the same issue; count the primary once.
            location = issue.find("location")
            if location is None:
                raise QualityError("Lint issue without primary location in " + str(path))
            append(location.get("file"), location.get("line"), location.get("column"),
                   issue.get("id"), issue.get("message"), issue.get("severity"))
    else:
        raise QualityError(f"Unexpected {family} XML schema: {path}: {root.tag}")
    return findings


def report_paths(repo: Path) -> dict[str, list[Path]]:
    result = {family: [] for family in REPORT_FAMILIES}
    modules = sorted(item.parent for item in repo.glob("*/build.gradle.kts"))
    if {module.name for module in modules} != {"app", "design"}:
        raise QualityError("Module inventory changed; review required report matrix")
    for module in modules:
        reports = module / "build/reports"
        result["detekt"].append(reports / "detekt/detekt.xml")
        result["lint"].append(reports / "lint-results-debug.xml")
        tasks = {"ktlintKotlinScriptCheck"}
        for source_set in (module / "src").iterdir():
            if source_set.is_dir() and any(source_set.rglob("*.kt")):
                tasks.add("ktlint" + source_set.name[0].upper() + source_set.name[1:] + "SourceSetCheck")
        for task in sorted(tasks):
            result["ktlint"].append(reports / f"ktlint/{task}/{task}.xml")
    return result


def check_quality_reports(repo: Path, started_at: float) -> tuple[list[dict], list[dict]]:
    findings, inventory = [], []
    for family, paths in report_paths(repo).items():
        for report in paths:
            if not report.is_file() or report.is_symlink():
                raise QualityError("Missing required report: " + str(report))
            if report.stat().st_mtime < started_at:
                raise QualityError("Stale report: " + str(report))
            findings.extend(parse_xml_report(repo, report, family))
            inventory.append({"family": family, "path": report.relative_to(repo).as_posix(),
                              "sha256": hashlib.sha256(report.read_bytes()).hexdigest(),
                              "mtime_ns": report.stat().st_mtime_ns})
    return findings, inventory


def compare_findings(baseline: list[dict], current: list[dict], base_repo: Path, repo: Path):
    """Match multiplicity, same path/report, unchanged line, rule/message/severity.

    Lines may shift. New files cannot borrow old debt. Quantitative debt may
    decrease but never increase. No rule (including Compose naming) is waived.
    """
    maps = {}

    def old_line(item):
        filename = item["file"]
        if filename not in maps:
            old, new = base_repo / filename, repo / filename
            if not old.is_file() or old.is_symlink():
                maps[filename] = {}
            else:
                mapping = {}
                old_bytes, new_bytes = old.read_bytes(), new.read_bytes()
                if old_bytes == new_bytes:
                    mapping[0] = 0
                try:
                    old_text, new_text = old_bytes.decode("utf-8").splitlines(), new_bytes.decode("utf-8").splitlines()
                except UnicodeDecodeError:
                    old_text, new_text = [], []  # Binary asset issues have file-level locations.
                for a, b, size in SequenceMatcher(None, old_text, new_text, autojunk=False).get_matching_blocks():
                    mapping.update((b + i + 1, a + i + 1) for i in range(size))
                maps[filename] = mapping
        return maps[filename].get(item["line"])

    def key(item, line):
        message = item["message"]
        match = re.search(METRICS.get(item["rule"], r"(?!)"), message)
        metric = int(match.group()) if match else None
        if match:
            message = message[:match.start()] + "$METRIC$" + message[match.end():]
        return (item["family"], item["report"], item["file"], line, item["column"],
                item["rule"], message, item["severity"]), metric

    available = defaultdict(list)
    for item in baseline:
        identity, metric = key(item, item["line"])
        available[identity].append(metric)
    added, existing = [], []
    for item in current:
        line = old_line(item)
        identity, metric = key(item, line)
        choices = [value for value in available[identity]
                   if value == metric or (value is not None and metric is not None and value >= metric)]
        if line is not None and choices:
            value = min(choices, key=lambda value: -1 if value is None else value)
            available[identity].remove(value)
            existing.append({**item, "baseline_line": line, "baseline_metric": value})
        else:
            added.append(item)
    return added, existing


def assert_no_quality_bypass(changed: set[str], repo: Path, base_ref: str, approved_compose=False):
    protected = changed & QUALITY_CONFIG_FILES
    if protected:
        raise QualityError("Quality policy modified in feature scope: " + ", ".join(sorted(protected)))
    if "build.gradle.kts" in changed or COMPOSE_CONFIG in changed:
        before = git(repo, "show", f"{base_ref}:build.gradle.kts") + "\n"
        after = (repo / "build.gradle.kts").read_text()
        if (not approved_compose or before.count(OLD_WIRING) != 1
                or after != before.replace(OLD_WIRING, NEW_WIRING)
                or (repo / COMPOSE_CONFIG).read_text() != COMPOSE_POLICY):
            raise QualityError("Only the explicitly approved Compose naming correction is supported")
    for entry in changed:
        if entry.startswith(".github/workflows/"):
            before = git(repo, "show", f"{base_ref}:{entry}") + "\n"
            after = (repo / entry).read_text()
            expected = before.replace("          persist-credentials: false\n",
                                      "          persist-credentials: false\n          fetch-depth: 0\n", 1)
            expected = expected.replace("      - 'tools/verify-release-apks.sh'\n",
                                        "      - 'tools/verify-release-apks.sh'\n" + CI_PATHS)
            expected = expected.replace("      - name: Verify compiled Full APK manifest\n",
                                        CI_STEP + "      - name: Verify compiled Full APK manifest\n")
            if entry != ".github/workflows/android.yml" or after != expected:
                raise QualityError("Unexpected CI policy change: " + entry)
        if entry.endswith((".kt", ".kts")):
            path = repo / entry
            if not path.exists():
                continue  # Deletions cannot contain added suppressions.
            if path.is_symlink():
                raise QualityError("Symlinked Kotlin source: " + entry)
            diff = git(repo, "diff", "--unified=0", base_ref, "--", entry)
            lines = [line[1:] for line in diff.splitlines() if line.startswith("+") and not line.startswith("+++")]
            if not diff:
                lines = path.read_text().splitlines() if entry not in git(repo, "ls-tree", "-r", "--name-only", base_ref).splitlines() else []
            if any(marker in line for line in lines for marker in
                   ("@Suppress", "@file:Suppress", "//noinspection", "ktlint-disable", "detekt-disable")):
                raise QualityError("Added source suppression: " + entry)


def source_digest(repo: Path) -> str:
    paths = git(repo, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0")
    digest = hashlib.sha256()
    for name in sorted(set(filter(None, paths))):
        path = repo / name
        digest.update(name.encode() + b"\0")
        digest.update(path.read_bytes() if path.is_file() else b"<deleted>")
    return digest.hexdigest()


def run_gradle(repo: Path, out: Path, tasks: tuple) -> tuple[int, float]:
    command = [str(repo / "gradlew"), "--no-daemon", "--console=plain", "--continue",
               "--rerun-tasks", "--no-build-cache", "-Ptenseven.strictQuality=false", *tasks]
    (out / "command.json").write_text(json.dumps({"cwd": str(repo), "argv": command}, indent=2) + "\n")
    started_at = time.time()
    print("Running Gradle; log:", out / "gradle.log", flush=True)
    with (out / "gradle.log").open("w") as log:
        result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT, check=False)
    (out / "exit-code.txt").write_text(str(result.returncode) + "\n")
    return result.returncode, started_at


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--out", required=True, type=Path, help="New evidence directory outside repository")
    parser.add_argument("--baseline-repo", type=Path, help="Existing clean detached worktree at base-ref")
    parser.add_argument("--reports-only", action="store_true", help="Diagnostic only; always HOLD")
    parser.add_argument("--approved-compose-policy", action="store_true", help="Apply the reviewed narrow correction equally to parent analysis")
    args = parser.parse_args()
    repo, out = args.repo.resolve(strict=True), args.out.resolve()
    if out.exists() or out == repo or repo in out.parents or out in repo.parents:
        raise QualityError("Output must be new and disjoint from repository")
    base_sha = git(repo, "rev-parse", args.base_ref + "^{commit}")
    changed = changed_paths(repo, base_sha)
    if not changed:
        raise QualityError("No changed paths; refusing misleading qualification")
    assert_no_quality_bypass(changed, repo, base_sha, args.approved_compose_policy)
    out.mkdir(parents=True)
    result = {"kind": "TenSeven_Deliverable_Quality_Gate_v2", "base_ref": base_sha,
              "head": git(repo, "rev-parse", "HEAD"), "changed_paths": sorted(changed),
              "reports_only": args.reports_only, "result": "HOLD",
              "remote_and_device_gates": "MUST_PASS_SEPARATELY"}
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
    java_version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    result["toolchain"] = {"java": java_version.stderr.strip(), "python": sys.version,
                           "JAVA_HOME": os.environ.get("JAVA_HOME"),
                           "ANDROID_HOME": os.environ.get("ANDROID_HOME")}
    try:
        baseline = args.baseline_repo.resolve() if args.baseline_repo else out / "baseline-source"
        if not args.baseline_repo:
            git(repo, "worktree", "add", "--detach", str(baseline), base_sha)
        if (baseline == repo or git(baseline, "rev-parse", "HEAD") != base_sha
                or git(baseline, "status", "--porcelain") or git(baseline, "branch", "--show-current")):
            raise QualityError("Baseline must be a clean detached worktree at the exact base SHA")
        # Never edit source modules or upstream policy in the baseline. This one
        # reviewed configuration correction must be identical on both sides.
        overlay = args.approved_compose_policy and COMPOSE_CONFIG in changed
        result["analysis_overlay"] = {"applied_to_parent": overlay,
                                      "policy": COMPOSE_POLICY if overlay else None,
                                      "gradle_wiring": NEW_WIRING if overlay else None}
        if overlay:
            (baseline / COMPOSE_CONFIG).write_text(COMPOSE_POLICY)
            path = baseline / "build.gradle.kts"
            path.write_text(path.read_text().replace(OLD_WIRING, NEW_WIRING))
        try:
            for label, root, tasks in (("baseline", baseline, QUALITY_TASKS), ("candidate", repo, GRADLE_TASKS)):
                evidence = out / label
                evidence.mkdir()
                before = source_digest(root)
                rc, started = (None, 0) if args.reports_only else run_gradle(root, evidence, tasks)
                findings, inventory = check_quality_reports(root, started)
                if before != source_digest(root):
                    raise QualityError("Sources changed during analysis: " + label)
                result[label] = {"gradle_rc": rc, "source_sha256": before,
                                 "findings": findings, "reports": inventory,
                                 "counts": dict(Counter(item["family"] for item in findings))}
                for entry in inventory:
                    source = root / entry["path"]
                    target = evidence / entry["path"]
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, target)
            added, existing = compare_findings(result["baseline"]["findings"], result["candidate"]["findings"], baseline, repo)
            result["new_findings"], result["existing_findings"] = added, existing
            result["new_counts"] = {family: sum(item["family"] == family for item in added) for family in REPORT_FAMILIES}
            if not args.reports_only and not added and all(result[label]["gradle_rc"] == 0 for label in ("baseline", "candidate")):
                result["result"] = "PASS_NO_NEW_FINDINGS"
        finally:
            if overlay:
                # Reverse only the exact edits made above in our detached worktree.
                path = baseline / "build.gradle.kts"
                path.write_text(path.read_text().replace(NEW_WIRING, OLD_WIRING))
                (baseline / COMPOSE_CONFIG).unlink()
    except (QualityError, OSError, ET.ParseError, UnicodeError) as exc:
        result["blocker"] = str(exc)
    (out / "QUALITY_GATE.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({key: result[key] for key in ("result", "new_counts", "blocker") if key in result}, sort_keys=True))
    print("Evidence:", out)
    return 0 if result["result"] == "PASS_NO_NEW_FINDINGS" else 2 if "blocker" in result else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (QualityError, OSError, ET.ParseError, UnicodeError) as exc:
        print("QUALITY_GATE_HOLD: " + str(exc), file=sys.stderr)
        sys.exit(2)
