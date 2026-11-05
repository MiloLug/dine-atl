#!/usr/bin/env bash
# Drives the windows APK (windows/, built by the superproject's tests/harness/apk.py) in the
# headless build: the shown activity's window brightness reaches the cli host while it shows, the
# screen's own (-1) while the task is in the back (Home), again on its return, and -1 once Back
# finished the activity.
# Usage: run-windows.sh <headless build dir> <windows.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data" timeout 60 \
    ./android-translation-layer -c "$apk" -l dev/dine/windows/MainActivity -X -Xint 2>&1
}
pid=$ATL_PID
: >"$work/log"

fail() { kill "$pid" 2>/dev/null || true; echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }

# the next "host: brightness" line into REPLY
next() {
  local line re='^(host: brightness .*)'
  while IFS= read -r -t 30 line <&"${ATL[0]}"; do
    printf '%s\n' "$line" >>"$work/log"
    if [[ $line =~ $re ]]; then
      REPLY=${BASH_REMATCH[1]}
      return 0
    fi
  done
  fail "ATL went quiet or exited"
}

expect() {
  next
  [[ $REPLY == "$1" ]] || fail "got \"$REPLY\", want \"$1\""
}
expect "host: brightness 0.50"
echo home >&"${ATL[1]}"
expect "host: brightness -1.00"
echo front >&"${ATL[1]}"
expect "host: brightness 0.50"
echo back >&"${ATL[1]}"
expect "host: brightness -1.00"
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
echo PASS
