#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${BASELINE_API_LEVEL:-30}"
TARGET="${BASELINE_TARGET:-default}"
ABI="${BASELINE_ABI:-x86_64}"
AVD_NAME="${BASELINE_AVD_NAME:-baseline-ci}"
EMULATOR_PORT="${BASELINE_EMULATOR_PORT:-5554}"
BOOT_TIMEOUT_SECS="${BASELINE_BOOT_TIMEOUT_SECS:-900}"
GRADLE_TIMEOUT_SECS="${BASELINE_GRADLE_TIMEOUT_SECS:-2700}"
GRADLE_TASK="${BASELINE_GRADLE_TASK:-:app:generateBaselineProfile}"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set."
  exit 1
fi

export ANDROID_SDK_ROOT
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

SYSTEM_IMAGE="system-images;android-${API_LEVEL};${TARGET};${ABI}"
IMAGE_ABI="${TARGET}/${ABI}"
EMULATOR_SERIAL="emulator-${EMULATOR_PORT}"
EMULATOR_LOG="${RUNNER_TEMP:-/tmp}/baseline-emulator.log"
LOGCAT_FILE="${RUNNER_TEMP:-/tmp}/baseline-logcat.txt"

cleanup() {
  adb -s "${EMULATOR_SERIAL}" logcat -d >"${LOGCAT_FILE}" 2>/dev/null || true
  adb -s "${EMULATOR_SERIAL}" emu kill >/dev/null 2>&1 || true
  pkill -f "emulator.*-port ${EMULATOR_PORT}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

set +o pipefail
yes 2>/dev/null | sdkmanager --licenses >/dev/null
set -o pipefail

# Keep cmdline tools in sync with repository metadata to reduce sdkmanager protocol warnings.
sdkmanager --install "cmdline-tools;latest" >/dev/null
sdkmanager --install \
  "platform-tools" \
  "emulator" \
  "platforms;android-${API_LEVEL}" \
  "${SYSTEM_IMAGE}" >/dev/null

echo "no" | avdmanager create avd --force -n "${AVD_NAME}" --abi "${IMAGE_ABI}" --package "${SYSTEM_IMAGE}"

emulator \
  -port "${EMULATOR_PORT}" \
  -avd "${AVD_NAME}" \
  -no-window \
  -gpu swiftshader_indirect \
  -noaudio \
  -no-boot-anim \
  -accel off \
  -no-snapshot-load \
  -no-snapshot-save >"${EMULATOR_LOG}" 2>&1 &

if ! timeout "${BOOT_TIMEOUT_SECS}" adb -s "${EMULATOR_SERIAL}" wait-for-device; then
  echo "Emulator device was not detected within ${BOOT_TIMEOUT_SECS}s."
  tail -n 200 "${EMULATOR_LOG}" || true
  exit 1
fi

boot_completed=""
for ((elapsed=0; elapsed<BOOT_TIMEOUT_SECS; elapsed+=5)); do
  boot_completed="$(adb -s "${EMULATOR_SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "${boot_completed}" == "1" ]]; then
    break
  fi
  sleep 5
done

if [[ "${boot_completed}" != "1" ]]; then
  echo "Emulator failed to finish boot within ${BOOT_TIMEOUT_SECS}s."
  tail -n 200 "${EMULATOR_LOG}" || true
  exit 1
fi

adb -s "${EMULATOR_SERIAL}" shell input keyevent 82 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true

if ! timeout "${GRADLE_TIMEOUT_SECS}" ./gradlew --no-daemon "${GRADLE_TASK}" \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile; then
  status=$?
  if [[ "${status}" -eq 124 ]]; then
    echo "Gradle baseline task timed out after ${GRADLE_TIMEOUT_SECS}s."
  fi
  tail -n 200 "${EMULATOR_LOG}" || true
  exit "${status}"
fi
