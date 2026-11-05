#!/usr/bin/env bash
# Runs the dialog APK (dialog/, built by the superproject's tests/harness/apk.py) with scripted
# answers: "Yes" on the alert and "Green" on the list it opens, then a cancel of the alert; then
# KeepActivity's alert, whose onShow disables "Yes" and hides "Later": "No" keeps it open and brings
# "Yes" back as "Sure", which closes it, while "Yes" or "Later" first has no button to answer.
# Each run's last dialog finishes the app, so ATL exits by itself.
# --headless: no display, and the headless backend's "dialog:" lines are checked too, then
# ContentActivity's Dialog with a content view through the cli host's -c: the view-tree dump with
# the dialog's window over the activity (dialog-content.viewtree; --update rewrites it), a tap on
# its button, then a second dialog canceled by a touch outside it; then StackActivity's
# non-touch-modal dialog: a drag in it scrolls it, a tap outside it reaches the activity, it hides
# while another activity covers its own, a hide during a gesture ends it with a CANCEL, and it goes
# (WindowLeaked) when its activity finishes.
# Usage: run-dialog.sh [--headless [--update]] <build dir> <dialog.apk>
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
headless=0 update=0
if [[ ${1-} == --headless ]]; then
  headless=1
  shift
  if [[ ${1-} == --update ]]; then
    update=1
    shift
  fi
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
  local env=(RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data-$1" ATL_DIALOG_ANSWERS="$2")
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
  # shown from onCreate, it shows once its activity does: with onShow's changes
  grep -q '^dialog: show title="Keep test" message="Keep it open?" positive="Yes" (disabled) negative="No" cancelable$' "$log" \
    || fail "no keep alert line"
  buttons='dialog: button positive="Yes"
dialog: button positive="Sure"'
  [[ $(grep '^dialog: button ' "$log") == "$buttons" ]] || fail "the button lines aren't the \"No\" changes"
  [[ $(grep -c '^dialog: close$' "$log") == 1 ]] || fail "not one \"dialog: close\" line"
fi
[[ $rc == 0 ]] || fail "exit status $rc"

# a disabled or hidden button takes no answer
for answer in Yes Later; do
  run "keep-$answer" "$answer" KeepActivity
  [[ $rc == 1 ]] || fail "\"$answer\" on the keep alert: exit status $rc, want 1"
  grep -qF "dialog: no scripted answer for \"Keep test\" (next answer: $answer)" "$log" || fail "\"$answer\" was answered"
done

if ((headless)); then
  log="$work/content.log"
  mkdir -p "$work/data-content"
  coproc ATL {
    cd "$build"
    exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data-content" timeout 60 \
      ./android-translation-layer -c "$apk" -l dev/dine/dialog/ContentActivity -X -Xint 2>&1
  }
  pid=$ATL_PID
  : >"$log"
  # reads ATL's lines into the log until one matches
  wait_for() {
    local line
    while IFS= read -r -t 30 line <&"${ATL[0]}"; do
      printf '%s\n' "$line" >>"$log"
      [[ $line =~ $1 ]] && return 0
    done
    kill "$pid" 2>/dev/null || true
    fail "no line matching \"$1\""
  }
  wait_for 'content shown'
  echo dump >&"${ATL[1]}"
  : >"$work/content.viewtree"
  while IFS= read -r -t 30 line <&"${ATL[0]}"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == viewtree:* ]] && printf '%s\n' "$line" >>"$work/content.viewtree"
    [[ $line == "viewtree: end" ]] && break
  done
  if ((update)); then
    cp "$work/content.viewtree" "$here/dialog-content.viewtree"
  else
    diff -u "$here/dialog-content.viewtree" "$work/content.viewtree" || fail "view tree differs from dialog-content.viewtree"
  fi
  echo "tap Close" >&"${ATL[1]}"
  for line in 'tap: Close' 'content click' 'content dismissed' 'content shown'; do
    wait_for "$line"
  done
  printf 'touch down 0 5 5\ntouch up 0 5 5\n' >&"${ATL[1]}"
  for line in 'content canceled' 'content dismissed'; do
    wait_for "$line"
  done
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true

  # StackActivity's non-touch-modal dialog: a drag in it scrolls its list, a tap outside reaches the
  # activity; the activity over it hides it until Back; its finishing activity takes it along
  log="$work/stack.log"
  mkdir -p "$work/data-stack"
  coproc ATL {
    cd "$build"
    exec env -u DISPLAY -u WAYLAND_DISPLAY RUN_FROM_BUILDDIR=1 DINE_PREFIX="$work/data-stack" timeout 60 \
      ./android-translation-layer -c "$apk" -l dev/dine/dialog/StackActivity -X -Xint 2>&1
  }
  pid=$ATL_PID
  : >"$log"
  # the dump's "window dialog" line, or none
  dialog_line() {
    echo dump >&"${ATL[1]}"
    REPLY=
    while IFS= read -r -t 30 line <&"${ATL[0]}"; do
      printf '%s\n' "$line" >>"$log"
      [[ $line == "viewtree: window dialog "* ]] && REPLY=$line
      [[ $line == "viewtree: end" ]] && return 0
    done
    fail "no view-tree dump"
  }
  wait_for 'stack resumed'
  dialog_line
  [[ $REPLY =~ ^viewtree:\ window\ dialog\ ([0-9]+),([0-9]+)\  ]] || fail "no dialog over StackActivity"
  dx=$((BASH_REMATCH[1] + 20)) dy=${BASH_REMATCH[2]}
  printf 'touch down 0 %d %d\n' "$dx" $((dy + 100)) >&"${ATL[1]}"
  for step in 80 60 40 20; do
    printf 'touch move 0 %d %d\n' "$dx" $((dy + step)) >&"${ATL[1]}"
  done
  printf 'touch up 0 %d %d\n' "$dx" $((dy + 20)) >&"${ATL[1]}"
  wait_for 'stack scrolled'
  echo "tap under" >&"${ATL[1]}"
  wait_for 'stack under click'
  echo "tap next" >&"${ATL[1]}"
  wait_for 'stack cover resumed'
  dialog_line
  [[ -z $REPLY ]] || fail "the dialog shows over CoverActivity: $REPLY"
  echo back >&"${ATL[1]}"
  wait_for 'stack resumed'
  dialog_line
  [[ -n $REPLY ]] || fail "the dialog didn't come back with StackActivity"
  echo "tap hider" >&"${ATL[1]}"
  for line in 'stack hider down' 'stack hider cancel' 'stack shown again'; do
    wait_for "$line"
  done
  echo "tap finish" >&"${ATL[1]}"
  wait_for 'has leaked window'
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
fi
echo "PASS"
