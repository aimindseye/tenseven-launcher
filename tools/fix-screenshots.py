#!/usr/bin/env python3
"""Repair screenshots that have adb warning text in front of the PNG data.

Usage: tools/fix-screenshots.py <folder> [<folder> ...]
Files are fixed in place. Files that are already valid, or have no PNG data, are left alone.
Symbolic links are skipped, and only regular .png files that really sit inside the given folder
are rewritten.
"""
import pathlib
import sys

SIG = b"\x89PNG\r\n\x1a\n"


def inside(root: pathlib.Path, path: pathlib.Path) -> bool:
    """True when path is a regular, non-link file whose real location is under root."""
    if path.is_symlink() or not path.is_file():
        return False
    return path.resolve().is_relative_to(root)


def repair(root: pathlib.Path) -> None:
    for path in sorted(root.rglob("*.png")):
        if not inside(root, path):
            print(f"skipped (not a plain file inside the folder): {path}")
            continue
        data = path.read_bytes()
        at = data.find(SIG)
        if at == 0:
            continue
        if at < 0:
            print(f"cannot repair (no PNG data): {path}")
            continue
        path.write_bytes(data[at:])
        print(f"repaired: {path}")


def main(folders: list[str]) -> int:
    if not folders:
        print(__doc__)
        return 2
    status = 0
    for folder in folders:
        root = pathlib.Path(folder).resolve()
        if not root.is_dir():
            print(f"not a folder: {folder}")
            status = 1
            continue
        repair(root)
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
