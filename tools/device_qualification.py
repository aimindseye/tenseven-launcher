#!/usr/bin/env python3
"""Print read-only ADB facts for a pending TenSeven physical qualification report."""
import argparse
import json
import subprocess

SCENARIOS = ("upgrade", "bb7_layout", "bb10_layout", "keyboard_ime", "widgets_bind_configure",
             "widgets_restore_resize", "profile_privacy", "permissions", "capture_consent")


def capture(serial):
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
            "scenarios": dict.fromkeys(SCENARIOS, "pending")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="Explicit adb device serial; never selects a device implicitly")
    args = parser.parse_args()
    # stdout only: no CLI-supplied file paths are read or written.
    print(json.dumps(capture(args.serial), indent=2))


if __name__ == "__main__":
    main()
