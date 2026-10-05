#!/usr/bin/env bash
# Adds Lumbina's accessibility service to the enabled list (TV firmware often hides this setting).
# Keeps other services, drops duplicates.
source "$(dirname "$0")/common.sh"
current="$("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
[[ "$current" == "null" ]] && current=""
list="$(printf '%s\n' "${current//:/$'\n'}" "$A11Y_SERVICE" | awk 'NF && !seen[$0]++' | paste -sd: -)"
"$ADB" shell settings put secure enabled_accessibility_services "$list"
"$ADB" shell settings put secure accessibility_enabled 1
echo "enabled: $A11Y_SERVICE"
