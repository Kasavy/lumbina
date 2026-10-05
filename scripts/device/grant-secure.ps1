# Grants WRITE_SECURE_SETTINGS so Lumbina can re-enable its accessibility service after reboot.
. "$PSScriptRoot\common.ps1"
& $Adb shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS
"granted WRITE_SECURE_SETTINGS to $Pkg"
