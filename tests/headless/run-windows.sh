#!/usr/bin/env bash
# Drives the windows APK (windows/, built by the superproject's tests/harness/apk.py) in the
# headless build through the cli host's -c:
# - MainActivity: the shown activity's window brightness reaches the cli host while it shows, the
#   screen's own (-1) while the task is in the back (Home), again on its return, and -1 once Back
#   finished the activity;
# - StateActivity: its dialog's keep-screen-on, brightness and exclusion rect reach the host while
#   the dialog shows, not while the task is in the back or after Back closed the dialog; the
#   dialog's root takes the part of the insets its frame overlaps;
# - OverlayActivity: WindowManager.addView windows in the registry's order: a panel above the
#   dialog it is attached to, a TYPE_TOAST window above every dialog, a dialog shown again on top
#   with its panel, the panel gone with its dialog, a panel attached to the activity under them;
# - PopupActivity: a PopupWindow below its anchor, its row's tap, and an outside tap that closes it
#   (ACTION_OUTSIDE) and reaches the activity; an AutoCompleteTextView's drop-down after typing,
#   closed by Back before the keyboard takes it, then its row filling the field;
# - MenuActivity: a PopupMenu's items as the backend gets them ("menu:" lines), a picked item, then
#   a dismissal, answered from ATL_DIALOG_ANSWERS, then Back closing an open menu, not the activity;
# - ToastActivity: two text toasts ("toast:" lines) and a setView one (a toast window), one after
#   the other for 2 s each, Home hiding the one up, and one made in the back that never shows.
# --gtk: the gtk build on a display, which has no -c: SelfActivity drives itself through a popup
# below its anchor, a menu answered from ATL_DIALOG_ANSWERS and a toast, then finishes.
# Usage: run-windows.sh [--gtk] <build dir> <windows.apk>
set -euo pipefail

gtk=0
if [[ ${1-} == --gtk ]]; then
  gtk=1
  shift
fi
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
pid=

fail() { [[ -n $pid ]] && kill "$pid" 2>/dev/null; echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }

if ((gtk)); then
  log="$work/SelfActivity.log"
  mkdir -p "$work/data"
  rc=0
  (cd "$build" && env RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data" ATL_DIALOG_ANSWERS=Copy timeout 60 \
    ./android-translation-layer "$apk" -l dev/dine/windows/SelfActivity -X -Xint) >"$log" 2>&1 || rc=$?
  for line in "self popup at 20,60" "menu: presented 2" "self menu item Copy" "self menu dismissed" 'toast: shown "self toast"' "self done"; do
    grep -qF "$line" "$log" || fail "no \"$line\" (exit status $rc)"
  done
  [[ $rc == 0 ]] || fail "exit status $rc"
  echo PASS
  exit 0
fi

# start <activity> [<answers>]: ATL in a coproc, its output in $work/<activity>.log; answers:
# ATL_DIALOG_ANSWERS
start() {
  log="$work/$1.log"
  mkdir -p "$work/data-$1"
  : >"$log"
  local answers=()
  [[ -n ${2-} ]] && answers=(ATL_DIALOG_ANSWERS="$2")
  coproc ATL {
    cd "$build"
    exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data-$1" "${answers[@]}" timeout 60 \
      ./android-translation-layer -c "$apk" -l "dev/dine/windows/$1" -X -Xint 2>&1
  }
  pid=$ATL_PID
  # the coproc's array is the function's; these stay
  exec {atl_out}<&"${ATL[0]}" {atl_in}>&"${ATL[1]}"
}

stop() {
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  exec {atl_out}<&- {atl_in}>&-
}

send() { echo "$1" >&"$atl_in"; }

# want <line>: reads up to that exact line, or a log line ending in it; a "host:" line of the window
# state on the way fails, so their order is checked, other lines are skipped
want() {
  local line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == "$1" || $line == *": $1" ]] && return 0
    [[ $line =~ ^host:\ (keep\ screen\ on|brightness|exclusion) ]] && fail "got \"$line\", want \"$1\""
  done
  fail "ATL went quiet or exited before \"$1\""
}

start MainActivity
want "host: brightness 0.50"
send home
want "host: brightness -1.00"
send front
want "host: brightness 0.50"
send back
want "host: brightness -1.00"
stop

start StateActivity
want "host: keep screen on 1"
want "host: brightness 0.30"
want "state dialog laid out"
# system gestures 20 left and right, navigation bar 30 at the bottom (dp; px at 160 dpi)
send "insets 20 0 20 0 0 0 0 0 0 0 0 30"
want "host: exclusion left 100-200 right -"
send dump
want "viewtree: end"
grep -q "state insets 20 0 0 0" "$log" || fail "no \"state insets 20 0 0 0\" from the dialog at the left edge"
send home
want "host: keep screen on 0"
want "host: brightness -1.00"
want "host: exclusion left - right -"
send front
want "host: keep screen on 1"
want "host: brightness 0.30"
want "host: exclusion left 100-200 right -"
send back
want "host: keep screen on 0"
want "host: brightness -1.00"
want "host: exclusion left - right -"
stop

# windows <pattern>...: the dump's window lines, bottom first, each matching its regex
windows() {
  send dump
  local line got=()
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == "viewtree: end" ]] && break
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
windows "panel 400,300 100x50" "dialog 0,0 300x100" "panel 10,10 100x50" "dialog 200,50 300x100" "toast 600,0 100x50"
send "tap raise"
want "overlay raised"
windows "panel 400,300 100x50" "dialog 200,50 300x100" "dialog 0,0 300x100" "panel 10,10 100x50" "toast 600,0 100x50"
send "tap close"
want "overlay closed"
windows "panel 400,300 100x50" "dialog 200,50 300x100" "toast 600,0 100x50"
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
windows "panel 300,[0-9]+ 200x[0-9]+" # its background's padding moves it up (ListPopupWindow)
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

start ToastActivity
want "toast activity created"
want 'toast: show "hello" gravity=0x51 x=0 y=48'
want "toast: hide"
want 'toast: show "world" gravity=0x51 x=0 y=48'
want "toast: hide"
want "toast custom attached"
want "host: keep screen on 1" # the toast's window keeps the screen on, as AOSP's TN asks
windows "toast [0-9]+,[0-9]+ [0-9]+x[0-9]+"
want "toast custom detached"
want "host: keep screen on 0"
want 'toast: show "last" gravity=0x51 x=0 y=48'
send home # within its 3.5 s
want "toast: hide"
want "toast background done"
send front
grep -q 'toast: show "background"' "$log" && fail "a toast made in the back showed"
stop
echo PASS
