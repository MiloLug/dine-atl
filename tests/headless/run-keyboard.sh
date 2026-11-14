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
# - PanActivity and ResizeActivity again at 480 dpi, where a dp of the insets is 3 px.
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

# the field's bottom, 417, comes up to the keyboard's top, 340: 77 of the keyboard's 200
start PanScrollActivity
want "panscroll ime 0 nav 0 size 960x540 field 400"
send "insets $keyboard"
want "panscroll ime 200 nav 30 size 960x540 field 323"
stop

start ResizePlainActivity
want "resizeplain ime 0 nav 0 size 960x540 field 523"
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
send "insets $keyboard"
want "resize ime 0 nav 0 size 960x360 field 309"
stop

echo PASS
