#!/usr/bin/env bash
# Runs the views APK (views/, built by the superproject's tests/harness/apk.py), which removes,
# re-adds and collects views, a dialog and a drawable, then finishes. MALLOC_PERTURB_ fills freed
# memory, so a free that corrupts the heap likely crashes; only uikit walks the records after a
# free (iOS atl-views).
# --headless: no display, and the tree and the frees are checked too.
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

env=(RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" MALLOC_PERTURB_=85)
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}" ATL_DUMP_VIEWTREE=1)
rc=0
(cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/views/MainActivity \
  -X -Xint) >"$log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }
# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
grep -q "views done" "$log" || fail "no \"views done\" (exit status $rc)"
grep -q "native_1destructor\|native_1removeView\|nativeDestroy\|native_1unref" "$log" && fail "a native is missing"
grep -q "views ui mode type 1$" "$log" || fail "uiMode's type isn't UI_MODE_TYPE_NORMAL"
if ((headless)); then
  grep '^viewtree:' "$log" | diff -u "$here/views.viewtree" - || fail "view tree differs from views.viewtree"
  grep -q '^viewfreed: android.widget.LinearLayout$' "$log" || fail "x and y's column not freed"
  grep -q '^dialogfreed:$' "$log" || fail "the dialog's window not freed"
  grep -q '^drawablefreed:$' "$log" || fail "the drawable not freed"
  for text in x y d c; do
    grep -q "^viewfreed: android.widget.TextView \"$text\"$" "$log" || fail "\"$text\" not freed"
  done
fi
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
