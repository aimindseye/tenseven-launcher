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
    declaration = re.match(
        r"^\s*(?:(?:private|internal|public|protected|override|tailrec|inline|suspend|operator|infix|data|enum|sealed|abstract|open|final)\s+)*"
        r"(fun|class|interface|object)\s+([^\s(<]+)", text)
    if declaration:
        return " ".join(declaration.groups())
    text = re.sub(r"\b(private|internal)\b", "<visibility>", text)
    return re.sub(r"\s+", " ", text).strip()


# Existing quantitative debt may improve, but may never worsen. Thresholds remain in the identity.
METRICS = {
    "detekt.LongMethod": r"(?<=too long \()\d+",
    "detekt.CyclomaticComplexMethod": r"(?<=complexity: )\d+",
}


def finding(tool, rule, message, source):
    if not rule or message is None:
        raise ValueError("Malformed finding: missing rule or message")
    expression = METRICS.get(rule) if tool == "detekt" else None
    match = re.search(expression, message) if expression else None
    if match:
        value = int(match.group())
        message = message[:match.start()] + "$METRIC$" + message[match.end():]
    else:
        value = None
    return tool, rule, message, source, value


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
                source = anchor(root, location.get("file", ""), location.get("line")) if location is not None else "<project>"
                result[finding(tool, issue.get("id"), issue.get("message"), source)] += 1
        elif document.tag == "checkstyle":
            seen.add(tool)
            for file in document.findall("file"):
                for error in file.findall("error"):
                    result[finding(tool, error.get("source"), error.get("message"),
                                   anchor(root, file.get("name", ""), error.get("line")))] += 1
    missing = TOOLS - seen
    if missing:
        raise ValueError("Required reports missing: " + ", ".join(sorted(missing)))
    return result


def compare(baseline, current):
    matched = baseline & current
    available = baseline - matched
    pending = current - matched
    added = Counter()
    for key, count in sorted(pending.items(), key=lambda item: item[0][4] if len(item[0]) > 4 and item[0][4] is not None else -1, reverse=True):
        for _ in range(count):
            candidates = []
            if len(key) > 4 and key[4] is not None:
                candidates = [old for old in available if available[old] > 0 and len(old) > 4
                              and old[:4] == key[:4] and old[4] is not None and old[4] >= key[4]]
            if candidates:
                available[min(candidates, key=lambda old: old[4])] -= 1
            else:
                added[key] += 1
    return added


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
    for (tool, rule, message, source, metric), count in sorted(added.items()):
        display = message.replace("$METRIC$", str(metric)) if metric is not None else message
        print(f"NEW {tool} {rule} ({count}): {display}\n  {source}")
    if added:
        print("Quality ratchet FAILED: fix new findings; do not update the baseline to hide them.")
        return 1
    print("Quality ratchet PASSED: no new Lint/detekt/ktlint findings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
