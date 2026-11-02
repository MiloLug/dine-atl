# Package tests

Install, query and removal through the package registry on the headless build:
`tools/dine test package` (after `tools/dine linux build`; engine `tests/harness/package.py`). Every
check runs in a prefix of its own (`DINE_PREFIX`).

- `fixtures/`: the APKs the checks install, built by `tests/harness/apk.py`; `package.py`'s
  `FIXTURES` names each variant (version code, renamed package, signing key). `ext` has no launcher
  activity and requests the feature `dine.test.extension`; `driver` is an installer app.
- `keys/`: two test keystores (`a.p12`, `b.p12`, password `dine-test`), our own; fixtures are
  v1-signed with them, since ATL reads no v2 or v3 signature block.
- Rows `cli:*`: installs, uninstalls and runs through the CLI, graded on exit codes, the registry's
  rows (read with Python's `sqlite3`) and the files in the prefix.
- `suite/`: a JUnit APK run with `--instrument` after the harness installs its fixtures; its rows are
  `<Class>#<test>`.
- Rows `scenario:*`: the `driver` app installs the way apps do (sessions, intents), one process per
  scenario, its dialogs answered in order by `ATL_DIALOG_ANSWERS`.
- On the phone, the dev build's atl screen has an `installer` tile: `driver` installs `ext` through a
  session and confirms from its Application context, the dialog answered by hand or `dbg ui tap`.
- `expectations/linux-x86_64-headless.json`: rows expected to fail (`--update-expectations`).

Not covered yet: multidex, per-ABI native libs, broken zips, v2 and v3 signatures.
