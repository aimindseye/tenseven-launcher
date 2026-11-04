#!/usr/bin/env bash
# Build and package the signed 1.0.0 APK with the existing registered release key.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ ! -f keystore.properties && -z "${TENSEVEN_KEYSTORE:-}" ]]; then
  echo "ERROR: Configure keystore.properties or the TENSEVEN signing environment variables." >&2
  exit 2
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: Commit tracked source changes before packaging a release." >&2
  exit 2
fi
./gradlew --no-daemon :app:verifyRegisteredReleaseKey :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
full="app/build/outputs/apk/release/Tenseven-Launcher-release.apk"
EXPECTED_VERSION_NAME=1.0.0 EXPECTED_VERSION_CODE=18 bash tools/verify-release-apks.sh "$full"
out="${TENSEVEN_RELEASE_DIR:-$HOME/Downloads/tenseven-release/1.0.0}"
mkdir -p "$out"
asset="TenSeven-Launcher-1.0.0.apk"
cp "$full" "$out/$asset"
cp docs/releases/1.0.0.md "$out/RELEASE_NOTES.md"
git rev-parse HEAD > "$out/SOURCE_COMMIT.txt"
(cd "$out" && shasum -a 256 "$asset" > "$asset.sha256")
printf 'Signed APK and release files: %s\nSource commit: %s\n' "$out" "$(git rev-parse HEAD)"
