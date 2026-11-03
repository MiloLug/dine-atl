#!/usr/bin/env bash
# Runs the widgets APK's ListsActivity (widgets/, built by the superproject's tests/harness/apk.py)
# in the headless build: a ListView scrolled by 600 px shows a later first row, and nothing fails.
# Usage: run-lists.sh <headless build dir> <widgets.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"

rc=0
(cd "$build" && env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data" timeout 15 \
  ./android-translation-layer "$apk" -l dev/dine/widgets/ListsActivity -X -Xint) >"$log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }
grep -qE "UnsatisfiedLinkError|FATAL EXCEPTION|JNI DETECTED ERROR" "$log" && fail "an exception or a missing native"
grep -qE "lists first [1-9][0-9]* of [0-9]+ shown" "$log" || fail "no \"lists first\" past row 0 (exit status $rc)"
echo "PASS"
