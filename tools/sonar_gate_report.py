#!/usr/bin/env python3
"""Read-only SonarCloud diagnostics; never changes a gate or an issue's status."""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://sonarcloud.io/api/"
PROJECT = "aimindseye_tenseven-launcher"


def fetch(endpoint, parameters, token):
    request = urllib.request.Request(
        BASE + endpoint + "?" + urllib.parse.urlencode(parameters),
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def report(token, pull_request=None, branch=None):
    scope = {"pullRequest": pull_request} if pull_request else {"branch": branch} if branch else {}
    gate = fetch("qualitygates/project_status", {"projectKey": PROJECT, **scope}, token)
    issues = []
    page = 1
    while True:
        result = fetch("issues/search", {
            "componentKeys": PROJECT, **scope, "resolved": "false", "ps": 100, "p": page,
        }, token)
        issues.extend(result.get("issues", []))
        total = result.get("paging", {}).get("total", result.get("total", len(issues)))
        if len(issues) >= total or not result.get("issues"):
            break
        if page >= 50:
            raise RuntimeError("Issue result exceeds the bounded diagnostic limit")
        page += 1
    notes = []
    try:
        measures = fetch("measures/component_tree", {
            "component": PROJECT, **scope, "qualifiers": "FIL", "ps": 500,
            "metricKeys": "new_coverage,new_lines_to_cover,new_uncovered_lines,new_duplicated_lines_density,new_duplicated_lines",
        }, token)
    except urllib.error.HTTPError as error:
        measures = {}
        notes.append("Per-file measures unavailable: HTTP " + str(error.code))
    return {"projectStatus": gate.get("projectStatus", {}), "issues": issues,
            "components": measures.get("components", []), "notes": notes}


def render(data):
    status = data["projectStatus"]
    lines = ["Sonar quality gate: " + status.get("status", "UNKNOWN")]
    for condition in status.get("conditions", []):
        lines.append("GATE " + json.dumps(condition, sort_keys=True))
    for item in data["issues"]:
        # Metadata only: no source files, environment variables or response headers.
        fields = {key: item.get(key) for key in
                  ("key", "rule", "type", "severity", "component", "line", "message")}
        lines.append("ISSUE " + json.dumps(fields, sort_keys=True))
    for component in data["components"]:
        lines.append("MEASURE " + json.dumps({"path": component.get("path"),
                                              "measures": component.get("measures", [])}, sort_keys=True))
    return "\n".join(lines) + "\n"


def main():
    token = os.environ.get("SONAR_TOKEN")
    if not token:
        print("Sonar diagnostic unavailable: SONAR_TOKEN is missing", file=sys.stderr)
        return 1
    try:
        data = report(token, os.environ.get("SONAR_PULL_REQUEST"), os.environ.get("SONAR_BRANCH"))
        output = render(data)
        os.makedirs("build/reports/sonar-gate", exist_ok=True)
        with open("build/reports/sonar-gate/report.json", "w", encoding="utf-8") as stream:
            json.dump(data, stream, indent=2)
        with open("build/reports/sonar-gate/report.txt", "w", encoding="utf-8") as stream:
            stream.write(output)
        print(output, end="")
        return 0  # Diagnostic result only; the preceding scanner step owns gate failure.
    except urllib.error.HTTPError as error:
        print("Sonar diagnostic HTTP status: " + str(error.code), file=sys.stderr)
    except (OSError, ValueError, RuntimeError):
        # Do not print exceptions that could include HTTP request/authentication details.
        print("Sonar diagnostic request or report failed; no credentials were logged", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
