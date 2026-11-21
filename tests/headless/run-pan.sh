#!/usr/bin/env bash
# Drives the widgets APK's PanActivity (widgets/, built by the superproject's tests/harness/apk.py)
# through the cli host's -c touch lines in the headless build, where a gesture that starts on a
# scroll node and passes the touch slop along its axis is taken as the toolkit's pan is: the app's
# touch gets a CANCEL and the node scrolls. As Android's ScrollView and HorizontalScrollView take
# it, and not otherwise: a child that asked its parent not to intercept on its DOWN keeps the whole
# drag; a ScrollView whose content fits takes none; a HorizontalScrollView whose content fits takes
# it all the same; and a plain child's gesture is taken and the node scrolls by the drag. The
# checked lines must come in order: one that must not come is caught by the next ones.
# Usage: run-pan.sh <headless build dir> <widgets.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"
: >"$log"

cd "$build"
coproc ATL {
  exec env "${headless_env[@]}" DINE_PREFIX="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/widgets/PanActivity -X -Xint 2>&1
}
session

# the next "pan " line, or a dump's end, into REPLY, without the log prefix
next() {
  local line re='(pan .*|^viewtree: end$)'
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    if [[ $line =~ $re ]]; then
      REPLY=${BASH_REMATCH[1]}
      return 0
    fi
  done
  fail "ATL went quiet or exited"
}

# the next lines are exactly the arguments
expect() {
  for want; do
    next
    [[ $REPLY == "$want" ]] || fail "got \"$REPLY\", want \"$want\""
  done
}

# drag <dx> <dy> <top>: down at 60 px right of the node's left edge, 30 below its top, moved by 20
# and by <d>, then up; the slop is 8 px
drag() {
  local x=60 y=$(($3 + 30))
  send "touch down 0 $x $y"
  send "touch move 0 $((x + $1 / 5)) $((y + $2 / 5))"
  send "touch move 0 $((x + $1)) $((y + $2))"
  send "touch up 0 $((x + $1)) $((y + $2))"
}

expect "pan shown"
send dump # the first layout is done by the time its answer comes
expect "viewtree: end"

drag 0 -50 20 # a child that claims the gesture keeps all of it
expect "pan claim ACTION_DOWN" "pan claim ACTION_MOVE" "pan claim ACTION_MOVE" "pan claim ACTION_UP"

drag 0 -50 140 # nothing to scroll: a ScrollView takes no drag
expect "pan fits ACTION_DOWN" "pan fits ACTION_MOVE" "pan fits ACTION_MOVE" "pan fits ACTION_UP"

drag -50 0 260 # a HorizontalScrollView takes it with nothing to scroll
expect "pan wide ACTION_DOWN" "pan wide ACTION_CANCEL"

drag 0 -50 380 # a plain child's gesture is taken, the node scrolls as dragged
expect "pan plain ACTION_DOWN" "pan plain ACTION_CANCEL" "pan scroll plain 10" "pan scroll plain 50"

# a checked line after the last expected one fails here: ATL answers a dump first
send dump
expect "viewtree: end"
end_session
echo "PASS"
