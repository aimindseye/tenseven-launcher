#!/usr/bin/env bash
# Static checks for this repository. No network, no Android SDK, no device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
fails=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; fails=$((fails + 1)); }

tracked() { git ls-files "$@" 2>/dev/null || find . -path ./.git -prune -o -type f -print; }

# 1. Shell syntax and shellcheck.
mapfile -t shells < <(tracked '*.sh')
for f in "${shells[@]}"; do
    bash -n "$f" || fail "bash -n $f"
done
if command -v shellcheck >/dev/null 2>&1; then
    if shellcheck -x "${shells[@]}"; then pass "shellcheck (${#shells[@]} files)"; else fail shellcheck; fi
else
    printf 'SKIP  shellcheck not installed\n'
fi

# 2. Every XML file (manifest, resources, generated icons) is well-formed.
mapfile -t xmls < <(tracked 'app/*.xml' 'design/*.xml' 'third_party/*.xml' ':!:third_party/minklauncher/**/*.kt')
if command -v xmllint >/dev/null 2>&1; then
    if xmllint --noout "${xmls[@]}"; then pass "XML well-formed (${#xmls[@]} files)"; else fail "XML well-formed"; fi
else
    if python3 -c 'import sys,xml.dom.minidom as m; [m.parse(f) for f in sys.argv[1:]]' "${xmls[@]}"; then
        pass "XML well-formed (${#xmls[@]} files, python)"
    else
        fail "XML well-formed"
    fi
fi

# 3. Python tools compile, and what they generate or catalogue is current.
if python3 -I -m py_compile tools/*.py; then pass "python tools compile"; else fail "python tools compile"; fi
if python3 -I tools/tabler_icons.py check; then :; else fail "tabler icons"; fi
if python3 -I tools/brand_assets.py check; then :; else fail "brand assets"; fi

# 4. The detekt config is the byte-exact SableOS copy it says it is.
# shellcheck source=config/DETEKT_PROVENANCE.env
source config/DETEKT_PROVENANCE.env
if [[ "$(git hash-object config/detekt.yml)" == "$DETEKT_CONFIG_GIT_BLOB" ]]; then
    pass "detekt.yml matches its provenance"
else
    fail "config/detekt.yml differs from DETEKT_CONFIG_GIT_BLOB (edit config/detekt-tenseven.yml, not detekt.yml)"
fi

# 5. Every third-party folder carries its licence and a provenance note.
before=$fails
for d in third_party/*/; do
    d="${d%/}"
    [[ -f "$d/LICENSE" ]] || fail "$d has no LICENSE"
    if [[ "$d" == third_party/titan-ai ]]; then continue; fi # MIT notice only; its provenance is THIRD_PARTY_NOTICES.md
    [[ -f "$d/PROVENANCE.md" ]] || fail "$d has no PROVENANCE.md"
done
if [[ "$fails" -eq "$before" ]]; then pass "third_party folders have LICENSE and PROVENANCE.md"; fi

# 6. The manifest asks only for permissions an ordinary install can hold. Signature permissions are
#    rejected by Android Lint and never granted; the platform-signed SableOS build adds its own.
signature='INTERACT_ACROSS_USERS|OBSERVE_GRANT_REVOKE_PERMISSIONS|MODIFY_QUIET_MODE|ACCESS_HIDDEN_PROFILES_FULL|REAL_GET_TASKS|REORDER_TASKS|REMOVE_TASKS|START_TASKS_FROM_RECENTS|READ_FRAME_BUFFER|org\.sableos\.permission|WRITE_SECURE_SETTINGS|MANAGE_ACTIVITY_TASKS'
if grep -nE "<uses-permission[^>]*($signature)" app/src/main/AndroidManifest.xml; then
    fail "manifest requests a signature permission"
else
    pass "manifest requests no signature permission"
fi

# 7. Nothing that must never be committed.
forbidden="$(tracked | grep -E '\.(img|zip|apk|aab|jks|keystore|pk8)$' || true)"
if [[ -z "$forbidden" ]]; then pass "no images, archives, APKs or keys tracked"; else fail "forbidden files tracked: $forbidden"; fi

# 8. The Gradle wrapper jar is the one this repository pinned.
expected_jar="7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172"
if [[ "$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)" == "$expected_jar" ]]; then
    pass "gradle-wrapper.jar checksum"
else
    fail "gradle-wrapper.jar changed (update expected_jar in tests/run.sh with the new Gradle release's sha256)"
fi

if [[ "$fails" -eq 0 ]]; then printf '\nAll static checks passed.\n'; else printf '\n%d check(s) failed.\n' "$fails"; exit 1; fi
