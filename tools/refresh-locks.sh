#!/usr/bin/env bash
# Writes the dependency lock files (gradle.lockfile per module) and the checksum-verification metadata.
# Run this after changing gradle/libs.versions.toml or a module's dependencies, on a machine with internet
# access and the Android SDK, and commit the result. build.gradle.kts switches locking on for every module
# that has a gradle.lockfile.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

./gradlew --no-daemon --console=plain :design:dependencies :app:dependencies --write-locks
./gradlew --no-daemon --console=plain --write-verification-metadata sha256 \
    :design:assembleDebug :app:assembleDebug :design:testDebugUnitTest :app:testDebugUnitTest

echo "Wrote gradle.lockfile files and gradle/verification-metadata.xml. Review the diff and commit it."
