#!/usr/bin/env bash
# Grants WRITE_SECURE_SETTINGS so Lumbina can re-enable its accessibility service after reboot.
# The grant survives app updates signed with the same key, but not an uninstall.
source "$(dirname "$0")/common.sh"
"$ADB" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
echo "granted WRITE_SECURE_SETTINGS to $PKG"
