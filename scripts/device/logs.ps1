# Prints Lumbina's own log file (logcat is disabled on some firmware, e.g. Hisense VIDAA).
. "$PSScriptRoot\common.ps1"
$log = "/sdcard/Android/data/$Pkg/files/logs/lumbina.log"
& $Adb shell "cat $log 2>/dev/null || echo 'no log yet (the file is created by the accessibility service)'"
