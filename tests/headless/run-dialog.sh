#!/usr/bin/env bash
# Runs the dialog APK (dialog/, built by the superproject's tests/harness/apk.py) with scripted
# answers: "Yes" on the alert and "Green" on the list it opens, then a cancel of the alert; then
# KeepActivity's alert, whose onShow disables "Yes" and hides "Later": "No" keeps it open and brings
# "Yes" back as "Sure", which closes it, while "Yes" or "Later" first has no button to answer; then
# LateActivity's, where the same changes come while the alert is drawn: "Later" answers before
# them, "Yes" and "Later" don't after; ModalActivity's alert, which a cancel doesn't close.
# Each run's last dialog finishes the app, so ATL exits by itself.
# Each run's app lines must be exactly the expected ones, in order.
# --headless: no display, and the headless backend's "dialog:" lines are checked among them, then
# ContentActivity's Dialog with a content view through the cli host's -c: the view-tree dump with
# the dialog's window over the activity (dialog-content.viewtree; --update rewrites it), a tap on
# its button, then a second dialog canceled by a touch outside it; then StackActivity's
# non-touch-modal dialog: a drag in it scrolls it, a tap outside it reaches the activity, it hides
# while another activity covers its own, a hide during a gesture ends it with a CANCEL and nothing
# else, and it goes (WindowLeaked) when its activity finishes, also from over another activity;
# then AlertDialog panels: ViewActivity's setView content (dialog-view.viewtree), its field's text
# and a touch outside the panel, ChoiceActivity's adapter list, and ModalActivity's dialogs under
# Back and a touch outside, through -c. In a -c session the app's lines must come in the order
# waited for.
# ChoiceActivity's choice rows, ProgressActivity's progress and ViewActivity's OK run on both.
# Usage: run-dialog.sh [--headless [--update]] <build dir> <dialog.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
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

# run <name> <answers> [<activity>]: ATL's output in $work/<name>.log, the exit status in rc; no
# answers: no ATL_DIALOG_ANSWERS
run() {
  log="$work/$1.log"
  mkdir -p "$work/data-$1"
  local env=("${atl_env[@]}" DINE_PREFIX="$work/data-$1")
  [[ -n $2 ]] && env+=(ATL_DIALOG_ANSWERS="$2")
  ((headless)) && env=(-u DISPLAY -u WAYLAND_DISPLAY "${env[@]}")
  rc=0
  (cd "$build" && env "${env[@]}" timeout 60 ./android-translation-layer "$apk" -l "dev/dine/dialog/${3:-MainActivity}" \
    -X -Xint) >"$log" 2>&1 || rc=$?
}

