#!/usr/bin/env bash
# Drives the widgets APK's TextActivity (widgets/, built by the superproject's tests/harness/apk.py)
# through the cli host's -c touch and text lines in the headless build. The fields (name, notes, pin,
# enter, capped) are the dump's EditTexts in order, the buttons are found by their text. The field
# applies a text line at once ("edit:" lines) and reports it; Java's filters and watchers answer, a
# state that differs comes back as a snapshot the field takes unless it holds a newer report or
# marked text ("edit: drop"). The checked lines must come in order.
# Usage: run-text.sh <headless build dir> <widgets.apk>
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
  exec env "${headless_env[@]}" ATL_DUMP_VIEWTREE=1 DINE_PREFIX="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/widgets/TextActivity -X -Xint 2>&1
}
session

# the dump's frames are relative to the parent: each depth's origin, window px
declare -A x y w h
origin_x=() origin_y=()
fields=(reveal calc locked nofocus date name notes pin enter capped)
field=0
dump_re='^viewtree: ( *)([^ ]+) (-?[0-9]+),(-?[0-9]+) ([0-9]+)x([0-9]+)( "(.*)")?(( (checked|pressed|invisible|gone|disabled|scroll -?[0-9]+,-?[0-9]+|ellipsize (start|middle|end)))*)$'
while :; do
  IFS= read -r -t 30 line <&"$atl_out" || fail "no view-tree dump"
  printf '%s\n' "$line" >>"$log"
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
for control in name reveal calc locked nofocus date notes pin enter capped fill show size eye calckeys lockedkeys; do
  [[ -n ${x[$control]:-} ]] || fail "no \"$control\" in the dump"
done

# the next checked line, or a dump's end, into REPLY, without the log prefix
next() {
  local line re='(text (before|on|after|focus|action|show|eye|calckeys|lockedkeys|name|reveal|calc|locked|nofocus|date|notes|pin|enter|capped) .*|text fill$|^edit: .*|^keyboard: .*|^text: .*|^host: task .*|^viewtree: end$)'
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

center() { echo "$((x[$1] + w[$1] / 2)) $((y[$1] + h[$1] / 2))"; }

tap() {
  local at
  at=$(center "$1")
  send "touch down 0 $at"
  send "touch up 0 $at"
}

# what ui_edit_config says of each field (the hint is the field's name): the keyboard, the return key,
# autocap, secure (a password input: nothing learned), hidden (the text shows as dots), multiline,
# autocorrect and what a tap the toolkit takes does (focus, keyboard, edits); as EditText.atlSendConfig
# resolves them from the input type, the transformation, the focus flags and the IME options
taps="tap_focus=1 tap_keyboard=1 editable=1"
declare -A config=(
  [name]="kind=text return=next autocap=none secure=0 hidden=0 multiline=0 autocorrect=1 $taps"
  [reveal]="kind=text return=next autocap=none secure=1 hidden=1 multiline=0 autocorrect=0 $taps"
  [notes]="kind=text return=default autocap=none secure=0 hidden=0 multiline=1 autocorrect=1 $taps"
  [pin]="kind=number return=done autocap=none secure=1 hidden=1 multiline=0 autocorrect=0 $taps"
  [enter]="kind=text return=default autocap=none secure=0 hidden=0 multiline=0 autocorrect=1 $taps"
  [capped]="kind=text return=default autocap=none secure=0 hidden=0 multiline=1 autocorrect=1 $taps"
  # the password after setTransformationMethod(null): the keyboard still learns nothing
  [revealed]="kind=text return=next autocap=none secure=1 hidden=0 multiline=0 autocorrect=0 $taps"
  # showSoftInputOnFocus(false): a tap shows no keyboard
  [calc]="kind=text return=done autocap=none secure=0 hidden=0 multiline=0 autocorrect=1 tap_focus=1 tap_keyboard=0 editable=1"
  # setKeyListener(null): no input type, so no keyboard on a tap and no edits
  [locked]="kind=text return=done autocap=none secure=0 hidden=0 multiline=0 autocorrect=0 tap_focus=1 tap_keyboard=0 editable=0"
  # a date: the keyboard with digits and separators, no corrections
  [date]="kind=datetime return=done autocap=none secure=0 hidden=0 multiline=0 autocorrect=0 $taps"
  # setFocusable(false): a tap takes no focus
  [nofocus]="kind=text return=done autocap=none secure=0 hidden=0 multiline=0 autocorrect=1 tap_focus=0 tap_keyboard=1 editable=1"
)

# keyboard on|off <field> [<config key>]: the field's own config unless the second key is given
keyboard() { echo "keyboard: $1 android.widget.EditText \"$2\" ${config[${3:-$2}]}"; }

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

# a password's text is hidden as the transformation says, not as the input type: the toggle shows it
# and hides it again, the keyboard goes on learning nothing
send "text tap $(center reveal)"
expect "$(keyboard off notes)" "$(keyboard on reveal)" "text focus notes false" "text focus reveal true"
tap eye
expect "text eye shown"
send "text tap $(center notes)"
expect "$(keyboard off reveal revealed)" "$(keyboard on notes)" "text focus reveal false" "text focus notes true"
send "text tap $(center reveal)"
expect "$(keyboard off notes)" "$(keyboard on reveal revealed)" "text focus notes false" "text focus reveal true"
tap eye
expect "text eye hidden"
send "text tap $(center notes)"
expect "$(keyboard off reveal)" "$(keyboard on notes)" "text focus reveal false" "text focus notes true"

# Android's own tap rule, for a tap the toolkit takes: a field with showSoftInputOnFocus false or no
# input type takes the focus and shows no keyboard until the app asks for one (a hardware keyboard
# types: no input target); one with no input type has its edits refused; a field no tap focuses takes
# none
send "text dismiss"
expect "$(keyboard off notes)"
send "text tap $(center calc)"
expect "text focus notes false" "text focus calc true"
send "text commit x"
expect "text: no field"
tap calckeys
expect "$(keyboard on calc)" "text calckeys true"
send "text commit 7"
expect 'edit: "7" 1,1' 'text calc "7"'
send "text dismiss"
expect "$(keyboard off calc)"
send "text tap $(center locked)"
expect "text focus calc false" "text focus locked true"
tap lockedkeys
expect "$(keyboard on locked)" "text lockedkeys true"
send "text commit x"
expect "edit: refused"
send "text dismiss"
expect "$(keyboard off locked)"
send "text tap $(center nofocus)"
send "text tap $(center date)"
expect "$(keyboard on date)" "text focus locked false" "text focus date true"
send "text commit 1x/2"
expect 'edit: "1x/2" 4,4' 'text date "1/2"' 'edit: "1/2" 3,3'
send "text tap $(center notes)"
expect "$(keyboard off date)" "$(keyboard on notes)" "text focus date false" "text focus notes true"

# focus moves with the keyboard up: the keyboard follows; the toolkit takes the keyboard away (as
# uikit does when its field loses focus): the next Back goes back at once
tap name
expect "text focus notes false" "text focus name true" "$(keyboard off notes)" "$(keyboard on name)"
send "text dismiss"
expect "$(keyboard off name)"
send "back"
expect "host: task back app"
send "front"
expect "host: task front host"

# Back hides the keyboard first: the activity, still in front, answers a tap; then it goes back
tap name
expect "$(keyboard on name)"
send "back"
expect "$(keyboard off name)"
capped_height
send "back"
expect "host: task back app"

# a checked line after the last expected one fails here: ATL answers a dump first
send dump
expect "viewtree: end"
end_session
echo "PASS"
