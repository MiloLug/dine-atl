# Framework tests

`android.*` API tests against ATL's api-impl, run in-VM on the headless build:
`tools/dine test framework` (after `tools/dine linux build`; engine `tests/harness/framework.py`).
`--gtk` runs them on the gtk build instead, one suite at a time; it needs a display, and
`tools/dine test framework --gtk` gives it a hidden one.

- Each `<suite>/` is a test APK (`AndroidManifest.xml`, `src/**/*.java`) built by
  `tests/harness/apk.py` against `test_runner.jar` and ART's JUnit 3 jars.
- The host runs it with `--instrument android.test.InstrumentationTestRunner`, which finds the
  APK's `TestCase`s in its dex. Tests that need a window start an activity of their own APK with
  `startActivitySync` and touch views in `runOnMainSync`.
- The runner always exits 0: the harness grades its `INSTRUMENTATION_*` output, and a run that
  aborts, finds no tests or leaves a test unfinished fails `suite:<name>`.
- `expectations/linux-x86_64-headless.json` and `linux-x86_64-gtk.json` list each build's tests
  expected to fail (`--update-expectations`, with `--gtk` for the gtk file, rewrites it).
- Runs set `MALLOC_PERTURB_`: freed memory is filled, which makes a use-after-free more likely to
  fail a test.
