#!/usr/bin/env python3
"""Offline regression tests for the Full-only APK release gate.

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
kind = 'full'
if sys.argv[2] == 'badging':
    pkg = os.getenv('TEST_PACKAGE', 'io.github.aimindseye.tenseven')
    code = os.getenv('TEST_FULL_CODE', '3')
    name = os.getenv('TEST_VERSION_NAME', '0.1.0')
    print(f"package: name='{pkg}' versionCode='{code}' versionName='{name}'")
    print("sdkVersion:'" + os.getenv("TEST_MIN_SDK", "34") + "'")
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
print(label + os.getenv('TEST_SIGNER_PUNCTUATION', ':') + ' certificate SHA-256 digest: ' + cert)
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
        self.full.write_bytes(b"fake full")

    def gate(self, expected_success=True, ci_unsigned=False, **overrides):
        env = dict(os.environ, ANDROID_HOME=str(self.root), **overrides)
        args = ["bash", str(SCRIPT)]
        if ci_unsigned:
            args.append("--ci-unsigned")
            self.full = self.root / "full-unsigned.apk"
            self.full.write_bytes(b"fake full unsigned")
        completed = subprocess.run(args + [str(self.full)],
                                   capture_output=True, text=True, env=env, timeout=20)
        self.assertEqual(completed.returncode == 0, expected_success,
                         completed.stdout + "\n" + completed.stderr)
        return completed.stdout

    def test_android_11_minimum_is_rejected_after_android_14_floor(self):
        self.gate(expected_success=False, TEST_MIN_SDK="30")

    def test_release_metadata_matches(self):
        self.gate(EXPECTED_VERSION_CODE="18", EXPECTED_VERSION_NAME="1.0.0",
                  TEST_FULL_CODE="18", TEST_VERSION_NAME="1.0.0")

    def test_wrong_release_code_is_rejected(self):
        self.gate(expected_success=False, EXPECTED_VERSION_CODE="18", TEST_FULL_CODE="17")

    def test_wrong_release_name_is_rejected(self):
        self.gate(expected_success=False, EXPECTED_VERSION_NAME="1.0.0")

    def test_v2_only(self):
        self.assertIn("verified v2 signature", self.gate())

    def test_v3_only(self):
        self.assertIn("verified v3 signature", self.gate(TEST_SCHEME="v3"))

    def test_signer_number_label(self):
        self.gate(TEST_SIGNER_LABEL="Signer #1")

    def test_signer_number_label_without_colon(self):
        # Exact Build Tools 36 signer format captured from signed Tenseven APK on macOS.
        self.assertIn(
            "verified v2 signature",
            self.gate(TEST_SIGNER_LABEL="Signer #1", TEST_SIGNER_PUNCTUATION=""),
        )

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

    def test_invalid_version(self):
        self.gate(False, TEST_FULL_CODE="invalid")

    def test_full_missing_service(self):
        self.gate(False, TEST_MISSING_ACCESS="1")

    def test_ci_unsigned(self):
        self.assertIn("mode=ci-unsigned", self.gate(ci_unsigned=True))

    def test_unsigned_file_rejected_in_signed_mode(self):
        self.full = self.root / "full-unsigned.apk"
        self.full.write_bytes(b"fake unsigned")
        self.gate(False)

if __name__ == "__main__":
    unittest.main()
