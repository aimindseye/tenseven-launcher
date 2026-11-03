#!/usr/bin/env python3
"""Capture read-only ADB facts for a pending TenSeven physical qualification report."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

SCENARIOS = ("upgrade", "bb7_layout", "bb10_layout", "keyboard_ime", "widgets_bind_configure",
             "widgets_restore_resize", "profile_privacy", "permissions", "capture_consent")


def capture(serial, apk=None):
    def adb(*arguments):
        return subprocess.check_output(["adb", "-s", serial, "shell", *arguments], text=True, timeout=15).strip()
    properties = {name: adb("getprop", key) for name, key in {
        "manufacturer": "ro.product.manufacturer", "model": "ro.product.model",
        "android": "ro.build.version.release", "sdk": "ro.build.version.sdk",
        "security_patch": "ro.build.version.security_patch",
    }.items()}
    sdk = int(properties["sdk"])
    return {"schema": 1, "qualification": "pending", "install_floor": "eligible" if sdk >= 34 else "blocked",
            "device": properties, "window": adb("wm", "size"), "density": adb("wm", "density"),
            "font_scale": adb("settings", "get", "system", "font_scale"),
            "apk_sha256": hashlib.sha256(Path(apk).read_bytes()).hexdigest() if apk else None,
            "scenarios": {scenario: "pending" for scenario in SCENARIOS}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="Explicit adb device serial; never selects a device implicitly")
    parser.add_argument("output", type=Path)
    parser.add_argument("--apk", type=Path, help="Optional local APK to hash; never installed by this tool")
    args = parser.parse_args()
    result = capture(args.serial, args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Saved pending qualification: {args.output}")


if __name__ == "__main__":
    main()
