#!/usr/bin/env bash
# Drives the widgets APK's ControlsActivity (widgets/, built by the superproject's
# tests/harness/apk.py) through the cli host's -c touch and key lines in the headless build; controls
# are found by their text in the view-tree dump, the ImageButton by its class. All caps upper-case
# the shown text ("push" as "PUSH"), leaving markup's tags and entities as they are. A tap clicks a
# Button, the disabled "locked" takes no click, a CheckBox and a Switch toggle, a RadioButton checks
# and unchecks the group's other one but never itself, a ToggleButton shows its on text, the
# ImageButton clicks, Tab then Enter clicks the focused first button, ESC is Back on the task root.
# The checked lines must come in order: one that must not come is caught by the next ones.
# Usage: run-controls.sh <headless build dir> <widgets.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ATL_DUMP_VIEWTREE=1 ANDROID_APP_DATA_DIR="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/widgets/ControlsActivity -X -Xint 2>&1
}
pid=$ATL_PID
: >"$work/log"

fail() { kill "$pid" 2>/dev/null || true; echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }

# the dump's frames are relative to the parent: each depth's origin, window px
declare -A x y w h
origin_x=() origin_y=()
dump_re='^viewtree: ( *)([^ ]+) (-?[0-9]+),(-?[0-9]+) ([0-9]+)x([0-9]+)( "(.*)")?$'
while :; do
  IFS= read -r -t 30 line <&"${ATL[0]}" || fail "no view-tree dump"
  printf '%s\n' "$line" >>"$work/log"
  [[ $line == "viewtree: end" ]] && break
  [[ $line =~ $dump_re ]] || continue
  depth=$((${#BASH_REMATCH[1]} / 2))
  left=${BASH_REMATCH[3]} top=${BASH_REMATCH[4]}
  if ((depth > 0)); then
    left=$((left + origin_x[depth - 1])) top=$((top + origin_y[depth - 1]))
  fi
  origin_x[depth]=$left origin_y[depth]=$top
  name=${BASH_REMATCH[8]}
  [[ ${BASH_REMATCH[2]} == android.widget.ImageButton ]] && name=image
  if [[ -n $name ]]; then
    x[$name]=$left y[$name]=$top w[$name]=${BASH_REMATCH[5]} h[$name]=${BASH_REMATCH[6]}
  fi
done
for control in PUSH locked check red green switch "lamp off" image "<b>BOLD</b> &amp; CAPS"; do
  [[ -n ${x[$control]:-} ]] || fail "no \"$control\" in the dump"
done

# the next checked line into REPLY, without the log prefix
next() {
  local line re='(controls (click|checked|radio) .*|^host: task .*)'
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

tap() {
  local cx=$((x[$1] + w[$1] / 2)) cy=$((y[$1] + h[$1] / 2))
  send "touch down 0 $cx $cy"
  send "touch up 0 $cx $cy"
}

key() { send "key down $1"; send "key up $1"; }

tap PUSH
expect "controls click push"

tap locked
tap check
expect "controls checked check true"
tap check
expect "controls checked check false"

tap red
expect "controls checked red true" "controls radio red"
tap green
expect "controls checked green true" "controls checked red false" "controls radio green"
tap green

tap switch
expect "controls checked switch true"

tap "lamp off"
expect "controls click toggle lamp on"

tap image
expect "controls click image"

key 61 # Tab: leaves touch mode on the first focusable view
key 66 # Enter
expect "controls click push"

key 111 # Escape
expect "host: task back app"

kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo "PASS"
