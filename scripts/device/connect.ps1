# Usage: connect.ps1 <device-ip>[:port]
param([Parameter(Mandatory)][string]$Target)
. "$PSScriptRoot\common.ps1"
if ($Target -notmatch ':') { $Target = "${Target}:5555" }
& $Adb connect $Target
& $Adb devices -l
