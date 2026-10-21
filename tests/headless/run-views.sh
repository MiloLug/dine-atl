#!/usr/bin/env bash
# Runs the views APK (views/, built by the superproject's tests/harness/apk.py), which removes,
# re-adds and collects views, a dialog and drawables, then finishes. MALLOC_PERTURB_ fills freed
# memory, so a free that corrupts the heap likely crashes; only uikit walks the records after a
# free (iOS atl-views).
# --headless: no display, and the tree and the view, dialog and drawable frees are checked. Without
# it (gtk): the gsk drawables' frees, a nine-patch and a StateListDrawable among them.
# Usage: run-views.sh [--headless] <build dir> <views.apk>
set -euo pipefail

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

env=(RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" MALLOC_PERTURB_=85 ATL_DUMP_VIEWTREE=1)
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
rc=0
(cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/views/MainActivity \
  -X -Xint) >"$log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }
# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
grep -q "views done" "$log" || fail "no \"views done\" (exit status $rc)"
grep -q "native_1destructor\|native_1removeView\|nativeDestroy\|native_1unref" "$log" && fail "a native is missing"
grep -q "views ui mode type 1$" "$log" || fail "uiMode's type isn't UI_MODE_TYPE_NORMAL"
grep -q "views row attached true$" "$log" || fail "the row wasn't attached with its parent"
grep -q "views row detached$" "$log" || fail "the row wasn't detached on removal"
grep -q "views matrix 0,1 paint 2.0$" "$log" || fail "Matrix or Paint state wrong"
grep -q "views widgets$" "$log" || fail "the widgets step didn't finish"
if ((headless)); then
  grep '^viewtree:' "$log" | diff -u "$here/views.viewtree" - || fail "view tree differs from views.viewtree"
  grep -q '^viewfreed: android.widget.LinearLayout$' "$log" || fail "x and y's column not freed"
  grep -q '^dialogfreed:$' "$log" || fail "the dialog's window not freed"
  grep -q '^drawablefreed: record$' "$log" || fail "the drawable not freed"
  grep -q '^host: keep screen on 1$' "$log" && grep -q '^host: keep screen on 0$' "$log" || fail "keep screen on not sent and cleared"
  grep -q '^window: frame .* fullscreen$' "$log" || fail "FLAG_FULLSCREEN didn't reach the frame"
  for text in x y d c; do
    grep -q "^viewfreed: android.widget.TextView \"$text\"$" "$log" || fail "\"$text\" not freed"
  done
else
  grep -q "views gsk drawables$" "$log" || fail "the gsk drawables step didn't run"
  grep -q "views createFromPath missing: true$" "$log" || fail "createFromPath of a missing file isn't null"
  # the container that holds the nine-patch must go first: a ContainerPaintable line alone doesn't show it
  for kind in JavaPaintable NinePatchPaintable ContainerPaintable; do
    grep -q "^drawablefreed: $kind$" "$log" || fail "no $kind freed"
  done
fi
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
