#!/usr/bin/env bash
# Night changes in the headless build (the cli host's "night 0|1"), through the windows APK
# (windows/, built by the superproject's tests/harness/apk.py). "Night" is Android's dark mode:
# uiMode's night bits, 32 (UI_MODE_NIGHT_YES) dark and 16 (_NO) light.
# - ConfigActivity covers uiMode in its manifest: the Application's callbacks, the activity and its
#   view hear each change, then the views of its two dialogs, one hidden (an added window still);
#   its tint resolves to the values-night color and back; the change prunes the drawables it
#   holds from both caches, so they load again in the new color;
# - PlainConfigActivity doesn't: only the Application hears each change, ATL logs that the activity
#   keeps its configuration, and neither its own onConfigurationChanged nor its dialog's view logs.
# The screen's size in dp is the window's at each density.
# Usage: run-config.sh <headless build dir> <windows.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# start <activity> [<dpi>]
start() {
  local run="$1${2:+-$2}"
  log="$work/$run.log"
  mkdir -p "$work/data-$run"
  : >"$log"
  coproc ATL {
    cd "$build"
    exec env "${headless_env[@]}" DINE_PREFIX="$work/data-$run" timeout 60 \
      ./android-translation-layer -c ${2:+-d "$2"} "$apk" -l "dev/dine/windows/$1" -X -Xint 2>&1
  }
  session
}

# a line of the kind expect checks, late, fails here: ATL answers a dump after the last change
stop() {
  send dump
  read_dump 'config: |keeps its configuration$'
  end_session
}

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

# screen <want>: ConfigActivity's line about its screen
screen() {
  local line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == *": screen: "* ]] || continue
    [[ $line == *": screen: $1" ]] || fail "got \"${line#*: }\", want \"screen: $1\""
    return 0
  done
  fail "ATL went quiet or exited before \"screen: $1\""
}

night=32 day=16 # Configuration.UI_MODE_NIGHT_YES, _NO
start ConfigActivity 480
screen "320x180 dp, 48 dp 144 px"
expect "activity created color #ff112233"
stop

start ConfigActivity
screen "960x540 dp, 48 dp 48 px"
expect "activity created color #ff112233"
send "night 1"
expect "application night $night" "activity night $night color #ffaabbcc" "drawables box #ffaabbcc tint #ffaabbcc" "view night $night" "dialog night $night" "hidden night $night"
send "night 0"
expect "application night $day" "activity night $day color #ff112233" "drawables box #ff112233 tint #ff112233" "view night $day" "dialog night $day" "hidden night $day"
stop

start PlainConfigActivity
expect "plain created"
keeps="dev.dine.windows.PlainConfigActivity doesn't handle uiMode; keeps its configuration"
send "night 1"
expect "application night $night" "$keeps"
send "night 0" # the activity's own "config: plain night" for the first change would come before these
expect "application night $day" "$keeps"
stop

echo PASS
