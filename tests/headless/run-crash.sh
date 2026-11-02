#!/usr/bin/env bash
# Runs each activity of the crash APK (crash/, built by the superproject's tests/harness/apk.py) in
# the headless build. Each throws on Android's main thread (onCreate, onResume, a posted Runnable,
# the onCreate of an activity another one started) and must end ATL through the thread's uncaught
# handler: exit 10, no CheckJNI abort.
# Usage: run-crash.sh <headless build dir> <crash.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail() { echo "FAIL: $*"; echo "log: $work/$activity.log"; trap - EXIT; exit 1; }
cd "$build"
for activity in CreateActivity:onCreate ResumeActivity:onResume "PostActivity:a posted Runnable" StartActivity:onCreate; do
  where="${activity#*:}"
  activity="${activity%%:*}"
  mkdir "$work/$activity"
  rc=0
  env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/$activity" timeout 60 \
    ./android-translation-layer "$apk" -l "dev/dine/crash/$activity" -X -Xint >"$work/$activity.log" 2>&1 || rc=$?
  ! grep -q "JNI DETECTED ERROR" "$work/$activity.log" || fail "$activity: CheckJNI abort (exit status $rc)"
  grep -q "hacky_uncaught_exception_handler" "$work/$activity.log" || fail "$activity: no uncaught handler (exit status $rc)"
  grep -q "IllegalStateException: crash in $where" "$work/$activity.log" || fail "$activity: no trace of the crash"
  [[ $rc == 10 ]] || fail "$activity: exit status $rc, not 10"
done
echo "PASS"
