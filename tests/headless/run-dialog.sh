#!/usr/bin/env bash
# Runs the dialog APK (dialog/, built by the superproject's tests/harness/apk.py) with scripted
# answers: "Yes" on the alert and "Green" on the list it opens, then a cancel of the alert; then
# KeepActivity's alert, whose onShow disables "Yes" and hides "Later": "No" keeps it open and brings
# "Yes" back as "Sure", which closes it, while "Yes" or "Later" first has no button to answer.
# Each run's last dialog finishes the app, so ATL exits by itself.
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

fail() { echo "FAIL: $*"; echo "log: $log"; trap - EXIT; exit 1; }

# run <name> <answers> [<activity>]: ATL's output in $work/<name>.log, the exit status in rc
run() {
  log="$work/$1.log"
  mkdir -p "$work/data-$1"
  local env=(RUN_FROM_BUILDDIR=1 ANDROID_APP_DATA_DIR="$work/data-$1" ATL_DIALOG_ANSWERS="$2")
  ((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
  rc=0
  (cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l "dev/dine/dialog/${3:-MainActivity}" \
    -X -Xint) >"$log" 2>&1 || rc=$?
}

# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
run answers 'Yes;Green'
for line in "dialog onClick -1" "dialog onClick 1" "dialog done"; do
  grep -q "$line" "$log" || fail "no \"$line\" (exit status $rc)"
done
if ((headless)); then
  grep -q '^dialog: show title="Dialog test" message="Do you like dialogs?" positive="Yes" negative="No" cancelable$' "$log" \
    || fail "no alert line"
  grep -q '^dialog: show title="Pick a color" items="Red","Green","Blue" cancelable$' "$log" || fail "no list line"
  [[ $(grep -c '^dialog: close$' "$log") == 2 ]] || fail "not two \"dialog: close\" lines"
fi
[[ $rc == 0 ]] || fail "exit status $rc"

run cancel '!cancel'
grep -q "dialog canceled" "$log" || fail "no \"dialog canceled\" (exit status $rc)"
grep -q "dialog onClick" "$log" && fail "a click on cancel"
if ((headless)); then
  [[ $(grep -c '^dialog: close$' "$log") == 1 ]] || fail "not one \"dialog: close\" line"
fi
[[ $rc == 0 ]] || fail "exit status $rc"

run keep 'No;Sure' KeepActivity
for line in "keep no kept" "keep onClick -1" "keep done"; do
  grep -q "$line" "$log" || fail "no \"$line\" (exit status $rc)"
done
grep -q "keep onClick -2" "$log" && fail "the alert's own \"No\" listener ran"
if ((headless)); then
  grep -q '^dialog: show title="Keep test" message="Keep it open?" positive="Yes" negative="No" neutral="Later" cancelable$' "$log" \
    || fail "no keep alert line"
  buttons='dialog: button positive="Yes" disabled
dialog: button neutral hidden
dialog: button positive="Yes"
dialog: button positive="Sure"'
  [[ $(grep '^dialog: button ' "$log") == "$buttons" ]] || fail "the button lines aren't the onShow and \"No\" changes"
  [[ $(grep -c '^dialog: close$' "$log") == 1 ]] || fail "not one \"dialog: close\" line"
fi
[[ $rc == 0 ]] || fail "exit status $rc"

# a disabled or hidden button takes no answer
for answer in Yes Later; do
  run "keep-$answer" "$answer" KeepActivity
  [[ $rc == 1 ]] || fail "\"$answer\" on the keep alert: exit status $rc, want 1"
  grep -qF "dialog: no scripted answer for \"Keep test\" (next answer: $answer)" "$log" || fail "\"$answer\" was answered"
done
echo "PASS"
