#!/usr/bin/env bash
# Writes the dependency lock files (gradle.lockfile per module).
# Run this after changing gradle/libs.versions.toml or a module's dependencies, on a machine with internet
# access and the Android SDK, and commit the result. build.gradle.kts switches locking on for every module
# that has a gradle.lockfile.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

./gradlew --no-daemon --console=plain :design:dependencies :app:dependencies --write-locks
echo "Wrote gradle.lockfile files. Review the diff and commit them."
echo "Checksum metadata (verification-metadata.xml) is not written here: it must cover every OS variant of the Android build tools (aapt2, etc.), so generate it on Linux CI or with all platforms, never from one Mac."
