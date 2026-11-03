#!/usr/bin/env python3
"""Compare Lint/detekt/ktlint XML findings without granting file-renaming exemptions."""
from collections import Counter
from pathlib import Path
import argparse
import re
import sys
import xml.etree.ElementTree as ET

TOOLS = {"lint", "detekt", "ktlint"}


def anchor(root, filename, line):
    path = Path(filename)
    if not path.is_absolute():
        path = root / path
    try:
        text = path.read_text().splitlines()[max(int(line or 1) - 1, 0)]
    except (OSError, IndexError, ValueError):
        return "<no-source>"
    # Moving a declaration between cohesive files must not manufacture a new legacy finding.
    text = re.sub(r"\b(private|internal)\b", "<visibility>", text)
    return re.sub(r"\s+", " ", text).strip()


def findings(root):
    root = Path(root).resolve()
    result = Counter()
    seen = set()
    for report in root.glob("*/build/reports/**/*.xml"):
        relative = str(report.relative_to(root)).lower()
        tool = next((tool for tool in sorted(TOOLS, key=len, reverse=True) if tool in relative), None)
        if not tool or (tool == "ktlint" and "format" in relative):
            continue
        document = ET.parse(report).getroot()
        if document.tag == "issues" and tool == "lint":
            seen.add(tool)
            for issue in document.findall("issue"):
                if issue.get("severity") in {"Information", "Ignore"}:
                    continue
                location = issue.find("location")
                if location is not None:
                    result[(tool, issue.get("id"), issue.get("message"),
                            anchor(root, location.get("file", ""), location.get("line")))] += 1
        elif document.tag == "checkstyle":
            seen.add(tool)
            for file in document.findall("file"):
                for error in file.findall("error"):
                    result[(tool, error.get("source"), error.get("message"),
                            anchor(root, file.get("name", ""), error.get("line")))] += 1
    missing = TOOLS - seen
    if missing:
        raise ValueError("Required reports missing: " + ", ".join(sorted(missing)))
    return result


def compare(baseline, current):
    return current - baseline


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    args = parser.parse_args()
    try:
        old, new = findings(args.baseline), findings(args.current)
    except (ValueError, ET.ParseError) as error:
        print(f"Quality ratchet cannot validate reports: {error}", file=sys.stderr)
        return 2
    added = compare(old, new)
    print(f"Baseline findings: {sum(old.values())}; current findings: {sum(new.values())}")
    for (tool, rule, message, source), count in sorted(added.items()):
        print(f"NEW {tool} {rule} ({count}): {message}\n  {source}")
    if added:
        print("Quality ratchet FAILED: fix new findings; do not update the baseline to hide them.")
        return 1
    print("Quality ratchet PASSED: no new Lint/detekt/ktlint findings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
