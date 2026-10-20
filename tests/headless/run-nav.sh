#!/usr/bin/env bash
# Drives the nav APK (nav/, built by the superproject's tests/harness/apk.py) through the cli host's
# -c commands in the headless build: Back finishes B, Back on the task root A moves the task to the
# back, front and home move it again. Each command's lifecycle and host lines must come in order;
# a command that must do nothing is checked by the next command's lines coming first.
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

# the next "nav: " or "host: " line into REPLY, without the log prefix
next() {
  local line re='(nav: .*|^host: .*)'
  while IFS= read -r -t 30 line <&"${ATL[0]}"; do
    printf '%s\n' "$line" >>"$work/log"
    if [[ $line =~ $re ]]; then
      REPLY=${BASH_REMATCH[1]}
      return 0
    fi
  done
  fail "ATL went quiet or exited"
}

until_line() {
  while next; do
    [[ $REPLY == "$1" ]] && return 0
  done
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

until_line "nav: B onResume"
send back
expect "nav: B onPause" "nav: B onStop" "nav: A onRestart" "nav: A onStart" "nav: A onResume" "nav: B onDestroy"
send back # the task root: the task moves to the back
expect "nav: A onPause" "nav: A onStop" "host: task back app"
send back # nothing while in the back
send front
expect "nav: A onRestart" "nav: A onStart" "nav: A onResume" "host: task front host"
send front # nothing while in front
send home
expect "nav: A onPause" "nav: A onStop" "host: task back host"

kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo "PASS"
