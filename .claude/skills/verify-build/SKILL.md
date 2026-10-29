---
name: verify-build
description: Run OmniTuner's one verification pass (compile, app + shared unit tests, lint) and read the failures. Use after any code change before saying it's done, before every commit or merge, and whenever the user asks "does it build", "run the checks", "is lint clean" or wants something checked, even if they don't mention Gradle. Not for running or looking at the app on the emulator, which the user does themselves.
---

# Verify the build

Run exactly this from the repo root:

```bash
bash .claude/skills/verify-build/scripts/verify.sh
```

While iterating on a compile error, `--fast` runs the compile stage only. Run the full pass before reporting.

It sets `JAVA_HOME` to the Android Studio JBR, runs `:app:compileDebugKotlin`, then `:app:testDebugUnitTest :shared:testAndroidHostTest`, then `:app:lintDebug`, and prints `PASS`/`FAIL` per stage with only the error lines. A full pass takes a few minutes, so run it in the background. To run one shared test class while debugging it:

```bash
./gradlew.bat :shared:testAndroidHostTest --tests "com.omnituner.core.theory.KeyDetectorTest" --console=plain -q
```

## Loop

1. Run the script.
2. On `FAIL`, fix the cause (lint included: never suppress, baseline or `@SuppressLint` a lint error).
3. Run again. Report only once it prints `ALL PASS`.
4. Final report: what passed, and that the visual check on the emulator is the user's. Do not claim it looks right.

## Gotchas

- **Never touch the emulator**: no `adb install`, `am start`, `screencap`, `uiautomator`, `input`. Verification ends at this script.
- `Type 'State<…>' has no method 'getValue'… cannot serve as a delegate` means a missing import, not a type problem: add `import androidx.compose.runtime.getValue` (and `setValue` for `var … by`).
- The shared module is KMP with an Android host-test target. Its tests run with `:shared:testAndroidHostTest`, not `:shared:test` or `jvmTest` (there is no JVM target). `:shared:allTests` also works but is slower.
- Test results are JUnit XML in `app/build/test-results/testDebugUnitTest/` and `shared/build/test-results/testAndroidHostTest/`. The `<failure message=…>` attribute has the assertion; read it instead of rerunning with `--info`.
- A `java.lang.OutOfMemoryError: Java heap space` in a chord test means the voicing search is enumerating without pruning (7+ strings blow up by ×5–8 per string). It is an algorithm bug. Do not raise the heap. See `docs/chord-search-optimization.md`. Any change to `ChordVoicing.kt` gets tested on 6, 8 and 12 strings, not only a 6-string guitar.
- Pitch-detector tests assert within a cents tolerance. A failure like "off by 8.5 cents" is a real regression in the detector, not a flaky test.
- The tests can't catch a processed mic signal. `PitchCapture.kt` records with `MediaRecorder.AudioSource.UNPROCESSED` (no AGC, noise suppression or echo cancel), with `MIC` as the fallback. Keep that when touching capture.
- If the app shows a black screen or installs fail with empty errors, the user's emulator snapshot is stale. Tell them to Cold Boot the AVD (Device Manager → ⋮ → Cold Boot Now). Wiping data does not help. Do not debug app code for it.
- Deprecation warnings count: fix them when the replacement is a direct swap (e.g. `LocalLifecycleOwner` moved to `androidx.lifecycle.compose`).
