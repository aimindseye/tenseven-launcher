#!/usr/bin/env python3
"""Fail-closed parent-aware qualification; remote and device gates are separate."""

import argparse
from collections import Counter, defaultdict
from difflib import SequenceMatcher
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from quality_gate_policy import (
    review_policy, source_bytes, temporary_compose_overlay,
)
from quality_gate_runtime import EvidenceDirectory, GitRepository, QualityError

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


def changed_paths(repo, base_ref):
    git = GitRepository(repo)
    base = git.resolve(base_ref)
    if not git.ancestor(base, git.head()):
        raise QualityError("Base must be an ancestor of HEAD")
    return git.changed(base)


def relative_path(repo, source):
    if not source:
        raise QualityError("Finding has no source path")
    path = Path(source)
    path = path if path.is_absolute() else repo / path
    try:
        return path.resolve().relative_to(repo.resolve()).as_posix()
    except ValueError as exc:
        raise QualityError("Finding points outside analyzed repository") from exc


def finding_record(repo, report, family, source, line, column, rule, message, severity):
    if not rule or message is None or not severity:
        raise QualityError("Malformed finding in " + str(report))
    try:
        number, col = int(line or 0), int(column or 0)
    except ValueError as exc:
        raise QualityError("Malformed finding location") from exc
    if number < 0 or col < 0:
        raise QualityError("Negative finding location")
    filename = relative_path(repo, source)
    path = repo / filename
    if not path.is_file() or path.is_symlink():
        raise QualityError("Finding source unavailable: " + filename)
    if number and number > len(path.read_text().splitlines()):
        raise QualityError("Finding line outside source: " + filename)
    return {"family": family, "file": filename, "line": number, "column": col,
            "rule": rule, "message": message.replace(str(repo), "<repo>"), "severity": severity,
            "report": report.relative_to(repo).as_posix()}


def checkstyle_findings(repo, report, family, root):
    findings = []
    for file_element in root:
        if file_element.tag != "file":
            raise QualityError("Unknown checkstyle element")
        for error in file_element:
            if error.tag != "error" or not error.get("line"):
                raise QualityError("Malformed checkstyle error")
            findings.append(finding_record(
                repo, report, family, file_element.get("name"), error.get("line"), error.get("column"),
                error.get("source"), error.get("message"), error.get("severity"),
            ))
    return findings


def lint_findings(repo, report, root):
    findings = []
    for issue in root:
        if issue.tag != "issue":
            raise QualityError("Unknown lint element")
        location = issue.find("location")
        if location is None:
            raise QualityError("Lint issue without primary location")
        findings.append(finding_record(
            repo, report, "lint", location.get("file"), location.get("line"), location.get("column"),
            issue.get("id"), issue.get("message"), issue.get("severity"),
        ))
    return findings


def parse_xml_report(repo, path, family, snapshot=None):
    stream = path if snapshot is None else io.BytesIO(snapshot)
    root = ET.parse(stream).getroot()
    if root.tag == "checkstyle" and family in {"detekt", "ktlint"}:
        return checkstyle_findings(repo, path, family, root)
    if root.tag == "issues" and family == "lint":
        return lint_findings(repo, path, root)
    raise QualityError(f"Unexpected {family} XML schema: {path}: {root.tag}")


def report_paths(repo):
    result = {family: [] for family in REPORT_FAMILIES}
    modules = sorted(item.parent for item in repo.glob("*/build.gradle.kts"))
    if {module.name for module in modules} != {"app", "design"}:
        raise QualityError("Module inventory changed; review required report matrix")
    for module in modules:
        reports = module / "build/reports"
        result["detekt"].append(reports / "detekt/detekt.xml")
        result["lint"].append(reports / "lint-results-debug.xml")
        for task in ktlint_tasks(module):
            result["ktlint"].append(reports / f"ktlint/{task}/{task}.xml")
    return result


def ktlint_tasks(module):
    tasks = {"ktlintKotlinScriptCheck"}
    for source_set in (module / "src").iterdir():
        if source_set.is_dir() and any(source_set.rglob("*.kt")):
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", source_set.name):
                raise QualityError("Unknown source-set name")
            tasks.add("ktlint" + source_set.name[0].upper() + source_set.name[1:] + "SourceSetCheck")
    return sorted(tasks)


def check_quality_reports(repo, started_at):
    findings, inventory = [], []
    for family, paths in report_paths(repo).items():
        for report in paths:
            if not report.is_file() or report.is_symlink():
                raise QualityError("Missing required report: " + str(report))
            if report.stat().st_mtime < started_at:
                raise QualityError("Stale report: " + str(report))
            snapshot = report.read_bytes()
            findings.extend(parse_xml_report(repo, report, family, snapshot))
            inventory.append({"family": family, "path": report.relative_to(repo).as_posix(),
                              "sha256": hashlib.sha256(snapshot).hexdigest(),
                              "mtime_ns": report.stat().st_mtime_ns})
    return findings, inventory


