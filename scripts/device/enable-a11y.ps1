# Adds Lumbina's accessibility service to the enabled list, keeping others and dropping duplicates.
. "$PSScriptRoot\common.ps1"
$current = (& $Adb shell settings get secure enabled_accessibility_services).Trim()
if ($current -eq 'null') { $current = '' }
$list = @($current -split ':' | Where-Object { $_ }) + $A11yService | Select-Object -Unique
& $Adb shell settings put secure enabled_accessibility_services ($list -join ':')
& $Adb shell settings put secure accessibility_enabled 1
"enabled: $A11yService"
