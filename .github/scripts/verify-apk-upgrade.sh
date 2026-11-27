#!/usr/bin/env bash
set -euo pipefail
app=ru.epbloger.zaberi
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
current=app/build/outputs/apk/debug/app-debug.apk
old="${RUNNER_TEMP:?}/zaberi-1.0.3-baseline.apk"
reports=app/build/verification/apk-upgrade
mkdir -p "$reports"
test -f "$old"; test -f "$current"; test -f "$test_apk"
# Only the disposable CI emulator is reset.
adb uninstall "$app.test" || true
adb uninstall "$app" || true
adb install "$old"
adb install "$test_apk"
run_phase() {
    local phase="$1"
    adb shell am instrument -w -e class ru.epbloger.zaberi.PublishedApkUpgradeTest -e upgradePhase "$phase" "$app.test/androidx.test.runner.AndroidJUnitRunner" | tee "$reports/$phase.txt"
    grep -F 'OK (1 test)' "$reports/$phase.txt"
    if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$reports/$phase.txt"; then exit 1; fi
}
run_phase seed
adb shell am force-stop "$app"
adb install -r "$current"
run_phase verify
