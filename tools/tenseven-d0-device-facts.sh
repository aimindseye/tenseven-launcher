#!/usr/bin/env bash
# Metadata-only qualification from the physical Titan 2. Does not dump notifications or message text.
set -euo pipefail
SERIAL="${1:-TITAN20000006073}"
ADB=(adb -s "$SERIAL")
"${ADB[@]}" get-state >/dev/null
printf 'DEVICE_MODEL='; "${ADB[@]}" shell getprop ro.product.model | tr -d '\r'
printf 'ANDROID_VERSION='; "${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r'
printf 'SDK='; "${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r'
printf 'TENSEVEN_PACKAGE='; "${ADB[@]}" shell pm path io.github.aimindseye.tenseven | sed 's@^package:.*@installed@' | head -1
"${ADB[@]}" shell dumpsys package io.github.aimindseye.tenseven | tr -d '\r' | grep -E -m 2 'versionCode=|versionName=' || true
# Only a boolean. Never print the entire enabled_notification_listeners setting (other app identities).
listeners="$("${ADB[@]}" shell settings get secure enabled_notification_listeners | tr -d '\r' || true)"
case "$listeners" in
  *io.github.aimindseye.tenseven*ConversationListenerService*) echo 'TENSEVEN_LISTENER_ACCESS=listed' ;;
  *) echo 'TENSEVEN_LISTENER_ACCESS=not_listed' ;;
esac
echo 'NOTE: For per-app counts, use Hub Settings > Copy D0 notification audit.'
echo 'NOTE: No notification payload, contacts or device identifiers were exported.'
