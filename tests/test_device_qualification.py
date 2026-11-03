import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("qualification", Path(__file__).parents[1] / "tools/device_qualification.py")
qualification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualification)


class DeviceQualificationTest(unittest.TestCase):
    def test_read_only_capture_never_claims_qualification(self):
        def fake(command, **kwargs):
            self.assertEqual(["adb", "-s", "fixture", "shell"], command[:4])
            self.assertNotIn("install", command)
            return "34" if command[-1] == "ro.build.version.sdk" else "fixture"
        with patch.object(qualification.subprocess, "check_output", side_effect=fake):
            result = qualification.capture("fixture")
        self.assertEqual("eligible", result["install_floor"])
        self.assertEqual("pending", result["qualification"])
        self.assertEqual({"pending"}, set(result["scenarios"].values()))

    def test_android_below_floor_is_blocked_not_qualified(self):
        with patch.object(qualification.subprocess, "check_output", return_value="33"):
            result = qualification.capture("fixture")
        self.assertEqual("blocked", result["install_floor"])
        self.assertEqual("pending", result["qualification"])
