#!/usr/bin/env bash
# Runs the looper APK (looper/, built by the superproject's tests/harness/apk.py) in the headless
# build: it goes through the loop slot's paths and finishes, so ATL exits by itself. Exit 0 when
# every step logged and ATL exited 0. Usage: run-looper.sh <headless build dir> <looper.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
rc=0
env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" timeout 60 \
  ./android-translation-layer "$apk" -l dev/dine/looper/MainActivity -X -Xint >"$work/log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }
# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
for step in "worker idling" "worker woken" "worker timeout" "main woken" "worker quit" "done"; do
  grep -q "looper $step" "$work/log" || fail "no \"looper $step\" (exit status $rc)"
done
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