def line_mapping(old, new):
    if not old.is_file() or old.is_symlink():
        return {}
    before, after = old.read_bytes(), new.read_bytes()
    mapping = {0: 0} if before == after else {}
    try:
        old_lines, new_lines = before.decode().splitlines(), after.decode().splitlines()
    except UnicodeDecodeError:
        return mapping
    for a, b, size in SequenceMatcher(None, old_lines, new_lines, autojunk=False).get_matching_blocks():
        mapping.update((b + offset + 1, a + offset + 1) for offset in range(size))
    return mapping


def finding_identity(item, line):
    message = item["message"]
    match = re.search(METRICS.get(item["rule"], r"(?!)"), message)
    metric = None
    if match:
        metric = int(match.group())
        message = message[:match.start()] + "$METRIC$" + message[match.end():]
    return (item["family"], item["report"], item["file"], line, item["column"],
            item["rule"], message, item["severity"]), metric


def matching_metric(available, metric):
    choices = [value for value in available if value == metric or
               (value is not None and metric is not None and value >= metric)]
    if not choices:
        return False, None
    return True, min(choices, key=lambda value: -1 if value is None else value)


def compare_findings(baseline, current, base_repo, repo):
    available, mappings = defaultdict(list), {}
    for item in baseline:
        identity, metric = finding_identity(item, item["line"])
        available[identity].append(metric)
    added, existing = [], []
    for item in current:
        filename = item["file"]
        if filename not in mappings:
            mappings[filename] = line_mapping(base_repo / filename, repo / filename)
        line = mappings[filename].get(item["line"])
        identity, metric = finding_identity(item, line)
        found, value = matching_metric(available[identity], metric)
        if line is None or not found:
            added.append(item)
            continue
        available[identity].remove(value)
        existing.append({**item, "baseline_line": line, "baseline_metric": value})
    return added, existing


def assert_no_quality_bypass(changed, repo, base_ref, approved_compose=False):
    git = GitRepository(repo)
    review = review_policy(git, changed, git.resolve(base_ref), approved_compose)
    if review["suppression_audit"]:
        raise QualityError("Added source suppression requires review")


def source_digest(repo):
    git = GitRepository(repo)
    digest = hashlib.sha256()
    for name in git.files():
        path = git.path / name
        digest.update(name.encode() + b"\0")
        digest.update(source_bytes(git, name) if path.exists() else b"<deleted>")
    return digest.hexdigest()


def write_json(evidence, name, value):
    evidence.write(name, (json.dumps(value, indent=2, sort_keys=True) + "\n").encode())


def run_gradle(repo, evidence, tasks, wrapper_repo):
    command = [str(wrapper_repo / "gradlew"), "--project-dir", str(repo), "--no-daemon",
               "--console=plain", "--continue", "--rerun-tasks", "--no-build-cache",
               "-Ptenseven.strictQuality=false", *tasks]
    write_json(evidence, "command.json", {"cwd": str(repo), "argv": command})
    started_at = time.time()
    print("Running Gradle; log:", evidence.path / "gradle.log", flush=True)
    with evidence.open_file("gradle.log") as log:
        result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT, check=False)
    evidence.assert_linked()
    evidence.write("exit-code.txt", (str(result.returncode) + "\n").encode())
    return result.returncode, started_at


def analyze(root, evidence, tasks, wrapper_repo, diagnostic):
    git = GitRepository(root)
    before, head = source_digest(root), git.head()
    rc, started = (None, 0) if diagnostic else run_gradle(root, evidence, tasks, wrapper_repo)
    findings, inventory = check_quality_reports(root, started)
    if before != source_digest(root) or head != git.head():
        raise QualityError("Sources or HEAD changed during analysis")
    for entry in inventory:
        snapshot = (root / entry["path"]).read_bytes()
        if hashlib.sha256(snapshot).hexdigest() != entry["sha256"]:
            raise QualityError("Report changed after parsing: " + entry["path"])
        evidence.archive(entry["path"], snapshot)
    return {"gradle_rc": rc, "source_sha256": before, "findings": findings, "reports": inventory,
            "counts": dict(Counter(item["family"] for item in findings))}



def prepare_baseline(repo, base, evidence, supplied):
    baseline = supplied
    if baseline is None:
        evidence.assert_linked()
        baseline = evidence.path / "baseline-source"
        repo.detached_worktree(baseline, base)
    git = GitRepository(baseline)
    if git.path == repo.path or git.head() != base or git.status() or git.branch():
        raise QualityError("Baseline must be clean, detached and at the exact base SHA")
    return git.path


def toolchain():
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        raise QualityError("Java is unavailable")
    result = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    return {"java": result.stderr.strip(), "python": sys.version,
            "JAVA_HOME": os.environ.get("JAVA_HOME"), "ANDROID_HOME": os.environ.get("ANDROID_HOME")}


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--out", required=True, help="Fresh directory under runtime temp, RUNNER_TEMP or Downloads")
    parser.add_argument("--baseline-repo", type=Path)
    parser.add_argument("--reports-only", action="store_true", help="Diagnostic only; always HOLD")
    parser.add_argument("--approved-compose-policy", action="store_true")
    return parser.parse_args()


