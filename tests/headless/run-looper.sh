#!/usr/bin/env bash
# Runs the looper APK (looper/, built by the superproject's tests/harness/apk.py) in the headless
# build: it goes through the loop slot's paths and finishes, so ATL exits by itself. Exit 0 when
# every step logged, the worker's timed wait and main's untimed one lasted their 50 ms on next to no
# CPU time, and ATL exited 0. Usage: run-looper.sh <headless build dir> <looper.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"

cd "$build"
rc=0
env "${headless_env[@]}" DINE_PREFIX="$work/data" timeout 60 \
  ./android-translation-layer "$apk" -l dev/dine/looper/MainActivity -X -Xint >"$log" 2>&1 || rc=$?

# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
for step in "worker idling" "worker woken" "worker timeout" "main woken" "worker quit" "done"; do
  grep -q "looper $step" "$log" || fail "no \"looper $step\" (exit status $rc)"
done
for wait in timed untimed; do
  [[ $(grep -o "looper $wait wait .*" "$log") =~ ^looper\ $wait\ wait\ ([0-9]+)\ ms\ cpu\ ([0-9]+)\ ms$ ]] \
    || fail "no \"looper $wait wait\" line (exit status $rc)"
  # a wait that returns at once spins through its 50 ms
  ((BASH_REMATCH[1] >= 50 && BASH_REMATCH[2] < 10)) || fail "the wait spun or was cut short: ${BASH_REMATCH[0]}"
done
faults "$log"
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
