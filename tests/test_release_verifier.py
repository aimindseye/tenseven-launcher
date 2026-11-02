#!/usr/bin/env python3
"""Offline regression tests for the Full/Lite APK release gate.

Uses simulated Android SDK CLI output. These checks do not replace verification
of final signed APKs or physical-device Play Protect testing.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "verify-release-apks.sh"
CERT = "9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292"

AAPT2 = r'''#!/usr/bin/env python3
import os, sys
kind = 'lite' if 'lite' in sys.argv[3] else 'full'
if sys.argv[2] == 'badging':
    pkg = os.getenv('TEST_PACKAGE', 'io.github.aimindseye.tenseven')
    code = os.getenv('TEST_LITE_CODE' if kind == 'lite' else 'TEST_FULL_CODE', '1')
    print(f"package: name='{pkg}' versionCode='{code}' versionName='0.1.0'")
    print("sdkVersion:'30'")
elif kind == 'full':
    print('ConversationListenerService')
    if not os.getenv('TEST_MISSING_ACCESS'):
        print('LockScreenAccessibilityService')
else:
    print('LockScreenAccessibilityService' if os.getenv('TEST_LITE_LEAK') else 'NoSensitiveService')
'''

APKSIGNER = r'''#!/usr/bin/env python3
import os, sys
scheme = os.getenv('TEST_SCHEME', 'v2')
if os.getenv('TEST_CORRUPT'):
    print('DOES NOT VERIFY', file=sys.stderr)
    sys.exit(1)
print('Verifies')
print('Verified using v2 scheme (APK Signature Scheme v2): ' + str(scheme == 'v2').lower())
print('Verified using v3 scheme (APK Signature Scheme v3): ' + str(scheme == 'v3').lower())
print('Number of signers: ' + os.getenv('TEST_SIGNERS', '1'))
label = os.getenv('TEST_SIGNER_LABEL') or ('V3.0 Signer' if scheme == 'v3' else 'V2 Signer')
cert = os.getenv('TEST_CERT', '9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292')
print(label + ': certificate SHA-256 digest: ' + cert)
'''

class VerifyApkReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        tools = self.root / "build-tools" / "36.0.0"
        tools.mkdir(parents=True)
        for name, source in [("aapt2", AAPT2), ("apksigner", APKSIGNER)]:
            executable = tools / name
            executable.write_text(source)
            executable.chmod(0o755)
        self.full = self.root / "full.apk"
        self.lite = self.root / "lite.apk"
        self.full.write_bytes(b"fake full")
        self.lite.write_bytes(b"fake lite")

    def gate(self, expected_success=True, ci_unsigned=False, **overrides):
        env = dict(os.environ, ANDROID_HOME=str(self.root), **overrides)
        args = ["bash", str(SCRIPT)]
        if ci_unsigned:
            args.append("--ci-unsigned")
            self.full = self.root / "full-unsigned.apk"
            self.lite = self.root / "lite-unsigned.apk"
            self.full.write_bytes(b"fake full unsigned")
            self.lite.write_bytes(b"fake lite unsigned")
        completed = subprocess.run(args + [str(self.full), str(self.lite)],
                                   capture_output=True, text=True, env=env, timeout=20)
        self.assertEqual(completed.returncode == 0, expected_success,
                         completed.stdout + "\n" + completed.stderr)
        return completed.stdout

    def test_v2_only(self):
        self.assertIn("verified v2 signature", self.gate())

    def test_v3_only(self):
        self.assertIn("verified v3 signature", self.gate(TEST_SCHEME="v3"))

    def test_signer_number_label(self):
        self.gate(TEST_SIGNER_LABEL="Signer #1")

    def test_wrong_certificate(self):
        self.gate(False, TEST_CERT="a" * 64)

    def test_v1_only_rejected(self):
        self.gate(False, TEST_SCHEME="v1")

    def test_missing_certificate(self):
        self.gate(False, TEST_CERT="not-a-fingerprint")

    def test_multiple_signers(self):
        self.gate(False, TEST_SIGNERS="2")

    def test_tampered_apk(self):
        self.gate(False, TEST_CORRUPT="1")

    def test_package_mismatch(self):
        self.gate(False, TEST_PACKAGE="org.invalid")

    def test_version_mismatch(self):
        self.gate(False, TEST_LITE_CODE="2")

    def test_full_missing_service(self):
        self.gate(False, TEST_MISSING_ACCESS="1")

    def test_lite_service_leak(self):
        self.gate(False, TEST_LITE_LEAK="1")

    def test_ci_unsigned(self):
        self.assertIn("mode=ci-unsigned", self.gate(ci_unsigned=True))

    def test_unsigned_file_rejected_in_signed_mode(self):
        self.full = self.root / "full-unsigned.apk"
        self.full.write_bytes(b"fake unsigned")
        self.gate(False)

if __name__ == "__main__":
    unittest.main()
