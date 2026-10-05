#!/usr/bin/env bash
# Usage: connect.sh <device-ip>[:port]   — connects adb over the network (default port 5555).
source "$(dirname "$0")/common.sh"
target="${1:?usage: connect.sh <device-ip>[:port]}"
[[ "$target" == *:* ]] || target="$target:5555"
"$ADB" connect "$target"
"$ADB" devices -l
