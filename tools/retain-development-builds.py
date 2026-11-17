#!/usr/bin/env python3
"""Optionally retain existing debug outputs; never builds, signs or installs."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
PACKAGES = {
    "app": ("Launcher", "io.github.aimindseye.tenseven.debug.l0"),
    "phone": ("Phone", "io.github.aimindseye.tenseven.phone.debug"),
    "calendar": ("Calendar", "io.github.aimindseye.tenseven.calendar.debug"),
}
PRODUCTION_CERT = "9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*arguments):
    return subprocess.check_output(["git", "-C", str(ROOT), *arguments])


def source_identity():
    names = sorted(set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")) - {""})
    digest = hashlib.sha256()
    for name in names:
        path = ROOT / name
        raw = path.read_bytes() if path.is_file() else b"<deleted>"
        digest.update(name.encode() + b"\0" + raw)
    return {
        "commit": git("rev-parse", "HEAD").decode().strip(),
        "commit_tree": git("rev-parse", "HEAD^{tree}").decode().strip(),
        "worktree_sha256": digest.hexdigest(),
        "status": git("status", "--porcelain=v1", "--untracked-files=all").decode(),
    }


def inspect(module, sdk):
    directory = ROOT / module / "build/outputs/apk/debug"
    metadata = json.loads((directory / "output-metadata.json").read_text())
    element, = metadata["elements"]
    filename = element["outputFile"]
    if Path(filename).name != filename:
        raise ValueError("Output metadata must name a direct APK child")
    apk = directory / filename
    if apk.is_symlink() or not apk.is_file():
        raise ValueError("Expected an ordinary existing APK")
    badging = subprocess.check_output([str(sdk / "aapt2"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package[1] != PACKAGES[module][1] or "application-debuggable" not in badging:
        raise ValueError("Only isolated debuggable L0 packages may be retained here")
    signature = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    certificates = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", signature)
    if len(certificates) != 1 or certificates[0] == PRODUCTION_CERT:
        raise ValueError("Expected one development certificate, never the production signer")
    return apk, {
        "module": module, "application_id": package[1], "version_code": int(package[2]),
        "version_name": package[3], "variant": metadata["variantName"],
        "signing_certificate_sha256": certificates[0], "apk_sha256": sha(apk),
        "bytes": apk.stat().st_size,
        "output_modified_utc": datetime.datetime.fromtimestamp(apk.stat().st_mtime, datetime.timezone.utc).isoformat(),
        "gradle_output": str(apk.relative_to(ROOT)), "badging": badging, "signature_verification": signature,
    }


def main():
    before = source_identity()
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root:
        raise ValueError("Set ANDROID_HOME; this command does not install SDK tools")
    sdk = Path(sdk_root) / "build-tools/36.0.0"
    outputs = [inspect(module, sdk) for module in PACKAGES]
    parent = ROOT / "builds/development" / before["commit"]
    parent.mkdir(parents=True, exist_ok=True)
    # A new exclusive directory preserves previous retained builds, including dirty-source builds.
    stage = Path(tempfile.mkdtemp(prefix=".staging-", dir=parent))
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    final = parent / (timestamp + "-" + before["worktree_sha256"][:12])
    manifest = {
        "source": before, "retained_at_utc": timestamp,
        "qualification": "UNQUALIFIED_DEVELOPMENT_COPY; consult validation receipts",
        "build_command": "existing Gradle :app:assembleDebug :phone:assembleDebug :calendar:assembleDebug",
        "production_release": False, "artifacts": [],
    }
    for apk, record in outputs:
        filename = "TenSeven-" + PACKAGES[record["module"]][0] + "-L0-debug.apk"
        destination = stage / filename
        shutil.copyfile(apk, destination)
        if sha(destination) != record["apk_sha256"] or sha(apk) != record["apk_sha256"]:
            raise ValueError("APK changed during retention")
        record["retained_filename"] = filename
        manifest["artifacts"].append(record)
    if source_identity() != before:
        raise ValueError("Source changed during retention")
    (stage / "BUILD_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    sums = [sha(path) + "  " + path.name for path in sorted(stage.iterdir())]
    (stage / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n")
    if final.exists():
        raise FileExistsError(final)
    stage.rename(final)
    print(final)


if __name__ == "__main__":
    main()
