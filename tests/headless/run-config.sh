#!/usr/bin/env bash
# Night changes in the headless build (the cli host's "night 0|1"), through the windows APK
# (windows/, built by the superproject's tests/harness/apk.py). "Night" is Android's dark mode:
# uiMode's night bits, 32 (UI_MODE_NIGHT_YES) dark and 16 (_NO) light.
# - ConfigActivity covers uiMode in its manifest: the Application's callbacks, the activity and its
#   view hear each change, and its tint resolves to the values-night color and back; the change
#   prunes the drawables it holds from both caches;
# - PlainConfigActivity doesn't: only the Application hears it, ATL logs that the activity keeps its
#   configuration.
# Usage: run-config.sh <headless build dir> <windows.apk>
set -euo pipefail

build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
pid=

fail() { [[ -n $pid ]] && kill "$pid" 2>/dev/null; echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }

start() {
  log="$work/$1.log"
  mkdir -p "$work/data-$1"
  : >"$log"
  coproc ATL {
    cd "$build"
    exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data-$1" timeout 60 \
      ./android-translation-layer -c "$apk" -l "dev/dine/windows/$1" -X -Xint 2>&1
  }
  pid=$ATL_PID
  exec {atl_out}<&"${ATL[0]}" {atl_in}>&"${ATL[1]}"
}

stop() {
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  exec {atl_out}<&- {atl_in}>&-
}

send() { echo "$1" >&"$atl_in"; }

# expect <line>...: the next "config:" or "keeps its configuration" lines are exactly them, in order
expect() {
  local line want
  for want; do
    while true; do
      IFS= read -r -t 30 line <&"$atl_out" || fail "ATL went quiet or exited before \"$want\""
      printf '%s\n' "$line" >>"$log"
      [[ $line == *Exception* ]] && fail "got \"$line\""
      [[ $line == *"config: "* || $line == *"keeps its configuration" ]] || continue
      [[ $line == *": $want" ]] || fail "got \"${line#*: }\", want \"$want\""
      break
    done
  done
}

night=32 day=16 # Configuration.UI_MODE_NIGHT_YES, _NO
start ConfigActivity
expect "activity created color #ff112233"
send "night 1"
expect "application night $night" "activity night $night color #ffaabbcc" "view night $night"
send "night 0"
expect "application night $day" "activity night $day color #ff112233" "view night $day"
stop

start PlainConfigActivity
expect "plain created"
send "night 1"
expect "application night $night" "dev.dine.windows.PlainConfigActivity doesn't handle uiMode; keeps its configuration"
stop

echo PASS
