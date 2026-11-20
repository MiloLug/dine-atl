#!/usr/bin/env bash
# Runs the views APK (views/, built by the superproject's tests/harness/apk.py), which removes,
# re-adds and collects views, a dialog and drawables, then finishes. MALLOC_PERTURB_ fills freed
# memory, so a free that corrupts the heap likely crashes; only uikit walks the records after a
# free (iOS atl-views).
# --headless: no display, and the tree and the view, dialog and drawable frees are checked; then
# the activities of the cli host's -c runs, each under MALLOC_PERTURB_ and dumped: the tree after a
# laid-out view's removal (RemovedActivity, views-removed.viewtree), after a translation and nothing
# else (TranslateActivity, views-translated.viewtree), after a dialog dismissed from another's
# measure (RootsActivity, views-roots.viewtree), with a view in each state the dump words
# (StateActivity, views-state.viewtree). Without it (gtk): the dialog's free and the gsk
# drawables', a nine-patch and a StateListDrawable among them.
# Usage: run-views.sh [--headless] <build dir> <views.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
headless=0
if [[ ${1-} == --headless ]]; then
  headless=1
  shift
fi
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"

env=("${atl_env[@]}" DINE_PREFIX="$work/data" MALLOC_PERTURB_=85 ATL_DUMP_VIEWTREE=1)
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
rc=0
(cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/views/MainActivity \
  -X -Xint) >"$log" 2>&1 || rc=$?

# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
grep -q "views done" "$log" || fail "no \"views done\" (exit status $rc)"
faults "$log" # the finalizer daemon logs a missing native of a finalize() and goes on
grep -q "views ui mode type 1$" "$log" || fail "uiMode's type isn't UI_MODE_TYPE_NORMAL"
grep -q "views row attached true$" "$log" || fail "the row wasn't attached with its parent"
grep -q "views row detached$" "$log" || fail "the row wasn't detached on removal"
grep -q "views matrix 0,1 paint 2.0$" "$log" || fail "Matrix or Paint state wrong"
grep -q "views widgets$" "$log" || fail "the widgets step didn't finish"
grep -q "views drawables$" "$log" || fail "the drawables step didn't run"
grep -q "views createFromPath missing: true$" "$log" || fail "createFromPath of a missing file isn't null"
grep -q '^dialogfreed:$' "$log" || fail "the dialog's window not freed"
if ((headless)); then
  grep '^viewtree:' "$log" | diff -u "$here/views.viewtree" - || fail "view tree differs from views.viewtree"
  grep -q '^viewfreed: android.widget.LinearLayout$' "$log" || fail "x and y's column not freed"
  # the anonymous Drawable, the StateListDrawable and its nine-patch
  (($(grep -c '^drawablefreed: record$' "$log") >= 3)) || fail "not three drawables freed"
  # finish() clears it too: the row's removal must have, before "views widgets"
  [[ $(grep -x -e 'host: keep screen on [01]' -e '.*: views widgets' "$log" | sed 's/.*: //' | tr '\n' ' ') \
    == "keep screen on 1 keep screen on 0 views widgets " ]] || fail "keep screen on not sent, then cleared by the row's removal"
  grep -q '^window: frame .* fullscreen$' "$log" || fail "FLAG_FULLSCREEN didn't reach the frame"
  for text in x y d c; do
    grep -q "^viewfreed: android.widget.TextView \"$text\"$" "$log" || fail "\"$text\" not freed"
  done
else
  # the container that holds the nine-patch must go first: a ContainerPaintable line alone doesn't show it
  for kind in JavaPaintable NinePatchPaintable ContainerPaintable; do
    grep -q "^drawablefreed: $kind$" "$log" || fail "no $kind freed"
  done
fi
[[ $rc == 0 ]] || fail "exit status $rc"

if ((headless)); then
  # activity|the line it logs once its step is done|the tree the step leaves
  while IFS='|' read -r activity marker golden; do
    log="$work/$activity.log"
    : >"$log"
    coproc ATL {
      cd "$build"
      exec env "${headless_env[@]}" DINE_PREFIX="$work/data" MALLOC_PERTURB_=85 timeout 60 \
        ./android-translation-layer -c "$apk" -l "dev/dine/views/$activity" -X -Xint 2>&1
    }
    session
    while :; do
      IFS= read -r -t 30 line <&"$atl_out" || fail "no \"$marker\" from $activity"
      printf '%s\n' "$line" >>"$log"
      [[ $line == *": $marker" ]] && break
    done
    send dump
    read_dump
    printf '%s\n' "${dump[@]}" | diff -u "$here/$golden" - || fail "view tree differs from $golden"
    end_session
  done <<'LIST'
RemovedActivity|views removed|views-removed.viewtree
TranslateActivity|views translated|views-translated.viewtree
RootsActivity|views roots dismissed true|views-roots.viewtree
StateActivity|views state|views-state.viewtree
LIST
fi
echo "PASS"
