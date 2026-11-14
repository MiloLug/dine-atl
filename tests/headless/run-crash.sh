#!/usr/bin/env bash
# Runs each activity of the crash APK (crash/, built by the superproject's tests/harness/apk.py) in
# the headless build. The first group throws on Android's main thread where an exception ends the
# app (onCreate, onResume, a posted Runnable, the onCreate of an activity another one started,
# onLayout, onPause and onDestroy inside a finish): ATL ends through the thread's uncaught handler,
# exit 10, no CheckJNI abort, with CheckJNI on. The second throws where ATL prints the exception and
# goes on (a view's onMeasure, its onDraw): the app logs a later message and ATL exits 0.
# Usage: run-crash.sh <headless build dir> <crash.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cd "$build"
# run <activity>: its output into $log, its exit status into rc
run() {
  mkdir "$work/$1"
  log="$work/$1.log"
  rc=0
  env "${headless_env[@]}" DINE_PREFIX="$work/$1" timeout 60 \
    ./android-translation-layer "$apk" -l "dev/dine/crash/$1" -X -Xint >"$log" 2>&1 || rc=$?
  ! grep -q "JNI DETECTED ERROR" "$log" || fail "$1: CheckJNI abort (exit status $rc)"
  grep -q ": crash checkjni true$" "$log" || fail "$1: CheckJNI isn't on (exit status $rc)"
}

for activity in CreateActivity:onCreate ResumeActivity:onResume "PostActivity:a posted Runnable" StartActivity:onCreate \
  LayoutActivity:onLayout PauseActivity:onPause DestroyActivity:onDestroy; do
  where="${activity#*:}"
  activity="${activity%%:*}"
  run "$activity"
  grep -q "hacky_uncaught_exception_handler" "$log" || fail "$activity: no uncaught handler (exit status $rc)"
  grep -q "IllegalStateException: crash in $where" "$log" || fail "$activity: no trace of the crash"
  [[ $rc == 10 ]] || fail "$activity: exit status $rc, not 10"
done
for activity in MeasureActivity:onMeasure DrawActivity:onDraw; do
  where="${activity#*:}"
  activity="${activity%%:*}"
  run "$activity"
  grep -q "IllegalStateException: crash in $where" "$log" || fail "$activity: no trace of the crash (exit status $rc)"
  grep -q "^atl: app exception cleared:$" "$log" || fail "$activity: no line for the cleared exception (exit status $rc)"
  grep -q ": crash survived $where true$" "$log" || fail "$activity: the app didn't go on (exit status $rc)"
  ! grep -q "hacky_uncaught_exception_handler" "$log" || fail "$activity: the uncaught handler ran"
  [[ $rc == 0 ]] || fail "$activity: exit status $rc, not 0"
done
echo "PASS"
