#!/usr/bin/env bash
set -uo pipefail
mkdir -p store-media/phone store-media/tablet
adb shell input keyevent 82
adb shell cmd uimode night no
adb shell settings put system font_scale 1.0
adb shell svc wifi disable || true
adb shell svc data disable || true
failed=0
capture() {
  local name="$1" size="$2" density="$3"
  adb shell am force-stop ru.epbloger.zaberi || true
  adb shell pm clear ru.epbloger.zaberi || true
  adb shell wm size "$size"
  adb shell wm density "$density"
  sleep 2
  bash .github/scripts/gradle-retry.sh connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.epbloger.zaberi.StoreMediaTest || failed=1
  adb pull /sdcard/Android/data/ru.epbloger.zaberi/files/store-media/. "store-media/$name/" || failed=1
}
capture phone 1080x1920 440
capture tablet 1920x1080 240
adb shell wm size reset
adb shell wm density reset
find store-media -type f -name '*.png' -print
exit "$failed"
