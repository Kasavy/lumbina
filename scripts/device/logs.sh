#!/usr/bin/env bash
# Prints Lumbina's own log file (logcat is disabled on some firmware, e.g. Hisense VIDAA).
source "$(dirname "$0")/common.sh"
"$ADB" shell cat "/sdcard/Android/data/$PKG/files/logs/lumbina.log" 2>/dev/null \
  || echo "no log yet (the file is created by the accessibility service)"
