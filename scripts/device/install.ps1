# Usage: install.ps1 [debug|release]   — builds and installs the FOSS APK, then starts Lumbina.
# A release is also compiled by its baseline profile, which stops and starts Lumbina once more.
param([ValidateSet('debug', 'release')][string]$Type = 'debug')
. "$PSScriptRoot\common.ps1"
$task = ':app:assembleFoss' + $Type.Substring(0, 1).ToUpper() + $Type.Substring(1)
Push-Location $Root
try { & .\gradlew.bat $task; if ($LASTEXITCODE) { throw "build failed" } } finally { Pop-Location }
$apk = Get-ChildItem "$Root\app\build\outputs\apk\foss\$Type\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) { throw "no APK in app\build\outputs\apk\foss\$Type" }
# Release APKs have a process name prefix ("Build" in the README); a build without it is not one.
$prefixed = $env:LUMBINA_PROCESS_PREFIX -or
    (Select-String -Path "$Root\local.properties" -Pattern '^lumbina.processPrefix=' -Quiet -ErrorAction SilentlyContinue)
if (-not $prefixed) {
    Write-Warning 'this build has no process name prefix, see "Build" in the README'
}
& $Adb install -r $apk.FullName
# Nothing below may run after a failed install: it would stop the build already there.
if ($LASTEXITCODE) { throw "adb install failed" }
& $Adb shell am start -n "$Pkg/.MainActivity"
if ($Type -ne 'release') { return }
# As after a store install: compiled by the baseline profile. Sideloaded, the code is interpreted
# until the device compiles it some idle night. The profile is written a few seconds after the
# first frame; the compiled code is used from the processes' next start, and stopping them takes
# the accessibility service off, so it is put back if it was on.
$serviceWasOn = (& $Adb shell settings get secure enabled_accessibility_services) -match [regex]::Escape("$Pkg/")
Start-Sleep -Seconds 10
& $Adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$Pkg/androidx.profileinstaller.ProfileInstallReceiver" | Out-Null
& $Adb shell cmd package compile -m speed-profile -f $Pkg
# "Success" is printed with no profile too: then nothing was compiled, and the status says so.
if (-not ((& $Adb shell dumpsys package $Pkg) -match 'status=speed-profile')) {
    Write-Warning 'no baseline profile was applied; Lumbina will run uncompiled'
}
& $Adb shell am force-stop $Pkg
if ($serviceWasOn) { & "$PSScriptRoot\enable-a11y.ps1" }
& $Adb shell am start -n "$Pkg/.MainActivity"