def apply_comparison(result, baseline, repo):
    added, existing = compare_findings(result["baseline"]["findings"], result["candidate"]["findings"], baseline, repo)
    result["new_findings"], result["existing_findings"] = added, existing
    result["new_counts"] = {family: sum(item["family"] == family for item in added) for family in REPORT_FAMILIES}
    successful_runs = all(result[label]["gradle_rc"] == 0 for label in ("baseline", "candidate"))
    if not result["reports_only"] and not added and not result["policy"]["suppression_audit"] and successful_runs:
        result["result"] = "PASS_NO_NEW_FINDINGS"


def analysis_configuration(repo, baseline):
    shared = (".editorconfig", "config/detekt.yml", "config/DETEKT_PROVENANCE.env",
              "gradle.properties", "gradle/libs.versions.toml", "settings.gradle.kts",
              "app/build.gradle.kts", "design/build.gradle.kts", "gradlew",
              "gradle/wrapper/gradle-wrapper.jar")
    parent = GitRepository(baseline)
    identities = {}
    for name in shared:
        current, previous = source_bytes(repo, name), source_bytes(parent, name)
        if current != previous:
            raise QualityError("Incompatible analysis configuration/tooling: " + name)
        identities[name] = hashlib.sha256(current).hexdigest()
    wrapper = "gradle/wrapper/gradle-wrapper.properties"
    return {"wrapper_repo": str(repo.path), "parent_wrapper_unchanged": True,
            "shared_input_sha256": identities,
            "candidate_wrapper_sha256": hashlib.sha256(source_bytes(repo, wrapper)).hexdigest(),
            "original_parent_wrapper_sha256": hashlib.sha256(source_bytes(parent, wrapper)).hexdigest(),
            "original_parent_source_sha256": source_digest(baseline)}


def assert_candidate_unchanged(repo, result):
    if repo.head() != result["head"] or source_digest(repo.path) != result["candidate_start_source_sha256"]:
        raise QualityError("Candidate source or HEAD changed across qualification phases")
    if repo.changed(result["base_ref"]) != set(result["changed_paths"]):
        raise QualityError("Candidate changed-path inventory changed across qualification phases")


def qualify(args, repo, base, evidence, changed):
    result = {"kind": "TenSeven_Deliverable_Quality_Gate_v3", "base_ref": base, "head": repo.head(),
              "changed_paths": sorted(changed), "reports_only": args.reports_only, "result": "HOLD",
              "remote_and_device_gates": "MUST_PASS_SEPARATELY",
              "candidate_start_source_sha256": source_digest(repo.path)}
    try:
        if not repo.ancestor(base, result["head"]):
            raise QualityError("Base is no longer an ancestor of candidate HEAD")
        result["toolchain"] = toolchain()
        result["policy"] = review_policy(repo, changed, base, args.approved_compose_policy)
        baseline = prepare_baseline(repo, base, evidence, args.baseline_repo)
        result["analysis_configuration"] = analysis_configuration(repo, baseline)
        result["analysis_configuration"]["compose_overlay"] = result["policy"]["compose_overlay"]
        with temporary_compose_overlay(baseline, result["policy"]["compose_overlay"]):
            for label, root, tasks in (("baseline", baseline, QUALITY_TASKS), ("candidate", repo.path, GRADLE_TASKS)):
                child = evidence.child(label)
                try:
                    result[label] = analyze(root, child, tasks, repo.path, args.reports_only)
                finally:
                    child.close()
                assert_candidate_unchanged(repo, result)
            apply_comparison(result, baseline, repo.path)
        assert_candidate_unchanged(repo, result)
        if source_digest(baseline) != result["analysis_configuration"]["original_parent_source_sha256"]:
            raise QualityError("Parent source changed outside the approved overlay")
    except (QualityError, OSError, ET.ParseError, UnicodeError) as exc:
        result["result"] = "HOLD"
        result["blocker"] = str(exc)
    write_json(evidence, "QUALITY_GATE.json", result)
    return result


def result_code(result):
    if "blocker" in result:
        return 2
    if result["result"] == "PASS_NO_NEW_FINDINGS":
        return 0
    return 1


def main():
    args = parse_arguments()
    repo = GitRepository(args.repo)
    base = repo.resolve(args.base_ref)
    if not repo.ancestor(base, repo.head()):
        raise QualityError("Base must be an ancestor of HEAD")
    changed = repo.changed(base)
    if not changed:
        raise QualityError("No changed paths; refusing misleading qualification")
    evidence = EvidenceDirectory.reserve(args.out, repo.path)
    try:
        result = qualify(args, repo, base, evidence, changed)
        print(json.dumps({key: result[key] for key in ("result", "new_counts", "blocker") if key in result}, sort_keys=True))
        print("Evidence:", evidence.path)
        return result_code(result)
    finally:
        evidence.close()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (QualityError, OSError, ET.ParseError, UnicodeError) as exc:
        print("QUALITY_GATE_HOLD: " + str(exc), file=sys.stderr)
        sys.exit(2)
