#!/usr/bin/env bash
# Runs the dialog APK (dialog/, built by the superproject's tests/harness/apk.py) with scripted
# answers: "Yes" on the alert, "Green" on the list it opens; the list's dismissal finishes the app,
# so ATL exits by itself. Exit 0 when both clicks and the dismissal logged and ATL exited 0.
# --headless: no display, and the headless backend's "dialog:" lines are checked too.
# Usage: run-dialog.sh [--headless] <build dir> <dialog.apk>
set -euo pipefail

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

env=(RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data" ATL_DIALOG_ANSWERS='Yes;Green')
((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")

cd "$build"
rc=0
env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l dev/dine/dialog/MainActivity -X -Xint \
  >"$work/log" 2>&1 || rc=$?

fail() { echo "FAIL: $*"; echo "log: $work/log"; trap - EXIT; exit 1; }
# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
for line in "dialog onClick -1" "dialog onClick 1" "dialog done"; do
  grep -q "$line" "$work/log" || fail "no \"$line\" (exit status $rc)"
done
if ((headless)); then
  grep -q '^dialog: show title="Dialog test" message="Do you like dialogs?" positive="Yes" negative="No" cancelable$' "$work/log" \
    || fail "no alert line"
  grep -q '^dialog: show title="Pick a color" items="Red","Green","Blue" cancelable$' "$work/log" || fail "no list line"
  [[ $(grep -c '^dialog: close$' "$work/log") == 2 ]] || fail "not two \"dialog: close\" lines"
fi
[[ $rc == 0 ]] || fail "exit status $rc"
echo "PASS"
