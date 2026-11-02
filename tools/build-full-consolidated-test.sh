#!/usr/bin/env bash
# Single-APK user test package. Build/verify both variants internally but distribute Full only.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ ! -f keystore.properties && -z "${TENSEVEN_KEYSTORE:-}" ]]; then
  echo "ERROR: Configure protected keystore.properties or TENSEVEN_KEYSTORE environment." >&2
  exit 2
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: Commit or set aside tracked source modifications before testing exact revision." >&2
  exit 2
fi
./gradlew --no-daemon \
  :app:verifyRegisteredReleaseKey \
  :app:testDebugUnitTest \
  :app:assembleRelease \
  :app:assembleLite \
  :app:lintLite
python3 tests/test_release_verifier.py
full="app/build/outputs/apk/release/Tenseven-Launcher-release.apk"
lite="app/build/outputs/apk/lite/Tenseven-Launcher-lite.apk"
bash tools/verify-release-apks.sh "$full" "$lite"
sha="$(git rev-parse --short=12 HEAD)"
out="${HOME}/Downloads/tenseven-full-combined-test"
mkdir -p "$out"
target="$out/Tenseven-Full-Hub2-$sha.apk"
cp "$full" "$target"
(cd "$out" && shasum -a 256 "$(basename "$target")" > "$(basename "$target").sha256")
echo "Verified Full APK: $target"
echo "SHA256: $(shasum -a 256 "$target")"
echo "Only the Full APK is intended for phone installation; no keystore is copied."
