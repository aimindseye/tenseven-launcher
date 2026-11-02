#!/usr/bin/env bash
# Verify compiled Full and Lite APK identities. No keystore, passwords or secrets are needed.
# Default: require signed APKs using the cert registered in Android Developer Console.
# --ci-unsigned: verify build outputs/manifests, explicitly without claiming release signatures.
set -euo pipefail

EXPECTED_PACKAGE=io.github.aimindseye.tenseven
EXPECTED_CERT=9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292
EXPECTED_MIN_SDK=30
BUILD_TOOLS_VERSION=36.0.0

mode=signed
if [[ "${1:-}" == "--ci-unsigned" ]]; then mode=ci-unsigned; shift; fi
if [[ $# -ne 2 || ! -f "$1" || ! -f "$2" ]]; then
  echo "Usage: $0 [--ci-unsigned] FULL_APK LITE_APK (both files must exist)" >&2
  exit 2
fi
full="$1"
lite="$2"

# apkanalyzer can be installed in a different SDK than its required aapt build-tools,
# producing 'Cannot locate latest build tools'. Use aapt2 from one explicit SDK instead.
build_tools=""
for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
  if [[ -n "$sdk" && -x "$sdk/build-tools/$BUILD_TOOLS_VERSION/aapt2" ]]; then
    build_tools="$sdk/build-tools/$BUILD_TOOLS_VERSION"
    break
  fi
done
if [[ -z "$build_tools" ]]; then
  echo "ERROR: Android SDK build-tools/$BUILD_TOOLS_VERSION/aapt2 not found." >&2
  echo "Install with: sdkmanager 'build-tools;$BUILD_TOOLS_VERSION' and set ANDROID_HOME to the SDK root." >&2
  exit 1
fi
AAPT2="$build_tools/aapt2"
if [[ "$mode" == "signed" ]]; then
  APKSIGNER="$build_tools/apksigner"
  [[ -x "$APKSIGNER" ]] || { echo "ERROR: missing $APKSIGNER" >&2; exit 1; }
fi

extract_badging_field() {
  local field="$1" badging="$2"
  case "$field" in
    package) sed -nE "s/^package: name='([^']+)'.*/\1/p" <<< "$badging" ;;
    versionCode) sed -nE "s/^package: .*versionCode='([0-9]+)'.*/\1/p" <<< "$badging" ;;
    minSdk) sed -nE "s/^(sdkVersion|minSdkVersion):'([0-9]+)'.*/\2/p" <<< "$badging" ;;
    *) echo "ERROR: unrecognized field" >&2; exit 1 ;;
  esac
}

check_apk() {
  local kind="$1" apk="$2" badging="$3" xml="$4" app min cert certs output code scheme
  app="$(extract_badging_field package "$badging")"
  min="$(extract_badging_field minSdk "$badging")"
  code="$(extract_badging_field versionCode "$badging")"
  [[ "$app" == "$EXPECTED_PACKAGE" ]] || { echo "ERROR: $kind wrong package: $app" >&2; exit 1; }
  [[ "$min" == "$EXPECTED_MIN_SDK" ]] || { echo "ERROR: $kind wrong minSdk: $min" >&2; exit 1; }
  [[ "$code" =~ ^[0-9]+$ ]] || { echo "ERROR: $kind missing/invalid versionCode: $code" >&2; exit 1; }

  if [[ "$kind" == "full" ]]; then
    grep -Fq LockScreenAccessibilityService <<< "$xml" || { echo "ERROR: Full missing Accessibility service" >&2; exit 1; }
    grep -Fq ConversationListenerService <<< "$xml" || { echo "ERROR: Full missing notification listener service" >&2; exit 1; }
  else
    if grep -Fq LockScreenAccessibilityService <<< "$xml" || grep -Fq ConversationListenerService <<< "$xml"; then
      echo "ERROR: Lite declares a sensitive service" >&2; exit 1
    fi
    if grep -Fq android.permission.SYSTEM_ALERT_WINDOW <<< "$xml"; then
      echo "ERROR: Lite declares lock-layer overlay permission" >&2; exit 1
    fi
  fi

  if [[ "$mode" == "signed" ]]; then
    [[ "$apk" != *-unsigned.apk ]] || { echo "ERROR: $kind unsigned artifact rejected in release mode" >&2; exit 1; }
    output="$("$APKSIGNER" verify --min-sdk-version "$EXPECTED_MIN_SDK" --verbose --print-certs "$apk")"
    grep -qx 'Verifies' <<< "$output" || { echo "ERROR: $kind signature not verified" >&2; exit 1; }
    if grep -Fxq 'Verified using v3 scheme (APK Signature Scheme v3): true' <<< "$output"; then
      scheme=v3
    elif grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$output"; then
      scheme=v2
    else
      echo "ERROR: $kind has no verified v2/v3 APK signature" >&2; exit 1
    fi
    grep -qx 'Number of signers: 1' <<< "$output" || { echo "ERROR: $kind has unexpected signer count" >&2; exit 1; }
    # Build Tools may print 'V2 Signer:', 'V3.0 Signer:' or 'Signer #1' (no colon).
    # Validate every reported signing-certificate SHA-256, never only the first.
    certs="$(sed -nE 's/^(V[0-9.]+ Signer|Signer #[0-9]+):? certificate SHA-256 digest: ([[:xdigit:]]{64})$/\2/p' <<< "$output" | tr 'A-F' 'a-f')"
    [[ -n "$certs" ]] || { echo "ERROR: $kind missing signing certificate SHA-256 in apksigner output" >&2; exit 1; }
    while IFS= read -r cert; do
      [[ "$cert" == "$EXPECTED_CERT" ]] || { echo "ERROR: $kind signing certificate does not match registered key ($cert)" >&2; exit 1; }
    done <<< "$certs"
    echo "PASS $kind: verified $scheme signature and registered signing certificate"
  else
    [[ "$apk" == *-unsigned.apk ]] || { echo "ERROR: $kind CI mode expects an unsigned build candidate" >&2; exit 1; }
  fi
  echo "PASS $kind: package, minSdk, versionCode, compiled manifest and mode=$mode"
}

full_badging="$("$AAPT2" dump badging "$full")"
lite_badging="$("$AAPT2" dump badging "$lite")"
full_xml="$("$AAPT2" dump xmltree "$full" --file AndroidManifest.xml)"
lite_xml="$("$AAPT2" dump xmltree "$lite" --file AndroidManifest.xml)"

check_apk full "$full" "$full_badging" "$full_xml"
check_apk lite "$lite" "$lite_badging" "$lite_xml"
full_code="$(extract_badging_field versionCode "$full_badging")"
lite_code="$(extract_badging_field versionCode "$lite_badging")"
[[ "$full_code" == "$lite_code" ]] || { echo "ERROR: Full/Lite versionCodes differ: $full_code vs $lite_code" >&2; exit 1; }
echo "PASS Full/Lite matching versionCode=$full_code"
if [[ "$mode" == "signed" ]]; then
  echo "PASS both APKs signed with registered SHA-256 certificate $EXPECTED_CERT"
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$full" "$lite"; else sha256sum "$full" "$lite"; fi
fi
