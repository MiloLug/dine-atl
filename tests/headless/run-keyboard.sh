#!/usr/bin/env bash
# Keyboard avoidance in the headless build: the windows APK's keyboard activities (windows/, built by
# the superproject's tests/harness/apk.py) get a 200 dp IME inset over a 30 dp navigation bar through
# the cli host's -c, then lose it. Each logs what its content sees ("keyboard: <name> ime nav size
# field"):
# - PanActivity (adjustPan): the window keeps its size and insets, its root moves up until the
#   focused field at the bottom shows above the keyboard (keyboard-pan.viewtree);
# - ResizeActivity (adjustResize): the window lays out above the keyboard, its bottom insets are 0
#   there, and its ScrollView scrolls the focused field into the smaller room
#   (keyboard-resize.viewtree);
# - UnspecifiedActivity: no mode and a ScrollView, a scroll container: it resizes;
# - PanScrollActivity, ResizePlainActivity: the manifest's mode against what the layout alone would
#   give; the pan is only what the field 140 px above the bottom needs; UnspecifiedPlainActivity: no
#   mode, no scroll container: it pans; PanTopActivity: a field clear of the keyboard, no pan;
# - PanActivity and ResizeActivity again at 480 dpi, where a dp of the insets is 3 px;
# - windows over the activity (WindowKeyboardActivity): a floating dialog, adjustPan or adjustResize,
#   centred, at the bottom or as tall as the window, is placed in the room above the keyboard (the
#   content frame's bottom is the keyboard's top), and a popup below a field of an adjustPan
#   activity (the goldens keyboard-dialog-* and keyboard-popup*);
# - the state half of the soft input mode: stateVisible opens the keyboard with an activity or a
#   dialog, no tap; an activity that resizes for the keyboard opens it going into its field.
# Usage: run-keyboard.sh [--update] <headless build dir> <windows.apk>; --update rewrites the goldens
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
update=0
if [[ ${1-} == --update ]]; then
  update=1
  shift
fi
here="$(cd "$(dirname "$0")" && pwd)"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# start <activity> [<dpi>]
start() {
  log="$work/$1${2:+-$2}.log"
  mkdir -p "$work/data-$1"
  : >"$log"
  coproc ATL {
    cd "$build"
    exec env "${headless_env[@]}" DINE_PREFIX="$work/data-$1" timeout 60 \
      ./android-translation-layer -c ${2:+-d "$2"} "$apk" -l "dev/dine/windows/$1" -X -Xint 2>&1
  }
  session
}

# a "keyboard:" line after the last wanted one fails here: ATL answers a dump first
stop() {
  send dump
  read_dump 'keyboard: '
  end_session
}

# want <line>: the next "keyboard:" line is exactly it
want() {
  local line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == *"keyboard: "* ]] || continue
    [[ $line == *": $1" ]] && return 0
    fail "got \"${line#*: }\", want \"$1\""
  done
  fail "ATL went quiet or exited before \"$1\""
}

# on <hint>: the next toolkit line about a keyboard is its "on" for the field with that hint
on() {
  local line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == "keyboard: "* ]] || continue
    [[ $line == "keyboard: on android.widget.EditText \"$1\" "* ]] && return 0
    fail "got \"$line\", want the keyboard on for \"$1\""
  done
  fail "ATL went quiet or exited before the keyboard on for \"$1\""
}

# tree <golden>: the dump's view tree against it
tree() {
  send dump
  read_dump 'keyboard: '
  printf '%s\n' "${dump[@]}" >"$work/tree"
  if ((update)); then
    cp "$work/tree" "$here/$1"
  else
    diff -u "$here/$1" "$work/tree" || fail "view tree differs from $1"
  fi
}

# dp = px at the default density; the window is 960x540
bar='0 0 0 0 0 0 0 0 0 0 0 30'
keyboard="$bar 0 0 0 0 0 0 0 200"

start PanActivity
want "pan ime 0 nav 0 size 960x540 field 523"
send "insets $keyboard"
want "pan ime 200 nav 30 size 960x540 field 323"
tree keyboard-pan.viewtree
send "insets $bar"
want "pan ime 0 nav 30 size 960x540 field 523"
stop

start ResizeActivity
want "resize ime 0 nav 0 size 960x540 field 400"
on "resize field" # the window resizes for the keyboard: going forward into a field opens it
send "insets $keyboard"
want "resize ime 0 nav 0 size 960x340 field 323"
tree keyboard-resize.viewtree
send "insets $bar"
want "resize ime 0 nav 30 size 960x540 field 400"
stop

start UnspecifiedActivity
want "unspecified ime 0 nav 0 size 960x540 field 400"
on "unspecified field" # a scroll container: it resizes
send "insets $keyboard"
want "unspecified ime 0 nav 0 size 960x340 field 323"
stop

# the field's bottom, 417, comes up to the keyboard's top, 340: 77 of the keyboard's 200
start PanScrollActivity
want "panscroll ime 0 nav 0 size 960x540 field 400"
send "insets $keyboard"
want "panscroll ime 200 nav 30 size 960x540 field 323"
stop

start ResizePlainActivity
want "resizeplain ime 0 nav 0 size 960x540 field 523"
on "resizeplain field"
send "insets $keyboard"
want "resizeplain ime 0 nav 0 size 960x340 field 323"
stop

start UnspecifiedPlainActivity
want "plain ime 0 nav 0 size 960x540 field 523"
send "insets $keyboard"
want "plain ime 200 nav 30 size 960x540 field 323"
stop

