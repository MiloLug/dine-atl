#!/usr/bin/env bash
# Drives the windows APK (windows/, built by the superproject's tests/harness/apk.py) in the
# headless build through the cli host's -c:
# - MainActivity: the shown activity's window brightness reaches the cli host while it shows, the
#   screen's own (-1) while the task is in the back (Home); a brightness set there reaches the host
#   only on the return; -1 again once Back moved the task to the back;
# - StateActivity: its dialog's keep-screen-on, brightness (over the activity's own) and exclusion
#   rect, and its FLAG_FULLSCREEN, reach the host while the dialog shows, not while the task is in
#   the back or after Back closed the dialog, which leaves the task in front; the dialog's root
#   takes the part of the insets its frame overlaps, also at 480 dpi, where the insets' dp are 3 px
#   each and the exclusion span comes back in dp;
# - OverlayActivity: WindowManager.addView windows in the registry's order: a panel above the
#   dialog it is attached to, a TYPE_TOAST window above every dialog and a TYPE_APPLICATION_OVERLAY
#   one above the toast; a hide and a show leave a dialog where it was added, a dismiss and a show
#   put it on top without the panel it had; a panel attached to the activity under them;
# - PlaceActivity: a floating dialog in the content frame (inside the bars the insets leave), a
#   non-floating one in the whole window, FLAG_LAYOUT_NO_LIMITS and the fit to the display frame, a
#   popup past the window's edge moved inside it;
# - DrawActivity: the dialog's views are drawn as the activity's are;
# - PopupActivity: a PopupWindow below its anchor, its row's tap, and an outside tap that closes it
#   (ACTION_OUTSIDE) and reaches the activity; an AutoCompleteTextView's drop-down after typing,
#   closed by Back before the keyboard takes it, then its row filling the field;
# - MenuActivity: a PopupMenu's items as the backend gets them ("menu:" lines), a picked item, then
#   a dismissal, answered from ATL_DIALOG_ANSWERS, then Back closing an open menu, not the activity;
# - ToastActivity: two text toasts ("toast:" lines) for 2 s each and a setView one (a toast window)
#   for 3.5 s, one after the other, Home hiding the one up, and one made in the back that never
#   shows.
# --gtk: the gtk build on a display, which has no -c: SelfActivity drives itself through a popup
# below its anchor, a menu answered from ATL_DIALOG_ANSWERS and a toast, in that order, then
# finishes.
# Usage: run-windows.sh [--gtk] <build dir> <windows.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
gtk=0
if [[ ${1-} == --gtk ]]; then
  gtk=1
  shift
fi
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if ((gtk)); then
  log="$work/SelfActivity.log"
  mkdir -p "$work/data"
  rc=0
  (cd "$build" && env "${atl_env[@]}" DINE_PREFIX="$work/data" ATL_DIALOG_ANSWERS=Copy timeout 60 \
    ./android-translation-layer "$apk" -l dev/dine/windows/SelfActivity -X -Xint) >"$log" 2>&1 || rc=$?
  lines=("self popup at 20,60" "menu: presented 2" "self menu item Copy" "self menu dismissed" 'toast: shown "self toast"' "self done")
  for line in "${lines[@]}"; do
    grep -qF "$line" "$log" || fail "no \"$line\" (exit status $rc)"
  done
  [[ $(grep -oF "${lines[@]/#/-e}" "$log") == "$(printf '%s\n' "${lines[@]}")" ]] || fail "the popup, menu and toast lines are out of order"
  faults "$log"
  [[ $rc == 0 ]] || fail "exit status $rc"
  echo PASS
  exit 0
fi

# start <activity> [<answers> [<dpi>]]: ATL in a coproc, its output in $work/<activity>.log; answers:
# ATL_DIALOG_ANSWERS
start() {
  log="$work/$1${3:+-$3}.log"
  mkdir -p "$work/data-$1"
  : >"$log"
  local answers=()
  [[ -n ${2-} ]] && answers=(ATL_DIALOG_ANSWERS="$2")
  coproc ATL {
    cd "$build"
    exec env "${headless_env[@]}" DINE_PREFIX="$work/data-$1" "${answers[@]}" timeout 60 \
      ./android-translation-layer -c ${3:+-d "$3"} "$apk" -l "dev/dine/windows/$1" -X -Xint 2>&1
  }
  session
}

host_lines='^host: (keep screen on|brightness|exclusion|task)'

