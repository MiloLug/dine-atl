#!/usr/bin/env bash
# Runs demo_app.apk in the headless build (no display) and compares its view-tree dump with
# demo_app.viewtree, or demo_app.<DPI>.viewtree at another density. Exit 0 on a match.
#
# Usage: run-demo.sh <headless build dir> <demo_app.apk> [--density DPI] [--update]
# The build dir's own libtranslation_layer_main.so must load: RUN_FROM_BUILDDIR resolves
# java.library.path to ./, so this runs from the build dir.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
shift 2
density=() expected="$here/demo_app.viewtree" update=
while (($#)); do
  case $1 in
    --density) density=(-d "$2"); expected="$here/demo_app.$2.viewtree"; shift 2 ;;
    --update) update=1; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"

cd "$build"
coproc ATL {
  exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 ATL_DUMP_VIEWTREE=1 \
    ANDROID_APP_DATA_DIR="$work/data" timeout 120 ./android-translation-layer "${density[@]}" "$apk" \
    -l com/example/demo_application/MainActivity -X -Xint -X -verbose:jni 2>&1
}
pid=$ATL_PID
: >"$work/log"
while IFS= read -r line <&"${ATL[0]}"; do
  printf '%s\n' "$line" >>"$work/log"
  [[ $line == "viewtree: end" ]] && break
done
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true

fail() { echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }
grep -q "Added shared library \"$build/libtranslation_layer_main.so\"\|Added shared library \"./libtranslation_layer_main.so\"" "$work/log" \
  || fail "the headless libtranslation_layer_main.so didn't load"
grep -q -- "- onResume - yay!" "$work/log" || fail "no onResume"
grep '^viewtree:' "$work/log" >"$work/viewtree" || fail "no view-tree dump"

if [[ $update ]]; then
  cp "$work/viewtree" "$expected"
  echo "updated $expected"
elif ! diff -u "$expected" "$work/viewtree"; then
  fail "view tree differs from $expected"
fi
echo "PASS"