# said <line>...: the run's app lines (log tag "dialog") are exactly these, in order; on headless
# the backend's own "dialog: " lines are among them, on gtk those arguments are skipped
said() {
  local want=() line lines='s/^[A-Z]\/dialog *\( *[0-9]+\): (.*)/\1/p'
  for line; do
    ((headless)) || [[ $line != "dialog: "* ]] || continue
    want+=("$line")
  done
  ((headless)) && lines+='; /^dialog: /p'
  diff -u <(((${#want[@]} == 0)) || printf '%s\n' "${want[@]}") <(sed -nE "$lines" "$log") \
    || fail "the dialog lines aren't the expected ones (exit status $rc)"
}

exited() {
  faults "$log"
  [[ $rc == "$1" ]] || fail "exit status $rc, want $1"
}

# The APK is unsigned: PackageParser's "no certificates" error is expected, ATL ignores it.
run answers 'Yes;Green'
said 'dialog: show title="Dialog test" message="Do you like dialogs?" positive="Yes" negative="No" cancelable' \
  "dialog onClick -1" \
  'dialog: show title="Pick a color" items="Red","Green","Blue" cancelable' \
  "dialog: close" \
  "dialog onClick 1" \
  "dialog: close" \
  "dialog done"
exited 0

run cancel '!cancel'
said 'dialog: show title="Dialog test" message="Do you like dialogs?" positive="Yes" negative="No" cancelable' \
  "dialog canceled" \
  "dialog: close"
exited 0

# shown from onCreate, the alert shows once its activity does: with onShow's changes
keep_shown='dialog: show title="Keep test" message="Keep it open?" positive="Yes" (disabled) negative="No" cancelable'
run keep 'No;Sure' KeepActivity
said "$keep_shown" \
  "keep no kept" \
  'dialog: button positive="Yes"' \
  'dialog: button positive="Sure"' \
  "keep onClick -1" \
  "dialog: close" \
  "keep done"
exited 0

# a disabled or hidden button takes no answer
for answer in Yes Later; do
  run "keep-$answer" "$answer" KeepActivity
  unanswered="dialog: no scripted answer for \"Keep test\" (next answer: $answer)"
  said "$keep_shown" "$unanswered"
  grep -qxF "$unanswered" "$log" || fail "\"$answer\" was answered" # on gtk too
  exited 1
done

# the same buttons, changed while the alert is drawn: all three answer until "No" changes them
late_shown='dialog: show title="Late test" message="Change it later?" positive="Yes" negative="No" neutral="Later" cancelable'
run late 'Later' LateActivity
said "$late_shown" \
  "late onClick -3" \
  "dialog: close" \
  "late done"
exited 0
for answer in Yes Later; do
  run "late-$answer" "No;$answer" LateActivity
  unanswered="dialog: no scripted answer for \"Late test\" (next answer: $answer)"
  said "$late_shown" \
    "late no kept" \
    'dialog: button positive="Yes" disabled' \
    'dialog: button neutral hidden' \
    "$unanswered"
  grep -qxF "$unanswered" "$log" || fail "\"$answer\" was answered after \"No\"" # on gtk too
  exited 1
done

# a cancel doesn't close an alert that isn't cancelable; its button does
run modal '!cancel;Done' ModalActivity
said 'dialog: show title="Modal test" positive="Done" negative="More"' \
  "modal done" \
  "dialog: close"
exited 0

# AlertDialog's own choice rows: Blue opens a plain alert over the single choice list; the list's
# checked rows follow the answers ("choice multi ok 0": Salt checked, Pepper unchecked)
run choice 'Blue;Okay;OK;Salt;Pepper;OK;Skip' ChoiceActivity
said "choice single shown 0" \
  'dialog: show panel title="Pick one" items="Red","Green","Blue" check=single checked=0 positive="OK" cancelable' \
  "dialog: chrome width=280 left=16 top=168 right=16 bottom=44" \
  "dialog: row 2 checked" \
  "choice single 2" \
  'dialog: show title="Blue is taken" positive="Okay" cancelable' \
  "dialog: close" \
  "choice single ok 2" \
  'dialog: show panel title="Pick some" items="Salt","Pepper","Oil" check=multi checked=1 positive="OK" cancelable' \
  "dialog: close" \
  "dialog: chrome width=280 left=16 top=168 right=16 bottom=44" \
  "dialog: row 0 checked" \
  "choice multi 0 true" \
  "dialog: row 1 unchecked" \
  "choice multi 1 false" \
  "choice multi ok 0" \
  'dialog: show panel title="Pick a fruit" negative="Skip" cancelable' \
  "choice adapter shown" \
  "dialog: close" \
  "dialog: chrome width=280 left=16 top=36 right=16 bottom=44" \
  "dialog: close" \
  "choice done"
exited 0

run progress '' ProgressActivity
said "progress spinner shown true" \
  'dialog: show panel title="Wait" message="Spinning" progress=spinner indeterminate cancelable' \
  "dialog: chrome width=280 left=16 top=80 right=16 bottom=16" \
  "dialog: close" \
  'dialog: show panel title="Loading" message="Please wait" progress=bar 0/10 cancelable' \
  "progress bar shown" \
  "dialog: chrome width=280 left=16 top=80 right=16 bottom=16" \
  "dialog: progress 5/10" \
  'dialog: show panel title="Loading" message="Almost" progress=bar 5/10 cancelable' \
  "progress 5/10" \
  "dialog: close" \
  "progress done"
exited 0

run view 'OK' ViewActivity
said "view shown" \
  'dialog: show panel title="View test" positive="OK" negative="Cancel" cancelable' \
  "dialog: chrome width=280 left=16 top=36 right=16 bottom=44" \
  'view ok ""' \
  "dialog: close" \
  "view dismissed"
exited 0

if ((headless)); then
  # start <name> <activity>: ATL under -c, its output in $work/<name>.log
  start() {
    log="$work/$1.log"
    mkdir -p "$work/data-$1"
    : >"$log"
    coproc ATL {
      cd "$build"
      exec env "${headless_env[@]}" DINE_PREFIX="$work/data-$1" timeout 60 \
        ./android-translation-layer -c "$apk" -l "dev/dine/dialog/$2" -X -Xint 2>&1
    }
    session
  }

  # the app's lines (log tags "dialog" and "stack") and the task's moves
  app_lines='^[A-Z]/(dialog|stack) *\(|^host: task '

  # wait_for <regex>: reads ATL's lines into the log up to one that matches; an app line or a task
  # move that doesn't match fails, so those come in the order they are waited for
  wait_for() {
    local line
    while IFS= read -r -t 30 line <&"$atl_out"; do
      printf '%s\n' "$line" >>"$log"
      [[ $line =~ $1 ]] && return 0
      [[ $line =~ $app_lines ]] && fail "got \"$line\", want a line matching \"$1\""
    done
    fail "no line matching \"$1\""
  }

  # golden <file>: the dump's view tree against it
  golden() {
    send dump
    read_dump "$app_lines"
    printf '%s\n' "${dump[@]}" >"$work/$1"
    if ((update)); then
      cp "$work/$1" "$here/$1"
    else
      diff -u "$here/$1" "$work/$1" || fail "view tree differs from $1"
    fi
  }

  # the dump's "window dialog" line into REPLY, or none
  dialog_line() {
    local line
    send dump
    read_dump "$app_lines"
    REPLY=
    for line in "${dump[@]}"; do
      [[ $line != "viewtree: window dialog "* ]] || REPLY=$line
    done
  }

  touch_at() {
    send "touch down 0 $1 $2"
    send "touch up 0 $1 $2"
  }

  start content ContentActivity
  wait_for 'content shown$'
  golden dialog-content.viewtree
  send "tap Close"
  for line in '^tap: Close ' 'content click$' 'content dismissed$' 'content shown$'; do
    wait_for "$line"
  done
  touch_at 5 5
  for line in 'content canceled$' 'content dismissed$'; do
    wait_for "$line"
  done
  exit_session

  # StackActivity's non-touch-modal dialog: a drag in it scrolls its list, a tap outside reaches the
  # activity; the activity over it hides it until Back; its finishing activity takes it along
  start stack StackActivity
  wait_for 'stack shown$'
  wait_for 'stack resumed$'
  dialog_line
  [[ $REPLY =~ ^viewtree:\ window\ dialog\ ([0-9]+),([0-9]+)\  ]] || fail "no dialog over StackActivity"
  dx=$((BASH_REMATCH[1] + 20)) dy=${BASH_REMATCH[2]}
  send "touch down 0 $dx $((dy + 100))"
  for step in 80 60 40 20; do
    send "touch move 0 $dx $((dy + step))"
  done
  send "touch up 0 $dx $((dy + 20))"
  wait_for 'stack scrolled$'
  send "tap under"
  wait_for 'stack under click$'
  send "tap next"
  wait_for 'stack cover resumed$'
  dialog_line
  [[ -z $REPLY ]] || fail "the dialog shows over CoverActivity: $REPLY"
  send back
  wait_for 'stack resumed$'
  dialog_line
  [[ -n $REPLY ]] || fail "the dialog didn't come back with StackActivity"
  # the tap's UP comes after the hide: neither the hider nor the activity's root may log it
  send "tap hider"
  for line in 'stack hider down$' 'stack hider cancel$' 'stack shown again$'; do
    wait_for "$line"
  done
  send "tap finish"
  wait_for 'has leaked window'
  exit_session

  # the same finish from over CoverActivity, which stays: the leaked dialog isn't left over it
  start leak CoverActivity
  wait_for 'stack cover resumed$'
  send "tap cover"
  wait_for 'stack shown$'
  wait_for 'stack resumed$'
  dialog_line
  [[ -n $REPLY ]] || fail "no dialog over StackActivity"
  send "tap finish"
  wait_for 'stack cover resumed$'
  wait_for 'has leaked window'
  dialog_line
  [[ -z $REPLY ]] || fail "the leaked dialog stays over CoverActivity: $REPLY"
  end_session

  # ViewActivity's setView content in the alert's panel: the dump's panel around the root
  # (dialog-view.viewtree), a tap on the chrome is no one's (no cancel), a tap on the content's
  # button is the view's, a touch on its field brings the keyboard and the text goes to it, the
  # chrome's OK answers
  start view-c ViewActivity
  wait_for 'view shown$'
  golden dialog-view.viewtree
  [[ $(grep '^viewtree: window dialog' "$work/dialog-view.viewtree") =~ dialog\ ([0-9]+),([0-9]+)\ .*\ panel\ ([0-9]+),([0-9]+)\  ]] \
    || fail "no panel around the view"
  field_x=$((BASH_REMATCH[1] + 8)) field_y=$((BASH_REMATCH[2] + 8)) # the field is the content's first row
  touch_at $((BASH_REMATCH[3] + 4)) $((BASH_REMATCH[4] + 4))
  send "tap Inner"
  wait_for 'view inner click$'
  touch_at $field_x $field_y
  wait_for '^keyboard: on android.widget.EditText "name" kind=text return=default autocap=none secure=0 hidden=0 multiline=1 autocorrect=1 tap_focus=1 tap_keyboard=1 editable=1$'
  send "text commit hey"
  wait_for '^edit: "hey" 3,3$'
  send "answer OK"
  for line in '^answer: OK$' 'view ok "hey"$' 'view dismissed$'; do
    wait_for "$line"
  done
  exit_session

  # a touch outside the panel cancels the alert: no "view ok"
  start view-outside ViewActivity
  wait_for 'view shown$'
  wait_for '^dialog: chrome '
  touch_at 5 5
  wait_for 'view dismissed$'
  exit_session

  # ChoiceActivity's adapter list: rows Android draws inside the panel take taps
  start choice-c ChoiceActivity
  wait_for 'choice single shown 0$'
  wait_for '^dialog: show panel title="Pick one"'
  # a tap finds no view under the plain alert, which has none
  send "answer Blue"
  wait_for 'choice single 2$'
  wait_for '^dialog: show title="Blue is taken"'
  send "tap nothing"
  wait_for '^tap: no nothing$'
  send "answer Okay"
  wait_for '^answer: Okay$'
  send "answer OK"
  wait_for 'choice single ok 2$'
  send "answer OK"
  wait_for 'choice multi ok 1$'
  wait_for 'choice adapter shown$'
  send "tap Pear"
  for line in '^tap: Pear ' 'choice adapter 1$' 'choice done$'; do
    wait_for "$line"
  done
  exit_session

  # WatchActivity: the window's first touch lands in the lower dialog, outside the upper one, whose
  # ACTION_OUTSIDE dismisses the lower dialog before the DOWN reaches it: the dialog gets no event, a
  # CANCEL included, as it has seen nothing to cancel
  start watch WatchActivity
  wait_for 'watch shown$'
  send dump
  read_dump "$app_lines"
  touch_at 260 270
  for line in 'watch outside$' 'watch lower dismissed$'; do
    wait_for "$line"
  done
  send dump
  read_dump "$app_lines"
  end_session

  # ModalActivity: Back and a touch outside leave a dialog that isn't cancelable, and the task in
  # front; a "modal canceled" or a task move would come before the lines waited for
  start modal-c ModalActivity
  wait_for '^dialog: show title="Modal test" positive="Done" negative="More"$'
  send back
  send "answer More"
  wait_for 'modal content shown$'
  touch_at 5 5
  send back
  send "tap Close"
  for line in 'modal content click$' 'modal content dismissed$'; do
    wait_for "$line"
  done
  exit_session
fi
echo "PASS"
