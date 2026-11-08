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
# - UnspecifiedActivity: no mode and a ScrollView, a scroll container: it resizes.
# Usage: run-keyboard.sh [--update] <headless build dir> <windows.apk>; --update rewrites the goldens
set -euo pipefail

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

# tree <golden>: the dump's view tree against it
tree() {
  local line
  send dump
  : >"$work/tree"
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == viewtree:* ]] && printf '%s\n' "$line" >>"$work/tree"
    [[ $line == "viewtree: end" ]] && break
  done
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
send "insets $keyboard"
want "resize ime 0 nav 0 size 960x340 field 323"
tree keyboard-resize.viewtree
send "insets $bar"
want "resize ime 0 nav 30 size 960x540 field 400"
stop

start UnspecifiedActivity
want "unspecified ime 0 nav 0 size 960x540 field 400"
send "insets $keyboard"
want "unspecified ime 0 nav 0 size 960x340 field 323"
stop

echo PASS
