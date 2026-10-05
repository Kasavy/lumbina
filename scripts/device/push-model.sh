#!/usr/bin/env bash
# Usage: push-model.sh <lang> <unpacked-vosk-model-dir>     (debug builds only)
#   e.g. push-model.sh ru ~/Downloads/vosk-model-small-ru-0.22
# Installs a Vosk model into the app's internal files/models/<lang>/: the model goes through
# /data/local/tmp and is copied by the app itself (run-as, which needs a debuggable build).
# Lumbina picks it up the next time its accessibility service starts (re-run enable-a11y.sh or
# reinstall). Normally models are downloaded in the app; this is for offline setups.
source "$(dirname "$0")/common.sh"
lang="${1:?usage: push-model.sh <lang> <model-dir>}"
dir="${2:?usage: push-model.sh <lang> <model-dir>}"
[ -f "$dir/conf/model.conf" ] || { echo "$dir doesn't look like a Vosk model (no conf/model.conf)"; exit 1; }
staging="/data/local/tmp/lumbina-model-$lang"
target="files/models/$lang"  # relative to the app's data dir under run-as
"$ADB" shell rm -rf "$staging"
"$ADB" push "$(host_path "$dir")" "$staging" >/dev/null
"$ADB" shell chmod -R a+rX "$staging"
"$ADB" shell run-as "$PKG" sh -c "'rm -rf $target && mkdir -p $target && cp -r $staging/. $target/'"
"$ADB" shell rm -rf "$staging"
"$ADB" shell run-as "$PKG" ls "$target/conf/model.conf" >/dev/null && echo "installed $lang model"
