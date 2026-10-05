# Usage: push-model.ps1 <lang> <unpacked-vosk-model-dir>   (debug builds only; see push-model.sh)
param([Parameter(Mandatory)][string]$Lang, [Parameter(Mandatory)][string]$Dir)
. "$PSScriptRoot\common.ps1"
if (-not (Test-Path (Join-Path $Dir 'conf\model.conf'))) { throw "$Dir doesn't look like a Vosk model (no conf\model.conf)" }
function Invoke-Adb {
    & $Adb @args
    if ($LASTEXITCODE) { throw "adb $($args -join ' ') failed ($LASTEXITCODE)" }
}
$staging = "/data/local/tmp/lumbina-model-$Lang"
$target = "files/models/$Lang"  # relative to the app's data dir under run-as
Invoke-Adb shell rm -rf $staging
Invoke-Adb push $Dir $staging | Out-Null
Invoke-Adb shell chmod -R a+rX $staging
Invoke-Adb shell run-as $Pkg sh -c "'rm -rf $target && mkdir -p $target && cp -r $staging/. $target/'"
Invoke-Adb shell rm -rf $staging
Invoke-Adb shell run-as $Pkg ls "$target/conf/model.conf" | Out-Null
"installed $Lang model"
