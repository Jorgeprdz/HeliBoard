#!/usr/bin/env bash
# R2.5.1 — isolated Android instrumentation evidence; NO product APK publication.
set -uo pipefail
mkdir -p r25-evidence
adb shell getprop ro.product.manufacturer > r25-evidence/manufacturer.txt || true
adb shell getprop ro.product.model > r25-evidence/model.txt || true
adb shell getprop ro.build.version.release > r25-evidence/android-version.txt || true
# Fresh Android 15 emulator may report "null" until Gboard/default IME boots.
# Snapshot the pre-ready state separately; never compare an uninitialized value.
adb shell settings get secure default_input_method > r25-evidence/ime-boot-initial.txt || true
ready=0
for attempt in $(seq 1 40); do
  ime="$(adb shell settings get secure default_input_method 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$ime" && "$ime" != "null" ]]; then
    printf '%s\n' "$ime" > r25-evidence/ime-before.txt
    echo "R252_IME_READY_AFTER_ATTEMPT=$attempt"
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then
  echo 'R252_NO_GO: emulator never established a valid default IME before test'
  exit 1
fi
adb logcat -c || true

capture() {
  local phase="$1"
  local dir="r25-evidence/$phase"
  mkdir -p "$dir"
  # Capture BEFORE android-emulator-runner tears down the emulator.
  adb logcat -d -b all -v threadtime > "$dir/logcat-all.txt" 2>&1 || true
  adb logcat -d -b crash -v threadtime > "$dir/logcat-crash.txt" 2>&1 || true
  adb shell dumpsys dropbox --print data_app_crash > "$dir/dropbox-crashes.txt" 2>&1 || true
  adb shell pm list instrumentation > "$dir/installed-instrumentation.txt" 2>&1 || true
  adb shell dumpsys activity processes > "$dir/activity-processes.txt" 2>&1 || true
  adb shell settings get secure default_input_method > "$dir/default-ime.txt" 2>&1 || true
  if [ -d app/build/outputs/androidTest-results/connected ]; then
    cp -R app/build/outputs/androidTest-results/connected "$dir/androidTest-results" || true
  else
    printf 'No connected XML output present for %s\n' "$phase" > "$dir/xml-missing.txt"
  fi
  if [ -d app/build/reports/androidTests ]; then
    cp -R app/build/reports/androidTests "$dir/androidTests-html" || true
  fi
}
trap 'capture exit-final' EXIT

run_gate() {
  local phase="$1" class="$2" result
  mkdir -p "r25-evidence/$phase"
  echo "R251_GATE_START phase=$phase class=$class"
  ./gradlew --init-script app/src/androidTest/instrumentation.init.gradle \
    :app:connectedDebugNoMinifyAndroidTest --no-daemon --stacktrace \
    -Pandroid.injected.build.abi=x86_64 \
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" \
    2>&1 | tee "r25-evidence/$phase/gradle.log"
  result=${PIPESTATUS[0]}
  printf '%s\n' "$result" > "r25-evidence/$phase/gradle-exit.txt"
  capture "$phase"
  echo "R251_GATE_END phase=$phase status=$result"
  return "$result"
}

if ! run_gate smoke helium314.keyboard.latin.common.dynamic.R251RunnerSmokeTest; then
  echo "R251_NO_GO: runner smoke failed; vector tests deliberately not attempted"
  exit 1
fi
# Second gate is not attempted until a real smoke XML and one passing case are present.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
reports=list(Path('r25-evidence/smoke/androidTest-results').rglob('*.xml'))
assert reports, 'Smoke did not yield Android instrumentation XML'
counts={'tests':0, 'failures':0, 'errors':0, 'skipped':0}
for path in reports:
    root=ET.parse(path).getroot()
    for key in counts: counts[key]+=int(root.attrib.get(key, 0))
assert counts == {'tests':1,'failures':0,'errors':0,'skipped':0}, counts
print('R251_SMOKE_XML_PASS', counts)
PY
if [ "$?" -ne 0 ]; then
  echo "R251_NO_GO: smoke XML missing or invalid; vector tests not attempted"
  exit 1
fi
adb logcat -c || true
if ! run_gate vector helium314.keyboard.latin.common.dynamic.VectorAndroidGateTest; then
  echo "R251_NO_GO: five vector tests failed; evidence captured"
  exit 1
fi
cp r25-evidence/vector/gradle.log r25-evidence/instrumentation.log
adb logcat -d -s R25_VECTOR_GATE:I '*:S' > r25-evidence/seed-metrics.log || true
adb shell settings get secure default_input_method > r25-evidence/ime-after.txt || true
if ! diff -u r25-evidence/ime-before.txt r25-evidence/ime-after.txt > r25-evidence/ime.diff; then
  echo "R251_NO_GO: default IME changed"
  exit 1
fi
echo "R251_INSTRUMENTATION_COMPLETED; strict vector assertions still required"

# AGP connectedAndroidTest uninstalls instrumentation after each run:
# a raw "am instrument" would discover zero registered test runners.
# Reuse the same isolated Gradle runner; capture separate XML before its next call.
adb logcat -c || true
if ! run_gate r252 helium314.keyboard.latin.common.dynamic.R252PaletteAndroidGateTest; then
  echo 'R252_NO_GO: Android lifecycle test class failed; logs and XML retained'
  exit 1
fi
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
reports=list(Path('r25-evidence/r252/androidTest-results').rglob('*.xml'))
assert reports, 'No R2.5.2 instrumented XML'
counts={k:0 for k in ('tests','failures','errors','skipped')}
for p in reports:
    root=ET.parse(p).getroot()
    for k in counts: counts[k]+=int(root.attrib.get(k,0))
print('R252_ANDROID_GATE',counts)
assert counts==dict(tests=3,failures=0,errors=0,skipped=0),counts
PY
# Enforce the IME isolation invariant across all 9 instrumented cases.
adb shell settings get secure default_input_method > r25-evidence/ime-after-r252.txt || true
if ! diff -u r25-evidence/ime-before.txt r25-evidence/ime-after-r252.txt > r25-evidence/ime-r252.diff; then
  echo 'R252_NO_GO: default IME changed during R2.5.2 tests'
  exit 1
fi
echo 'R252_ANDROID_GATE_PASS 3/3'
