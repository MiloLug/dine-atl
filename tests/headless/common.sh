# Sourced by the run-*.sh scripts, which set $log. A session is ATL under the cli host's -c as the
# coproc $pid, read from $atl_out and written to $atl_in.

# For env: a log filter, a log format or scripted answers left in the caller's shell would change
# the lines the checks read.
atl_env=(-u ANDROID_LOG_TAGS -u ATL_DIALOG_ANSWERS ANDROID_PRINTF_LOG=brief RUN_FROM_BUILDDIR=1)
headless_env=(-u DISPLAY -u WAYLAND_DISPLAY "${atl_env[@]}")

# Stops a running session's ATL and keeps the work dir for the log.
fail() {
  [[ -z ${pid:-} ]] || kill "$pid" 2>/dev/null || true
  echo "FAIL: $*"
  echo "log: $log"
  trap - EXIT
  exit 1
}

# faults <log>: what ATL prints and survives: an upcall prints and clears a Java exception, the
# finalizer daemon logs one.
faults() {
  local line
  line=$(grep -m1 -E $'^\tat |UnsatisfiedLinkError|JNI DETECTED ERROR|Runtime aborting|hacky_uncaught_exception_handler' "$1") || return 0
  fail "in the log: $line"
}

# After coproc ATL: bash unsets ATL once it reaped the coproc, output still unread, so the fds get
# names that stay.
session() {
  pid=$ATL_PID
  exec {atl_out}<&"${ATL[0]}" {atl_in}>&"${ATL[1]}"
}

# send <command>: a write to an ATL that is gone fails here, where SIGPIPE would end the script
# without a word.
send() {
  printf '> %s\n' "$1" >>"$log"
  trap '' PIPE
  printf '%s\n' "$1" 2>/dev/null >&"$atl_in" || fail "ATL exited before \"$1\""
  trap - PIPE
}

# read_dump [<regex>]: ATL's lines up to "viewtree: end" into the log, the dump's into the array
# dump; a dump that doesn't end fails, and so does a line matching the regex on the way.
read_dump() {
  local line
  dump=()
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
    [[ $line == viewtree:* ]] && dump+=("$line")
    [[ $line == "viewtree: end" ]] && return 0
    [[ -n ${1-} && $line =~ $1 ]] && fail "got \"$line\" in a view-tree dump"
  done
  fail "ATL went quiet or exited before the view-tree dump's end"
}

# end_session: for an app that stays up, after ATL answered the script's last command: only the
# kill may end it, so a death of its own after the last checked line fails.
end_session() {
  local rc=0
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || rc=$?
  pid=
  exec {atl_out}<&- {atl_in}>&-
  [[ $rc == 143 ]] || fail "ATL ended by itself, exit status $rc"
  faults "$log"
}

# exit_session: for an app whose last step finishes it: ATL exits 0 by itself.
exit_session() {
  local rc=0 line
  while IFS= read -r -t 30 line <&"$atl_out"; do
    printf '%s\n' "$line" >>"$log"
  done
  wait "$pid" 2>/dev/null || rc=$?
  pid=
  exec {atl_out}<&- {atl_in}>&-
  [[ $rc == 0 ]] || fail "exit status $rc"
  faults "$log"
}
