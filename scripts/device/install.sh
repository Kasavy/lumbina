#!/usr/bin/env bash
# Usage: install.sh [debug|release]   — builds and installs the FOSS APK, then starts Lumbina.
# A release is also compiled by its baseline profile, which stops and starts Lumbina once more.
source "$(dirname "$0")/common.sh"
type="${1:-debug}"
Type="$(tr '[:lower:]' '[:upper:]' <<< "${type:0:1}")${type:1}"
(cd "$ROOT" && ./gradlew ":app:assembleFoss$Type")
apks=("$ROOT"/app/build/outputs/apk/foss/"$type"/*.apk)
[ -f "${apks[0]}" ] || { echo "no APK in app/build/outputs/apk/foss/$type" >&2; exit 1; }
# A build for Hisense needs the process name prefix ("Build" in the README).
if "$ADB" shell pm list packages com.jamdeo.tv.vod | grep -q jamdeo &&
  ! grep -qs '^lumbina.processPrefix=' "$ROOT/local.properties" && [ -z "${LUMBINA_PROCESS_PREFIX:-}" ]; then
  echo "warning: this build has no process name prefix, see 'Build' in the README" >&2
fi
"$ADB" install -r "$(host_path "${apks[0]}")"
"$ADB" shell am start -n "$PKG/.MainActivity"
[ "$type" = release ] || exit 0
# As after a store install: compiled by the baseline profile. Sideloaded, the code is interpreted
# until the device compiles it some idle night. The profile is written a few seconds after the
# first frame; the compiled code is used from the processes' next start, and stopping them takes
# the accessibility service off, so it is put back if it was on.
service_was_on="$("$ADB" shell settings get secure enabled_accessibility_services | grep -c "$PKG/" || true)"
sleep 10
"$ADB" shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
  "$PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null
"$ADB" shell cmd package compile -m speed-profile -f "$PKG"
# "Success" is printed with no profile too: then nothing was compiled, and the status says so.
"$ADB" shell dumpsys package "$PKG" | grep -q 'status=speed-profile' ||
  echo "warning: no baseline profile was applied; Lumbina will run uncompiled" >&2
"$ADB" shell am force-stop "$PKG"
[ "$service_was_on" = 0 ] || bash "$(dirname "$0")/enable-a11y.sh"
"$ADB" shell am start -n "$PKG/.MainActivity"