# want <line>: reads up to that exact line, or a log line ending in it; a "host:" line of the window
# state or the task on the way fails, so their order is checked, other lines are skipped. now: when
# the line came, in microseconds
want() {
  local line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    now=${EPOCHREALTIME/[.,]/}
    [[ $line == "$1" || $line == *": $1" ]] && return 0
    [[ $line =~ $host_lines ]] && fail "got \"$line\", want \"$1\""
  done
  fail "ATL went quiet or exited before \"$1\""
}

# a host line after the session's last wanted one fails here: ATL answers a dump first
stop() {
  send dump
  want "viewtree: end"
  end_session
}

start MainActivity
want "host: brightness 0.50"
send home
want "host: brightness -1.00"
want "host: task back host"
want "windows brightness set while stopped" # no host line for it: the activity isn't shown
send front
want "host: brightness 0.70"
want "host: task front host"
send back # the task root: the task moves to the back
want "host: brightness -1.00"
want "host: task back app"
stop

start StateActivity
want "host: brightness 0.60" # the activity's own, before its dialog shows
want "host: keep screen on 1"
want "host: brightness 0.30" # the topmost window's brightness over the activity's
want "window: frame #ff1a1b20 fullscreen" # the dialog's FLAG_FULLSCREEN reaches the frame
want "state dialog laid out"
# system gestures 20 left and right, 15 at the top, 25 at the bottom, navigation bar 30 at the
# bottom (dp; px at 160 dpi): the dialog reaches the left strip only
insets="insets 20 15 20 25 0 0 0 0 0 0 0 30"
# state_insets <px>...: the last ones the dialog's root got
state_insets() {
  send dump
  want "viewtree: end"
  local got
  got=$(grep -o "state insets .*" "$log" | tail -n 1)
  [[ $got == "state insets $1" ]] || fail "the dialog at the left edge got \"$got\", want \"state insets $1\""
}
send "$insets"
want "host: exclusion left 100-200 right -"
state_insets "20 0 0 0"
send home
want "host: keep screen on 0"
want "host: brightness 0.60" # for a moment: the activity's own, before the task's end resets it (ATL-158)
want "window: frame #ff1a1b20"
want "host: exclusion left - right -"
want "host: brightness -1.00"
want "host: task back host"
send front
want "host: brightness 0.60" # the activity first, then its dialog
want "host: keep screen on 1"
want "host: brightness 0.30"
want "window: frame #ff1a1b20 fullscreen"
want "host: exclusion left 100-200 right -"
want "host: task front host"
send back # the dialog takes it: the task stays in front
want "host: keep screen on 0"
want "host: brightness 0.60" # the activity's own again
want "window: frame #ff1a1b20"
want "host: exclusion left - right -"
stop

# 480 dpi: the strips are 3 px a dp, the dialog's rect, px 100-200, comes back as dp
start StateActivity "" 480
want "host: brightness 0.60"
want "host: keep screen on 1"
want "host: brightness 0.30"
want "window: frame #ff1a1b20 fullscreen"
want "state dialog laid out"
send "$insets"
want "host: exclusion left 33-67 right -"
state_insets "60 0 0 0"
stop

