#!/usr/bin/env bash
set -euo pipefail
mkdir -p store-media/phone store-media/tablet
adb shell input keyevent 82
adb shell cmd uimode night no
adb shell settings put system font_scale 1.0
adb shell svc wifi disable || true
adb shell svc data disable || true
app="$(find app/build/outputs/apk/debug -name '*.apk' -print -quit)"
testapk="$(find app/build/outputs/apk/androidTest/debug -name '*.apk' -print -quit)"
test -n "$app"
test -n "$testapk"
adb install -r -t "$app"
adb install -r -t "$testapk"
failed=0
capture() {
  local name="$1" size="$2" density="$3"
  adb shell am force-stop ru.epbloger.zaberi
  adb shell pm clear ru.epbloger.zaberi
  adb shell wm size "$size"
  adb shell wm density "$density"
  sleep 2
  adb shell am instrument -w -r -e class ru.epbloger.zaberi.StoreMediaTest ru.epbloger.zaberi.test/androidx.test.runner.AndroidJUnitRunner | tee "/tmp/store-media-$name.log" || failed=1
  if ! grep -Fq 'OK (1 test)' "/tmp/store-media-$name.log"; then failed=1; fi
  adb pull /sdcard/Android/data/ru.epbloger.zaberi/files/store-media/. "store-media/$name/" || failed=1
  if [[ "$(find "store-media/$name" -type f -name '*.png' | wc -l)" != 5 ]]; then failed=1; fi
}
capture phone 1080x1920 440
capture tablet 1920x1080 240
adb shell wm size reset
adb shell wm density reset
find store-media -type f -name '*.png' -print
exit "$failed"
