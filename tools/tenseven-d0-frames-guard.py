#!/usr/bin/env python3
"""Block D0 if any Active Frames implementation or associated integrations changed."""
import subprocess
import sys
from pathlib import Path

BASE = "50f8b1a6a5e22dfd9ac6189fc1301a421984808d"


def changed_paths():
    diff = subprocess.check_output(["git", "diff", "--name-only", BASE], text=True).splitlines()
    untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines()
    return sorted(set(diff + untracked))


def is_protected(path):
    p = Path(path)
    if not path.startswith("app/src/main/"):
        return False
    name = p.name.lower()
    # Protect presentation, settings, capture/task/media integration and all named Frame files.
    return "frame" in name or any(part in {"frames", "activeframes"} for part in map(str.lower, p.parts))


def main():
    paths = changed_paths()
    modified = [p for p in paths if is_protected(p)]
    if modified:
        print("FAIL: Protected Active Frames paths changed:")
        print("\n".join(modified))
        return 1
    print("PASS: No Active Frames implementation files changed relative to D0 baseline.")
    if not any("HubNotificationAudit.kt" in p for p in paths):
        print("FAIL: D0 audit source missing")
        return 1
    print("PASS: D0 audit source present.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
