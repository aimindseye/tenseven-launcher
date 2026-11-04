#!/usr/bin/env bash
# Backward-compatible entry point; distribution naming now follows the release version.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec bash "$ROOT/tools/build-release.sh" "$@"
