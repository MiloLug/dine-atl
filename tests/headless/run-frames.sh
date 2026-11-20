#!/usr/bin/env bash
# Runs the widgets APK's PassesActivity (widgets/, built by the superproject's tests/harness/apk.py)
# on the records ui backend: the frames of AOSP's ViewRootImpl. An OnPreDrawListener that cancels three
# frames holds the view's draw back until its fourth call says go, one that cancels an invalidate's
# draw-only pass does the same, a layout asked for from a global layout call is done before the next
# frame's callbacks run; a listener that asks for another
# layout in every call gets passes at the frame rate (about 100 calls in 500 ms: two layouts a frame),
# no "layout still requested" and a main loop that still runs the message posted for the end of it.
# gtk has its own frame clock and takes no part.
# Usage: run-frames.sh <headless build dir> <widgets.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"

rc=0
(cd "$build" && env "${headless_env[@]}" DINE_PREFIX="$work/data" ATL_DUMP_DISPLAYLIST=1 timeout 60 \
  ./android-translation-layer "$apk" -l dev/dine/widgets/PassesActivity -X -Xint) >"$log" 2>&1 || rc=$?

grep -q "passes layouts " "$log" || fail "no \"passes layouts\" line (exit status $rc)"
faults "$log"

# three cancels, then the go, then the draw; an invalidate's cancel, go and draw; the second round
# before the frame's callbacks
mapfile -t lines < <(grep -oE 'passes (invalidate )?predraw [0-9]+ (cancel|go)|passes round [12]|passes frame$|^displaylist: .*PassesActivity\$Drawing 960x540' "$log")
want=("passes predraw 1 cancel" "passes predraw 2 cancel" "passes predraw 3 cancel" "passes predraw 4 go"
      "displaylist: dev.dine.widgets.PassesActivity\$Drawing 960x540"
      "passes invalidate predraw 1 cancel" "passes invalidate predraw 2 go"
      "displaylist: dev.dine.widgets.PassesActivity\$Drawing 960x540"
      "passes round 1" "passes round 2" "passes frame")
for i in "${!want[@]}"; do
  [[ ${lines[i]-} == "${want[i]}" ]] || fail "step $((i + 1)) of the pass lines is \"${lines[i]-}\", want \"${want[i]}\""
done
grep -q "passes drawn after 4 calls$" "$log" || fail "the view didn't draw after the fourth call"
grep -q "passes invalidate drawn after 2 calls$" "$log" || fail "the invalidated view didn't draw after the second call"

line=$(grep -o 'passes layouts [0-9]* in [0-9]* ms' "$log")
read -r _ _ passes _ ms _ <<<"$line"
((passes >= 20 && passes <= 400)) || fail "$passes layout passes in 500 ms: not the frame rate ($line)"
((ms < 700)) || fail "the message posted for 500 ms came after $ms ms ($line)"
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
