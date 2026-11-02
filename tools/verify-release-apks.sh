#!/usr/bin/env bash
# Enforce the registered Tenseven signing identity for final signed APK releases.
# In --ci-unsigned mode, validate real compiled APK manifests without claiming a signed release.
set -euo pipefail
EXPECTED_PACKAGE=io.github.aimindseye.tenseven
EXPECTED_CERT=9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292
EXPECTED_MIN_SDK=30
mode=signed
if [[ "${1:-}" == --ci-unsigned ]]; then mode=ci-unsigned; shift; fi
if [[ $# -ne 2 || ! -f "$1" || ! -f "$2" ]]; then
  echo "Usage: $0 [--ci-unsigned] FULL_APK LITE_APK (both files must exist)" >&2
  exit 2
fi
full="$1"; lite="$2"
sdk_tool() {
  local found
  if command -v "$1" >/dev/null 2>&1; then command -v "$1"; return; fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    found="$(find "$ANDROID_HOME" -type f -name "$1" -print -quit 2>/dev/null || true)"
    if [[ -n "$found" ]]; then echo "$found"; return; fi
  fi
  echo "Missing Android SDK tool: $1" >&2; exit 1
}
APKANALYZER="$(sdk_tool apkanalyzer)"
if [[ "$mode" == signed ]]; then APKSIGNER="$(sdk_tool apksigner)"; fi
check_apk() {
  local kind="$1" apk="$2" xml="$3" app min cert output
  app="$("$APKANALYZER" manifest application-id "$apk")"
  min="$("$APKANALYZER" manifest min-sdk "$apk")"
  [[ "$app" == "$EXPECTED_PACKAGE" ]] || { echo "$kind: wrong package $app" >&2; exit 1; }
  [[ "$min" == "$EXPECTED_MIN_SDK" ]] || { echo "$kind: wrong minSdk $min" >&2; exit 1; }
  if [[ "$kind" == full ]]; then
    grep -Fq LockScreenAccessibilityService <<< "$xml" || { echo "Full missing Accessibility service" >&2; exit 1; }
    grep -Fq ConversationListenerService <<< "$xml" || { echo "Full missing listener service" >&2; exit 1; }
  else
    if grep -Fq LockScreenAccessibilityService <<< "$xml" || grep -Fq ConversationListenerService <<< "$xml"; then
      echo "Lite declares a sensitive service" >&2; exit 1
    fi
    if grep -Fq android.permission.SYSTEM_ALERT_WINDOW <<< "$xml"; then
      echo "Lite declares lock-layer overlay permission" >&2; exit 1
    fi
  fi
  if [[ "$mode" == signed ]]; then
    [[ "$apk" != *-unsigned.apk ]] || { echo "$kind: refusing unsigned release" >&2; exit 1; }
    output="$("$APKSIGNER" verify --min-sdk-version "$EXPECTED_MIN_SDK" --verbose --print-certs "$apk")"
    grep -qx Verifies <<< "$output" || { echo "$kind: signature not verified" >&2; exit 1; }
    grep -qx 'Verified using v3 scheme (APK Signature Scheme v3): true' <<< "$output" || { echo "$kind: missing v3 signing" >&2; exit 1; }
    grep -qx 'Number of signers: 1' <<< "$output" || { echo "$kind: incorrect signer count" >&2; exit 1; }
    cert="$(sed -nE 's/^V3[.]0 Signer: certificate SHA-256 digest: ([0-9a-fA-F]+)$/\1/p' <<< "$output" | head -n 1 | tr A-F a-f)"
    [[ "$cert" == "$EXPECTED_CERT" ]] || { echo "$kind: certificate SHA-256 does not match registered key ($cert)" >&2; exit 1; }
  else
    [[ "$apk" == *-unsigned.apk ]] || { echo "$kind: CI mode requires -unsigned.apk" >&2; exit 1; }
  fi
  echo "PASS $kind package, minSdk, compiled manifest; mode=$mode"
}
full_xml="$("$APKANALYZER" manifest print "$full")"
lite_xml="$("$APKANALYZER" manifest print "$lite")"
check_apk full "$full" "$full_xml"
check_apk lite "$lite" "$lite_xml"
full_code="$("$APKANALYZER" manifest version-code "$full")"
lite_code="$("$APKANALYZER" manifest version-code "$lite")"
[[ "$full_code" == "$lite_code" ]] || { echo "Full/Lite versionCodes differ" >&2; exit 1; }
echo "PASS matching versionCode=$full_code"
if [[ "$mode" == signed ]]; then
  echo "PASS both signed with registered certificate $EXPECTED_CERT"
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$full" "$lite"; else sha256sum "$full" "$lite"; fi
fi
