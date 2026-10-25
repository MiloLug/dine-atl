#!/usr/bin/env bash
# Drives the widgets APK's TextActivity (widgets/, built by the superproject's tests/harness/apk.py)
# through the cli host's -c touch and text lines in the headless build. The fields (name, notes, pin,
# enter, capped) are the dump's EditTexts in order, the buttons are found by their text. The field
# applies a text line at once ("edit:" lines) and reports it; Java's filters and watchers answer, a
# state that differs comes back as a snapshot the field takes unless it holds a newer report or
# marked text ("edit: drop"). The checked lines must come in order.
# Usage: run-text.sh <headless build dir> <widgets.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ATL_DUMP_VIEWTREE=1 ANDROID_APP_DATA_DIR="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/widgets/TextActivity -X -Xint 2>&1
}
pid=$ATL_PID
: >"$work/log"

fail() { kill "$pid" 2>/dev/null || true; echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }

# the dump's frames are relative to the parent: each depth's origin, window px
declare -A x y w h
origin_x=() origin_y=()
fields=(name notes pin enter capped)
field=0
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
  if [[ ${BASH_REMATCH[2]} == android.widget.EditText ]] && ((field < ${#fields[@]})); then
    name=${fields[field]}
    field=$((field + 1))
  fi
  if [[ -n $name ]]; then
    x[$name]=$left y[$name]=$top w[$name]=${BASH_REMATCH[5]} h[$name]=${BASH_REMATCH[6]}
  fi
done
for control in name notes pin enter capped fill show size; do
  [[ -n ${x[$control]:-} ]] || fail "no \"$control\" in the dump"
done

# the next checked line into REPLY, without the log prefix
next() {
  local line re='(text (before|on|after|focus|action|show|name|notes|pin|enter|capped) .*|text fill$|^edit: .*|^keyboard: .*|^text: .*|^host: task .*)'
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

center() { echo "$((x[$1] + w[$1] / 2)) $((y[$1] + h[$1] / 2))"; }

tap() {
  local at
  at=$(center "$1")
  send "touch down 0 $at"
  send "touch up 0 $at"
}

keyboard() { echo "keyboard: $1 android.widget.EditText \"$2\""; }

# a tap focuses the field through Java and brings the keyboard
tap name
expect "text focus name true" "$(keyboard on name)"

# the filter drops the digit: watchers see the filtered insertion, the field gets it back
send "text commit hi1"
expect 'edit: "hi1" 3,3' 'text before 0 0 2 ""' 'text on 0 0 2 "hi"' 'text after "hi"' 'edit: "hi" 2,2'

send "text delete 1 0"
expect 'edit: "h" 1,1' 'text before 1 1 0 "hi"' 'text on 1 1 0 "h"' 'text after "h"'

# a pair of UTF-16 units: a delete of 2 before the caret takes it whole
send "text commit 😀"
expect 'edit: "h😀" 3,3' 'text before 1 0 2 "h"' 'text on 1 0 2 "h\ud83d\ude00"' 'text after "h\ud83d\ude00"'
send "text delete 2 0"
expect 'edit: "h" 1,1' 'text before 1 2 0 "h\ud83d\ude00"' 'text on 1 2 0 "h"' 'text after "h"'

send "text select 0 1"
expect 'edit: "h" 0,1'
send "text commit Yo"
expect 'edit: "Yo" 2,2' 'text before 0 1 2 "h"' 'text on 0 1 2 "Yo"' 'text after "Yo"'

# Java's setText during a composition: the field keeps its marked text, the commit lands at Java's
# cursor
send "text compose ni"
expect 'edit: "Yoni" 4,4'
tap fill
expect "text fill" 'text before 0 2 6 "Yo"' 'text on 0 2 6 "filled"' 'text after "filled"' 'edit: drop 6'
send "text commit 你"
expect 'edit: "Yo你" 3,3' 'text before 6 0 1 "filled"' 'text on 6 0 1 "filled\u4f60"' 'text after "filled\u4f60"' 'edit: "filled你" 7,7'

# a report still on its way: Java's snapshot is dropped, the report lands at Java's cursor
send "text hold"
send "text commit 1x"
expect 'edit: "filled你1x" 9,9'
tap fill
expect "text fill" 'text before 0 7 6 "filled\u4f60"' 'text on 0 7 6 "filled"' 'text after "filled"' 'edit: drop 7'
send "text release"
expect 'text before 6 0 1 "filled"' 'text on 6 0 1 "filledx"' 'text after "filledx"' 'edit: "filledx" 7,7'

tap show
expect 'text show "filledx" 7,7'

# Next: the listener sees it, focus and the keyboard move on
send "text action"
expect "text action name 5" "text focus name false" "text focus notes true" "$(keyboard off name)" "$(keyboard on notes)"

# a tap the toolkit takes itself moves Java's focus
send "text tap $(center pin)"
expect "$(keyboard off notes)" "$(keyboard on pin)" "text focus notes false" "text focus pin true"
send "text commit 1a2345"
expect 'edit: "1a2345" 6,6' 'text pin "123"' 'edit: "123" 3,3'

# Done hides the keyboard, focus stays
send "text action"
expect "$(keyboard off pin)"
send "text commit 9"
expect "text: no field"

# IME_FLAG_NO_ENTER_ACTION: return is an Enter key; the listener takes its down, then its up
send "text tap $(center enter)"
expect "$(keyboard on enter)" "text focus pin false" "text focus enter true"
send "text action"
expect "text action enter 0 key 0" "text action enter 0 key 1"

# maxLines 2: the capped field grows to two lines, then keeps its height
send "text tap $(center capped)"
expect "$(keyboard off enter)" "$(keyboard on capped)" "text focus enter false" "text focus capped true"
capped_height() {
  tap size
  next
  [[ $REPLY =~ ^text\ capped\ ([0-9]+)$ ]] || fail "got \"$REPLY\", want \"text capped <height>\""
  REPLY=${BASH_REMATCH[1]}
}
capped_height
one=$REPLY
send 'text commit 1\n2'
expect 'edit: "1\n2" 3,3' 'text capped "1\n2"'
capped_height
two=$REPLY
send 'text commit \n3\n4'
expect 'edit: "1\n2\n3\n4" 7,7' 'text capped "1\n2\n3\n4"'
capped_height
((one < two && REPLY == two)) || fail "capped heights $one, $two, $REPLY: want growth to two lines, then the same"

# a bare EditText is multi-line: return is a newline (it grows: taps below it use old frames)
send "text tap $(center notes)"
expect "$(keyboard off capped)" "$(keyboard on notes)" "text focus capped false" "text focus notes true"
send 'text commit a\nb'
expect 'edit: "a\nb" 3,3' 'text notes "a\nb"'
send "text action"
expect 'edit: "a\nb\n" 4,4' 'text notes "a\nb\n"'

# focus moves with the keyboard up: the keyboard follows; Back hides it first, then goes back
tap name
expect "text focus notes false" "text focus name true" "$(keyboard off notes)" "$(keyboard on name)"
send "back"
expect "$(keyboard off name)"
send "back"
expect "host: task back app"

kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo "PASS"