# windows <pattern>...: the dump's window lines, bottom first, each matching its regex
windows() {
  send dump
  read_dump "$host_lines"
  local line got=()
  for line in "${dump[@]}"; do
    [[ $line == "viewtree: window "* ]] && got+=("${line#viewtree: window }")
  done
  local want=("$@") i
  ((${#got[@]} == ${#want[@]})) || fail "windows \"${got[*]}\", want \"${want[*]}\""
  for ((i = 0; i < ${#want[@]}; i++)); do
    [[ ${got[i]} =~ ^${want[i]}$ ]] || fail "windows \"${got[*]}\", want \"${want[*]}\""
  done
}

start OverlayActivity
want "overlay shown"
# the layers: the activity's panel, the dialogs with the panel attached to A, the toast, the overlay
windows "panel 400,300 100x50" "dialog 0,0 300x100" "panel 10,10 100x50" "dialog 200,50 300x100" "toast 600,0 100x50" "overlay 700,400 100x50"
# AOSP's hide and show of a dialog switch its decor's visibility: the window stays where it was added
send "tap raise"
want "overlay raised"
windows "panel 400,300 100x50" "dialog 0,0 300x100" "panel 10,10 100x50" "dialog 200,50 300x100" "toast 600,0 100x50" "overlay 700,400 100x50"
send "tap close"
want "overlay closed"
windows "panel 400,300 100x50" "dialog 200,50 300x100" "toast 600,0 100x50" "overlay 700,400 100x50"
# shown again: a new add, on top of the dialogs' layer, and its panel went with its first window
send "tap reshow"
want "overlay reshown"
windows "panel 400,300 100x50" "dialog 200,50 300x100" "dialog 0,0 300x100" "toast 600,0 100x50" "overlay 700,400 100x50"
stop

# placement: a floating dialog in the content frame, inside the bars; any window moved inside its
# display frame unless FLAG_LAYOUT_NO_LIMITS; a window of a non-floating theme in the whole window
start PlaceActivity
want "place shown"
want "place probe 300x540"
want "place popup"
windows "panel 760,440 200x100" "dialog 330,440 300x100" "dialog 330,0 300x100" "dialog 0,0 960x540" "dialog -50,-20 300x100" "dialog 0,0 300x100" "dialog 660,0 300x50"
send "insets 0 0 0 0 0 0 0 0 0 0 0 30 0 24 0 0 0 0 0 0" # navigation bar 30 px at the bottom, status bar 24 px at the top
send dump # the windows are placed again in a pass after the insets
want "viewtree: end"
want "place probe 300x486" # the screen less the bars (540 - 24 - 30): ViewRootImpl's screenHeightDp
windows "panel 760,440 200x100" "dialog 330,410 300x100" "dialog 330,24 300x100" "dialog 0,0 960x540" "dialog -50,4 300x100" "dialog 0,24 300x100" "dialog 660,24 300x50"
stop

# the records draw a window over the activity as they draw the activity's own
start DrawActivity
want "draw activity"
want "draw dialog"
stop

start PopupActivity
want "popup activity created"
send "tap anchor"
want "popup shown"
windows "panel 20,60 200x100"
send "tap one"
want "popup row one"
send "tap elsewhere"
want "popup dismissed"
want "popup elsewhere"
windows
# the field, 200x40 at 300,20: a tap focuses it, then two letters bring the drop-down below it
send "touch down 0 400 40"
send "touch up 0 400 40"
send "text commit ap"
want "actv dropdown shown"
windows "panel 300,54 200x129" # its background's padding moves it up from the field's bottom, 60 (ListPopupWindow)
send back
want "actv dropdown dismissed"
windows
send "text commit p"
want "actv dropdown shown"
send "tap apple"
want "actv text apple"
windows
stop

start MenuActivity "Copy;!cancel"
want "menu activity created"
send "tap menu"
want "menu: show 5"
want 'menu: item 0 "Copy"'
want 'menu: item 1 "Paste" disabled'
want 'menu: item 2 "Bold" checkable checked'
want 'menu: item 3 "More" submenu'
want 'menu: item 4 "Deep" in 3'
want "menu: answer 0"
want "menu item Copy"
want "menu dismissed"
send "tap menu"
want "menu: answer -1"
want "menu dismissed"
stop

start MenuActivity
want "menu activity created"
send "tap menu"
want "menu shown"
send back
want "menu: close"
want "menu dismissed"
send "tap menu" # the activity is still there
want "menu shown"
stop

# up <least ms> <most ms>: the time from $shown to the last wanted line. The script's reads time it,
# so the bounds are loose
up() {
  local ms=$(((now - shown) / 1000))
  ((ms >= $1 && ms <= $2)) || fail "the toast was up for $ms ms, want $1 to $2"
}

start ToastActivity
want "toast activity created"
for text in hello world; do
  want "toast: show \"$text\" gravity=0x51 x=0 y=48"
  shown=$now
  want "toast: hide"
  up 1500 3000 # LENGTH_SHORT, 2 s
done
want "toast custom attached"
shown=$now
want "host: keep screen on 1" # the toast's window keeps the screen on, as AOSP's TN asks
windows "toast 454,475 51x17" # its 51x17 text, centred, 48 above the bottom
want "toast custom detached"
up 3000 4500 # LENGTH_LONG, 3.5 s
want "host: keep screen on 0"
want 'toast: show "last" gravity=0x51 x=0 y=48'
send home # within its 3.5 s
want "toast: hide"
want "host: task back host"
want "toast background done"
send front
want "host: task front host"
grep -q 'toast: show "background"' "$log" && fail "a toast made in the back showed"
stop
echo PASS
