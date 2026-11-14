#!/usr/bin/env bash
# Runs the widgets APK's ListsActivity (widgets/, built by the superproject's tests/harness/apk.py)
# in the headless build: a ListView scrolled by 600 px shows a later first row, and nothing fails.
# The activity stays up, so the timeout ends ATL.
# Usage: run-lists.sh <headless build dir> <widgets.apk>
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
build="$(cd "$1" && pwd)"
apk="$(realpath "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/data"
log="$work/log"

rc=0
(cd "$build" && env "${headless_env[@]}" DINE_PREFIX="$work/data" timeout 15 \
  ./android-translation-layer "$apk" -l dev/dine/widgets/ListsActivity -X -Xint) >"$log" 2>&1 || rc=$?

# headless text metrics are fixed, so the scroll has one right answer
grep -q ": lists first 11 of 12 shown$" "$log" || fail "no \"lists first 11 of 12 shown\" (exit status $rc)"
faults "$log"
[[ $rc == 124 ]] || fail "exit status $rc, not the timeout's 124"
echo "PASS"