start PanTopActivity
want "pantop ime 0 nav 0 size 960x540 field 0"
send "insets $keyboard"
want "pantop ime 200 nav 30 size 960x540 field 0"
stop

# windows over the activity: a floating dialog (400x300, field at the bottom) is placed in the content
# frame, inside the navigation bar (510 px high: 105 down, not 120) and above the keyboard (340 to
# 540: 20 down), adjustPan or not; the keyboard covers none of it, so it pans nowhere
start DialogPanActivity
want "dialogpan ime 0 size 400x300 top 120 field 283 above true"
send "insets $bar"
want "dialogpan ime 0 size 400x300 top 105 field 283 above true"
send "insets $keyboard"
want "dialogpan ime 65 size 400x300 top -45 field 218 above true" # ATL-165: in its new place, with the pan and insets of the old frame
want "dialogpan ime 0 size 400x300 top 20 field 283 above true"
tree keyboard-dialog-pan.viewtree
send "insets $bar"
want "dialogpan ime 0 size 400x300 top 105 field 283 above true"
stop

# at the bottom of the content frame (210 to 510): the room above the keyboard ends at 340, where it ends
start DialogBottomActivity
want "dialogbottom ime 0 size 400x300 top 240 field 283 above true"
on "dialogbottom field" # stateVisible
send "insets $bar"
want "dialogbottom ime 0 size 400x300 top 210 field 283 above true"
send "insets $keyboard"
want "dialogbottom ime 170 size 400x300 top -130 field 113 above true" # ATL-165: in its new place, with the pan and insets of the old frame
want "dialogbottom ime 0 size 400x300 top 40 field 283 above true"
tree keyboard-dialog-bottom.viewtree
send "insets $bar"
want "dialogbottom ime 0 size 400x300 top 210 field 283 above true"
stop

# as tall as the content frame (510 px): the room above the keyboard is 340, the window takes it
start DialogTallActivity
want "dialogtall ime 0 size 400x540 top 0 field 523 above true"
send "insets $bar"
want "dialogtall ime 0 size 400x510 top 0 field 493 above true"
send "insets $keyboard"
want "dialogtall ime 170 size 400x340 top -170 field 153 above true" # ATL-165: in its new place, with the pan and insets of the old frame
want "dialogtall ime 0 size 400x340 top 0 field 323 above true"
tree keyboard-dialog-tall.viewtree
send "insets $bar"
want "dialogtall ime 0 size 400x510 top 0 field 493 above true"
stop

# WRAP_CONTENT and 400 px high, the room above the keyboard 340: DisplayPolicy centres it in the cut
# content frame (pf) and Gravity.applyDisplay moves it into the display frame, which the keyboard
# doesn't cut for adjustPan (df is mDock): it is shifted down to the top, not shrunk, and its
# field, covered by the keyboard for 60 px, is panned up as far as it needs
start DialogOverActivity
want "dialogover ime 0 size 400x400 top 70 field 383 above true"
send "insets $bar"
want "dialogover ime 0 size 400x400 top 55 field 383 above true"
send "insets $keyboard"
want "dialogover ime 115 size 400x400 top -115 field 268 above true" # ATL-165: in its new place, with the pan and insets of the old frame
want "dialogover ime 60 size 400x400 top -60 field 323 above true"
tree keyboard-dialog-over.viewtree
send "insets $bar"
want "dialogover ime 0 size 400x400 top 55 field 383 above true"
stop

# adjustResize: the dialog is placed again in the room above the keyboard (540 - 200 = 340)
start DialogResizeActivity
want "dialogresize ime 0 size 400x300 top 120 field 283 above true"
on "dialogresize field"
send "insets $bar"
want "dialogresize ime 0 size 400x300 top 105 field 283 above true"
send "insets $keyboard"
want "dialogresize ime 65 size 400x300 top 20 field 283 above true" # ATL-165: in its new place, with the insets of the old frame
want "dialogresize ime 0 size 400x300 top 20 field 283 above true"
tree keyboard-dialog-resize.viewtree
send "insets $bar"
want "dialogresize ime 0 size 400x300 top 105 field 283 above true"
tree keyboard-dialog-resize-off.viewtree # back in the content frame
stop

# the state half of the soft input mode: stateVisible opens the keyboard with the window, no tap
start VisibleActivity
on "visible field"
stop

start DialogVisibleActivity
on "dialogvisible field"
stop

# a ListPopupWindow below the field at 400: its window follows the field's pan
start PopupKeyboardActivity
want "popuppan ime 0 size 960x540 top 0 field 400 above true"
send "tap open"
want "popup shown"
tree keyboard-popup.viewtree
send "insets $keyboard"
want "popuppan ime 200 size 960x540 top -77 field 323 above true"
tree keyboard-popup-pan.viewtree
send "insets $bar"
want "popuppan ime 0 size 960x540 top 0 field 400 above true"
stop

# 480 dpi: a dp is 3 px, a line of text 51 px; the window keeps its 960x540 px, so a 60 dp keyboard
# over a 10 dp navigation bar
keyboard="0 0 0 0 0 0 0 0 0 0 0 10 0 0 0 0 0 0 0 60"
start PanActivity 480
want "pan ime 0 nav 0 size 960x540 field 489"
send "insets $keyboard"
want "pan ime 180 nav 30 size 960x540 field 309"
stop

start ResizeActivity 480
want "resize ime 0 nav 0 size 960x540 field 400"
on "resize field"
send "insets $keyboard"
want "resize ime 0 nav 0 size 960x360 field 309"
stop

echo PASS
