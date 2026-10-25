#!/usr/bin/env bash
# Runs the widgets APK's DrawActivity (widgets/, built by the superproject's tests/harness/apk.py):
# a view drawn through Canvas, invalidated once, then laid out again at the same size.
# --headless: no display; the display list of each draw and the draw lines must match
# draw.displaylist (--update rewrites it). Without it (gtk): the view draws twice and no Canvas
# native is missing.
# Usage: run-draw.sh [--headless] [--update] <build dir> <widgets.apk>
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
headless=0 update=0
while [[ ${1-} == --* ]]; do
  case $1 in
    --headless) headless=1 ;;
    --update) update=1 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"
expected="$here/draw.displaylist"

env=(RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" ATL_DUMP_DISPLAYLIST=1)
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
rc=0
(cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/widgets/DrawActivity \
  -X -Xint) >"$log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }
grep -q "widgets draw done" "$log" || fail "no \"widgets draw done\" (exit status $rc)"
grep -q "UnsatisfiedLinkError" "$log" && fail "a native is missing"
if ((headless)); then
  grep -oE '^displaylist:.*|widgets draw .*' "$log" >"$work/displaylist" || true
  if ((update)); then
    cp "$work/displaylist" "$expected"
    echo "updated $expected"
  else
    diff -u "$expected" "$work/displaylist" || fail "display list differs from draw.displaylist"
  fi
else
  grep -q "widgets draw 2$" "$log" || fail "no second draw after invalidate()"
fi
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
