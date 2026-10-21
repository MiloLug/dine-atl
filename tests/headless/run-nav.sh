#!/usr/bin/env bash
# Drives the nav APK (nav/, built by the superproject's tests/harness/apk.py) through the cli host's
# -c commands in the headless build: Back finishes B, Back on the task root A moves the task to the
# back, front and home move it again. A's list, shown while the task is in the back, appears only on
# front and closes and comes back with the task; Back cancels it. The window's frame follows the shown activity: A's
# theme color; none for B, which clears the theme's flag, until B draws its own. Each show logs the
# insets the content gets; B's exclusion rect reaches the host once the insets give the right edge a
# gesture strip, and A, which has none, clears it. Each command's
# lifecycle, dialog, host and window lines must come in order; a command that must do nothing is
# checked by the next command's lines coming first.
# Usage: run-nav.sh <headless build dir> <nav.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/nav/MainActivity -X -Xint 2>&1
}
pid=$ATL_PID
: >"$work/log"

fail() { kill "$pid" 2>/dev/null || true; echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }

# the next "nav: ", "host: ", "dialog: " or "window: " line into REPLY, without the log prefix
next() {
  local line re='(nav: .*|^host: .*|^dialog: .*|^window: .*)'
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

a_frame='window: frame #ff1a1b20' # Theme.DeviceDefault's colorPrimaryDark
none='0 0 0 0 0 0 0 0 0 0 0 0'
expect "nav: A onCreate" "nav: A onStart" "$a_frame" "nav: A insets $none" "nav: A onResume" "nav: A onPause" "nav: A onStop" \
  "nav: B onCreate" "nav: B onStart" "window: frame none" "nav: B insets $none" "nav: B onResume" "window: frame #ff2196f3 light"
# dp = px at the default density: 20 dp side strips, 10 dp top and bottom, mandatory top and bottom,
# a 10 dp navigation bar. B's rects merge to 200-520, cut to 530 by the bottom strip and to the
# bottom-most 200 dp.
insets='20 10 20 10 0 10 0 10 0 0 0 10'
send "insets $insets"
expect "nav: B insets $insets" "host: exclusion left - right 320-520"
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
send front
expect "$list" "nav: A onRestart" "nav: A onStart" "nav: A insets $insets" "nav: A onResume" "host: task front host"
send home
expect "dialog: close" "nav: A onPause" "nav: A onStop" "host: task back host"
send front
expect "$list" "nav: A onRestart" "nav: A onStart" "nav: A insets $insets" "nav: A onResume" "host: task front host"
send back # the shown list takes it: cancel, then the close
expect "nav: list canceled" "dialog: close"

kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo "PASS"
