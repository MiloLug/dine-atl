#!/usr/bin/env bash
# Drives the nav APK (nav/, built by the superproject's tests/harness/apk.py) through the cli host's
# -c commands in the headless build: Back finishes B, Back on the task root A moves the task to the
# back, front and home move it again. A's list, shown while the task is in the back, appears only on
# front and closes and comes back with the task; Back cancels it. The window's frame follows the shown activity: A's
# theme color; none for B, which clears the theme's flag, until B draws its own. Each show logs the
# insets the content gets; B's exclusion rect reaches the host once the insets give the right edge a
# gesture strip, and A, which has none, clears it. Each command's
# lifecycle, dialog, host and window lines must come in order; a command that must do nothing is
# checked by the next command's lines coming first. Then the status bar and IME edges reach the
# content, and a size command lays the window out again; then ImmersiveActivity's bar flags; then
# A and B at 480 dpi, where the insets' and the size's dp are 3 px each and the spans come back in
# dp; last, ResultActivity starts AnswerActivity for a result from inside a tap and gets it once.
# Usage: run-nav.sh <headless build dir> <nav.apk>
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
# start <activity> [<dpi>]
start() {
  coproc ATL {
    exec env "${headless_env[@]}" DINE_PREFIX="$work/data" timeout 60 \
      ./android-translation-layer -c ${2:+-d "$2"} "$apk" -l "dev/dine/nav/$1" -X -Xint 2>&1
  }
  session
}

# the next "nav: ", "host: ", "dialog: " or "window: " line, or a dump's end, into REPLY, without
# the log prefix
next() {
  local line re='(nav: .*|^host: .*|^dialog: .*|^window: .*|^viewtree: end$)'
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

# a checked line after the session's last expected one fails here: ATL answers a dump first
stop() {
  send dump
  expect "viewtree: end"
  end_session
}

start MainActivity
a_frame='window: frame #ff1a1b20' # Theme.DeviceDefault's colorPrimaryDark
none='0 0 0 0 0 0 0 0 0 0 0 0'
expect "nav: A onCreate" "nav: A onStart" "$a_frame" "nav: A insets $none" "nav: A onResume" "nav: A onPause" "nav: A onStop" \
  "nav: B onCreate" "nav: B onStart" "window: frame none" "nav: B insets $none" "nav: B onResume" "window: frame #ff2196f3 light"
# dp = px at the default density: 20 dp side strips, 10 dp top and bottom, mandatory top and bottom,
# a 10 dp navigation bar. B's rects merge to 200-540, cut to 530 by the bottom strip and to the
# bottom-most 200 dp.
insets='20 10 20 10 0 10 0 10 0 0 0 10'
send "insets $insets"
expect "nav: B insets $insets" "host: exclusion left - right 330-530"
send back
expect "nav: B onPause" "nav: B onStop" "nav: A onRestart" "nav: A onStart" "$a_frame" "host: exclusion left - right -" \
  "nav: A insets $insets" "nav: A onResume" "nav: B onDestroy"
send back # the task root: the task moves to the back
expect "nav: A onPause" "nav: A onStop" "host: task back app"
send back # nothing while in the back
send front
expect "nav: A onRestart" "nav: A onStart" "nav: A insets $insets" "nav: A onResume" "host: task front host"
send front # nothing while in front
send home # A shows its list in onStop
expect "nav: A onPause" "nav: A onStop" "host: task back host"
list='dialog: show title="Nav list" items="Close","Odd ��😀��" cancelable'
send front # an activity's windows show once it's resumed, and hide once it's paused
expect "nav: A onRestart" "nav: A onStart" "nav: A insets $insets" "nav: A onResume" "$list" "host: task front host"
send home
expect "nav: A onPause" "dialog: close" "nav: A onStop" "host: task back host"
send front
expect "nav: A onRestart" "nav: A onStart" "nav: A insets $insets" "nav: A onResume" "$list" "host: task front host"
send back # the shown list takes it: cancel, then the close
expect "nav: list canceled" "dialog: close"
# the status bars and the IME, a 24 dp bar and a 200 dp keyboard; then the window's own size
send "insets $insets 0 24 0 0 0 0 0 200"
expect "nav: A insets $insets" "nav: A edges status 0 24 0 0 ime 0 0 0 200 shown"
send "size 200 300"
expect "nav: A insets $insets" "nav: A edges status 0 24 0 0 ime 0 0 0 200 shown" "nav: A size 200x300"
stop

# ImmersiveActivity: the frame line has its flags and its views hear them; while the navigation bar
# hides, its inset is 0; the status bar's reaches it, and the host's size lays it out again
start ImmersiveActivity
expect "nav: I onCreate" "nav: I onStart" "$a_frame cover-top hide-navigation immersive" "nav: I insets $none" "nav: I onResume" \
  "nav: I ui 5122" # LAYOUT_FULLSCREEN | HIDE_NAVIGATION | IMMERSIVE_STICKY
hidden='20 10 20 10 0 10 0 10 0 0 0 0'
send "insets $insets 0 24 0 0 0 0 0 0"
expect "nav: I insets $hidden" "nav: I edges status 0 24 0 0 ime 0 0 0 0 hidden"
send "size 960 600"
expect "nav: I insets $hidden" "nav: I edges status 0 24 0 0 ime 0 0 0 0 hidden" "nav: I size 960x600"
stop

# 480 dpi: the insets reach B as 3 px a dp; B's rects, px, end at the 30 px bottom strip (200-510)
# and come back as dp; the size's dp lay the window out in px, its right edge left of B's rects
start MainActivity 480
expect "nav: A onCreate" "nav: A onStart" "$a_frame" "nav: A insets $none" "nav: A onResume" "nav: A onPause" "nav: A onStop" \
  "nav: B onCreate" "nav: B onStart" "window: frame none" "nav: B insets $none" "nav: B onResume" "window: frame #ff2196f3 light"
send "insets $insets"
expect "nav: B insets 60 30 60 30 0 30 0 30 0 0 0 30" "host: exclusion left - right 67-170"
send "size 200 100"
expect "nav: B insets 60 30 60 30 0 30 0 30 0 0 0 30" "host: exclusion left - right -" "nav: B size 600x300"
stop

# R starts S for a result inside the tap's dispatch. Android hands the result over after S's onPause,
# ATL at setResult: only that it comes once before R resumes is checked, not where
start ResultActivity
expect "nav: R onCreate" "nav: R onStart" "$a_frame" "nav: R insets $none" "nav: R onResume"
send "tap ask"
expect "nav: R onPause" "nav: R onStop" "nav: S onCreate" "nav: S onStart" "nav: S insets $none" "nav: S onResume"
send "tap answer"
results=0
for want in "nav: S onPause" "nav: S onStop" "nav: R onRestart" "nav: R onStart" "nav: R insets $none" "nav: R onResume"; do
  next
  if [[ $REPLY == "nav: R result 7 -1 yes" ]]; then
    results=$((results + 1))
    next
  fi
  [[ $REPLY == "$want" ]] || fail "got \"$REPLY\", want \"$want\""
done
((results == 1)) || fail "no \"nav: R result 7 -1 yes\" before R resumed"
expect "nav: S onDestroy"
stop
echo "PASS"
