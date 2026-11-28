#!/usr/bin/env bash
set -u
mkdir -p app/build/verification
cp /tmp/zaberi-ime.txt app/build/verification/ime-initial.txt
failed=0
run_suite() {
  profile="$1"
  shift
  mkdir -p "app/build/verification/$profile"
  # The Android 15 Google APIs image can leave a NexusLauncher ANR dialog
  # in front of the app. Reset only the unrelated launcher, never ZABERI.
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell dumpsys window > "app/build/verification/$profile/window-before.txt"
  if grep -F 'mCurrentFocus=' "app/build/verification/$profile/window-before.txt" | grep -Fq 'Application Not Responding: com.google.android.apps.nexuslauncher'; then
    adb shell am force-stop com.google.android.apps.nexuslauncher
  fi
  gradle --no-daemon --stacktrace connectedDebugAndroidTest "$@" || failed=1
  cp -r app/build/reports/androidTests/connected/. "app/build/verification/$profile/" || true
  adb shell dumpsys input_method > "app/build/verification/$profile/input-method.txt"
  adb shell dumpsys window > "app/build/verification/$profile/window.txt"
}
run_suite default
adb shell wm size 720x1280
adb shell wm density 320
adb shell settings put system font_scale 1.3
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton || true
adb shell am force-stop ru.epbloger.zaberi
run_suite compact -Pandroid.testInstrumentationRunnerArguments.class=ru.epbloger.zaberi.AdaptiveLayoutTest,ru.epbloger.zaberi.ProductBrowserLayoutTest
adb shell wm size 1280x800
adb shell wm density 160
adb shell settings put system font_scale 1.5
adb shell am force-stop ru.epbloger.zaberi
run_suite tablet -Pandroid.testInstrumentationRunnerArguments.class=ru.epbloger.zaberi.AdaptiveLayoutTest,ru.epbloger.zaberi.ProductBrowserLayoutTest
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
exit "$failed"
