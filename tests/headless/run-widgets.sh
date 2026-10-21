#!/usr/bin/env bash
# Drives the widgets APK (widgets/, built by the superproject's tests/harness/apk.py) through the cli
# host's -c touch and key lines in the headless build; rows are found by their text in the view-tree
# dump. A tap clicks and the selector follows the pressed state; a hold long-clicks without a click;
# the first Tab leaves touch mode and focuses the first row, the next one moves on, Enter clicks it;
# a drag scrolls (headless stands in for the toolkit's pan: the touch is canceled) and the "x" row's
# gesture exclusion follows the scroll and then the translation T gives it; a drag far past the end
# stops at Java's end, padding included; ESC is Back on the task root. The checked lines must come in
# order: one that must not come is caught by the next ones.
# Usage: run-widgets.sh <headless build dir> <widgets.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ATL_DUMP_VIEWTREE=1 ANDROID_APP_DATA_DIR="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/widgets/MainActivity -X -Xint 2>&1
}
pid=$ATL_PID
: >"$work/log"

fail() { kill "$pid" 2>/dev/null || true; echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }

# the dump's frames are relative to the parent: each depth's origin, window px
declare -A x y w h
origin_x=() origin_y=()
dump_re='^viewtree: ( *)[^ ]+ (-?[0-9]+),(-?[0-9]+) ([0-9]+)x([0-9]+)( "(.*)")?$'
while :; do
  IFS= read -r -t 30 line <&"${ATL[0]}" || fail "no view-tree dump"
  printf '%s\n' "$line" >>"$work/log"
  [[ $line == "viewtree: end" ]] && break
  [[ $line =~ $dump_re ]] || continue
  depth=$((${#BASH_REMATCH[1]} / 2))
  left=${BASH_REMATCH[2]} top=${BASH_REMATCH[3]}
  if ((depth > 0)); then
    left=$((left + origin_x[depth - 1])) top=$((top + origin_y[depth - 1]))
  fi
  origin_x[depth]=$left origin_y[depth]=$top
  if [[ $line == *'MainActivity$2 '* ]]; then
    scroll_depth=$depth scroll_h=${BASH_REMATCH[5]}
  elif [[ -n ${scroll_depth:-} && -z ${content_h:-} ]] && ((depth == scroll_depth + 1)); then
    content_h=${BASH_REMATCH[5]}
  fi
  if [[ -n ${BASH_REMATCH[7]} ]]; then
    text=${BASH_REMATCH[7]}
    x[$text]=$left y[$text]=$top w[$text]=${BASH_REMATCH[4]} h[$text]=${BASH_REMATCH[5]}
  fi
done
for row in tap hold one x; do
  [[ -n ${x[$row]:-} ]] || fail "no \"$row\" row in the dump"
done
[[ -n ${content_h:-} ]] || fail "no ScrollView with a child in the dump"
end=$((content_h + 16 - scroll_h)) # the APK's 16 dp bottom padding
center() { echo "$((x[$1] + w[$1] / 2)) $((y[$1] + h[$1] / 2))"; }

# the next checked line into REPLY, without the log prefix
next() {
  local line re='(widgets (click|longclick|selector|scroll|focus|key) .*|^host: (exclusion|task) .*)'
  while IFS= read -r -t 30 line <&"${ATL[0]}"; do
    printf '%s\n' "$line" >>"$work/log"
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

send() {
  printf '> %s\n' "$1" >>"$work/log"
  printf '%s\n' "$1" >&"${ATL[1]}"
}

key() { send "key down $1"; send "key up $1"; }

# "x" spans its 40 dp on both edges' strips; dp = px at the default density
exclusion() {
  local top=$((y[x] + $1))
  echo "host: exclusion left $top-$((top + h[x])) right $top-$((top + h[x]))"
}

send "insets 20 10 20 10 0 10 0 10 0 0 0 10"
expect "$(exclusion 0)"

read -r tap_x tap_y <<<"$(center tap)"
send "touch down 0 $tap_x $tap_y"
send "touch up 0 $tap_x $tap_y"
expect "widgets selector ff00c000" "widgets click tap" "widgets selector ff808080"

read -r hold_x hold_y <<<"$(center hold)"
send "touch down 0 $hold_x $hold_y"
expect "widgets longclick hold"
send "touch up 0 $hold_x $hold_y"

key 61 # Tab
expect "widgets focus tap true"
key 61
expect "widgets focus tap false" "widgets focus one true"
key 66 # Enter
expect "widgets click one"

read -r row_x row_y <<<"$(center x)"
send "touch down 0 $row_x $row_y"
send "touch move 0 $row_x $((row_y - 50))"
send "touch move 0 $row_x $((row_y - 150))"
send "touch up 0 $row_x $((row_y - 150))"
expect "widgets focus one false" "widgets scroll 0 50" "$(exclusion -50)" "widgets scroll 0 150" "$(exclusion -150)"

key 48 # T
expect "widgets key 48" "$(exclusion -110)"

send "touch down 0 $row_x $((row_y - 150))"
send "touch move 0 $row_x $((row_y - 5150))"
send "touch up 0 $row_x $((row_y - 5150))"
expect "widgets scroll 0 $end" "widgets scroll end" "host: exclusion left - right -"

key 111 # Escape
expect "host: task back app"

kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo "PASS"
