#!/usr/bin/env bash
# Shared settings for device scripts. Override with environment variables.
set -euo pipefail
PKG="${LUMBINA_PKG:-dev.lumbina.tv}"
A11Y_SERVICE="${LUMBINA_A11Y_SERVICE:-$PKG/dev.lumbina.tv.platform.accessibility.LumbinaAccessibilityService}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# adb from PATH, else from the SDK in local.properties (sdk.dir=C\:/path, backslashes dropped).
find_adb() {
  if command -v adb >/dev/null 2>&1; then echo adb; return; fi
  local sdk
  sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null | tr -d '\\\r')"
  for candidate in "$sdk/platform-tools/adb" "$sdk/platform-tools/adb.exe"; do
    [ -x "$candidate" ] && { echo "$candidate"; return; }
  done
  echo "adb not found: put it on PATH or set ADB" >&2
  exit 1
}
ADB="${ADB:-$(find_adb)}"
# A file on this computer as adb sees it: on Windows adb.exe can't open Git Bash's /c/... paths,
# and MSYS_NO_PATHCONV (needed for device paths) stops Git Bash from converting them.
host_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
