#!/usr/bin/env bash
# One verification pass for OmniTuner: compile, unit tests (app + shared), lint.
# Usage: bash .claude/skills/verify-build/scripts/verify.sh [--fast]
#   --fast   compile only (use while iterating; run the full pass before reporting done)
# Prints only what matters: PASS/FAIL per stage, Kotlin errors, failed tests, lint issues.
set -u

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  sed -n '2,5p' "$0"; exit 0
fi

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT" || exit 2
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-/c/Program Files/Android/Android Studio/jbr}"
if [ ! -x "$JAVA_HOME/bin/java.exe" ] && [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "FAIL setup: no JDK at $JAVA_HOME (set JAVA_HOME_OVERRIDE)"; exit 2
fi

LOG="$(mktemp)"
run() { # $1 = stage name, rest = gradle tasks
  local name="$1"; shift
  if ./gradlew.bat "$@" --console=plain -q >"$LOG" 2>&1; then
    echo "PASS $name"; return 0
  fi
  echo "FAIL $name"
  # Kotlin compiler errors, test failures, then the gradle summary.
  local errs
  errs="$(grep -E '^e: |FAILED$|AssertionError|OutOfMemoryError' "$LOG" | sed -E 's|file:///[A-Za-z]:/[^ ]*/OmniTuner-Android-ver/||' | head -40)"
  if [ -n "$errs" ]; then echo "$errs"; else grep -A4 'What went wrong' "$LOG" | sed -n '2,5p'; fi
  return 1
}

status=0
run compile :app:compileDebugKotlin || { rm -f "$LOG"; exit 1; }
if [ "${1:-}" = "--fast" ]; then rm -f "$LOG"; exit 0; fi

run tests :app:testDebugUnitTest :shared:testAndroidHostTest || {
  status=1
  for f in app/build/test-results/testDebugUnitTest/*.xml shared/build/test-results/testAndroidHostTest/*.xml; do
    [ -f "$f" ] && grep -l '<failure' "$f" >/dev/null 2>&1 && echo "  report: $f"
  done
}

if ! run lint :app:lintDebug; then
  status=1
  TXT="app/build/reports/lint-results-debug.txt"
  [ -f "$TXT" ] && head -40 "$TXT"
  echo "  report: app/build/reports/lint-results-debug.html"
fi

rm -f "$LOG"
[ $status -eq 0 ] && echo "ALL PASS - ready for the user to check on the emulator"
exit $status
