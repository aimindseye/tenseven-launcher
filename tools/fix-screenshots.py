#!/usr/bin/env python3
"""Repair screenshots that have adb warning text in front of the PNG data.

Usage: tools/fix-screenshots.py <folder> [<folder> ...]
Files are fixed in place. Files that are already valid, or have no PNG data, are left alone.
"""
import pathlib
import sys

SIG = b"\x89PNG\r\n\x1a\n"

for folder in sys.argv[1:]:
    for path in sorted(pathlib.Path(folder).rglob("*.png")):
        data = path.read_bytes()
        at = data.find(SIG)
        if at == 0:
            continue
        if at < 0:
            print(f"cannot repair (no PNG data): {path}")
            continue
        path.write_bytes(data[at:])
        print(f"repaired: {path}")
