# Shared settings for device scripts. Override with environment variables.
$ErrorActionPreference = 'Stop'
$Pkg = if ($env:LUMBINA_PKG) { $env:LUMBINA_PKG } else { 'dev.lumbina.tv' }
$A11yService = if ($env:LUMBINA_A11Y_SERVICE) { $env:LUMBINA_A11Y_SERVICE } else { "$Pkg/dev.lumbina.tv.platform.accessibility.LumbinaAccessibilityService" }
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# adb from PATH, else from the SDK in local.properties (sdk.dir=C\:/path, backslashes dropped).
function Find-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) { return 'adb' }
    $props = Join-Path $Root 'local.properties'
    if (Test-Path $props) {
        $line = Select-String -Path $props -Pattern '^sdk\.dir=(.*)$' | Select-Object -First 1
        if ($line) {
            $adb = Join-Path ($line.Matches[0].Groups[1].Value -replace '\\', '') 'platform-tools\adb.exe'
            if (Test-Path $adb) { return $adb }
        }
    }
    throw 'adb not found: put it on PATH or set ADB'
}
$Adb = if ($env:ADB) { $env:ADB } else { Find-Adb }
