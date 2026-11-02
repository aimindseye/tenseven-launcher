"""Offline checks for read-only CI diagnostics and credential-safe errors."""
import importlib.util
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from contextlib import redirect_stderr
import urllib.error

SPEC = importlib.util.spec_from_file_location("sonar_gate_report", Path(__file__).parents[1] / "tools/sonar_gate_report.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class GateReportTest(unittest.TestCase):
    def test_pr_scope_and_pagination_are_preserved(self):
        calls = []

        def get(endpoint, params, token):
            calls.append((endpoint, params))
            self.assertEqual("dummy-token", token)
            self.assertEqual("108", params["pullRequest"])
            self.assertNotIn("branch", params)
            if endpoint == "qualitygates/project_status":
                return {"projectStatus": {"status": "ERROR"}}
            if endpoint == "issues/search":
                return {"issues": [{"key": str(params["p"])}], "paging": {"total": 2}}
            return {"components": []}

        with patch.object(MODULE, "fetch", side_effect=get):
            result = MODULE.report("dummy-token", "108", "feature")
        self.assertEqual(2, len(result["issues"]))
        self.assertEqual(4, len(calls))

    def test_http_error_does_not_log_url_or_credential(self):
        error = urllib.error.HTTPError("https://example.invalid/dummy-token", 403, "dummy-token", {}, None)
        output = io.StringIO()
        with patch.dict(os.environ, {"SONAR_TOKEN": "dummy-token"}), patch.object(MODULE, "report", side_effect=error), redirect_stderr(output):
            self.assertEqual(1, MODULE.main())
        self.assertNotIn("dummy-token", output.getvalue())
        self.assertIn("403", output.getvalue())


if __name__ == "__main__":
    unittest.main()
