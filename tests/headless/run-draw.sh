#!/usr/bin/env bash
# Runs the widgets APK's DrawActivity (widgets/, built by the superproject's tests/harness/apk.py):
# a view drawn through Canvas, invalidated once, then laid out again at the same size.
# --headless: no display; the display list of each draw and the draw lines must match
# draw.displaylist (--update rewrites it). Without it (gtk): the view draws its three times, no
# Canvas native is missing and GTK reports no unbalanced snapshot pushes, and the readback of what
# the view's recording draws holds; the order of its draws isn't compared.
# Usage: run-draw.sh [--headless] [--update] <build dir> <widgets.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
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

env=("${atl_env[@]}" DINE_PREFIX="$work/data" ATL_DUMP_DISPLAYLIST=1)
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
rc=0
(cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/widgets/DrawActivity \
  -X -Xint) >"$log" 2>&1 || rc=$?

# logged after the third draw
grep -q "widgets draw done" "$log" || fail "no \"widgets draw done\" (exit status $rc)"
faults "$log"
grep -q "widgets bitmap pixel ffff0000 0$" "$log" || fail "no red pixel read back from a Canvas(Bitmap)"
grep -q "widgets photo pixels ffff0000 ff00a000 ff0000ff ffffc800$" "$log" \
  || fail "the PNG resource's pixels read back wrong: $(grep -o 'widgets photo pixels.*' "$log")"
grep -q "widgets blend ok$" "$log" || fail "blend modes read back wrong: $(grep -o 'widgets blend.*' "$log")"
grep -q "widgets pixels ok$" "$log" || fail "bitmap pixels read back wrong: $(grep -o 'widgets pixels [a-z].*' "$log")"
grep -q "widgets layers ok$" "$log" || fail "layer blends read back wrong: $(grep -o 'widgets layers [a-z].*' "$log")"
# the view's own replay read back: clips, a PNG, a filtered drawable, a gradient, a stroke, text (gsk at scale 1
# only: the phone adds 3); headless draws no views
if ((headless)); then
  grep -q "widgets view pixels none: this build draws no views$" "$log" || fail "no \"none\" line for the view readback: $(grep -o 'widgets view pixels.*' "$log")"
else
  grep -q "widgets view pixels ok scales 1$" "$log" || fail "view pixels read back wrong: $(grep -o 'widgets view pixels.*' "$log")"
fi
grep -qE "Gtk-CRITICAL|Gsk-CRITICAL|gtk_snapshot_(push|pop)" "$log" && fail "unbalanced snapshot pushes or a GTK critical"
if ((headless)); then
  grep -q '^displaylist:.*text "picture"' "$log" || fail "the Picture's text isn't replayed as text"
  grep -oE '^displaylist:.*|widgets draw .*' "$log" >"$work/displaylist" || true
  if ((update)); then
    cp "$work/displaylist" "$expected"
    echo "updated $expected"
  else
    diff -u "$expected" "$work/displaylist" || fail "display list differs from draw.displaylist"
  fi
fi
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
