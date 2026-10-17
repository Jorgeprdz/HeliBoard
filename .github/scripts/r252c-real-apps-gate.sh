#!/usr/bin/env bash
# R2.5.2c-1: distinct installed UIDs and authentic IME input focus. No product APK.
set -euo pipefail
mkdir -p r252c-evidence/screenshots
capture() {
  set +e
  adb logcat -d -b all -v threadtime > r252c-evidence/logcat-all.txt 2>&1
  adb logcat -d -b crash -v threadtime > r252c-evidence/logcat-crash.txt 2>&1
  adb shell dumpsys display > r252c-evidence/dumpsys-display-after.txt 2>&1
  adb shell cmd package list packages -U > r252c-evidence/packages-with-uids.txt 2>&1
  adb shell settings get secure default_input_method > r252c-evidence/ime-after.txt 2>&1
  if test -d app/build/outputs/androidTest-results/connected; then
    cp -R app/build/outputs/androidTest-results/connected r252c-evidence/androidTest-results
  fi
  adb shell ls /sdcard/Download/r252c-*.png 2>/dev/null | tr -d '\r' |
    while IFS= read -r path; do
      test -n "$path" && adb pull "$path" "r252c-evidence/screenshots/$(basename "$path")"
    done
  if test -f r252c-evidence/ime-before.txt && test -f r252c-evidence/ime-after.txt; then
    diff -u r252c-evidence/ime-before.txt r252c-evidence/ime-after.txt > r252c-evidence/ime-restoration.diff
  fi
}
trap capture EXIT
adb wait-for-device
ready=0
for n in $(seq 1 40); do
  ime="$(adb shell settings get secure default_input_method | tr -d '\r')"
  if [[ -n "$ime" && "$ime" != "null" ]]; then
    printf '%s\n' "$ime" > r252c-evidence/ime-before.txt
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != 1 ]]; then echo "BLOCKED: emulator default IME never initialized"; exit 1; fi
adb shell dumpsys display > r252c-evidence/dumpsys-display-before.txt
for id in a b; do
  test -s "r252c-evidence/app-$id.apk"
  adb install -r "r252c-evidence/app-$id.apk"
done
adb logcat -c
set +e
./gradlew --init-script app/src/androidTest/instrumentation.init.gradle \
  :app:connectedDebugNoMinifyAndroidTest --no-daemon \
  -Pandroid.injected.build.abi=x86_64 \
  '-Pandroid.testInstrumentationRunnerArguments.class=helium314.keyboard.latin.common.dynamic.R252cRealAppsTest' \
  > r252c-evidence/instrumentation-gradle.log 2>&1
status="$?"
set -e
cat r252c-evidence/instrumentation-gradle.log
echo "$status" > r252c-evidence/gradle-exit.txt
if [[ "$status" -ne 0 ]]; then
  echo "R252C1_FAIL_OR_BLOCKED: authentic-host instrumented run exited $status"
  exit "$status"
fi
echo 'R252C1_INSTRUMENTED_FINISHED; XML and logcat verdict are checked by workflow.'
