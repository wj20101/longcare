#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${BASELINE_API_LEVEL:-33}"
TARGET="${BASELINE_TARGET:-default}"
ABI="${BASELINE_ABI:-x86_64}"
AVD_NAME="${BASELINE_AVD_NAME:-baseline-ci}"
DEFAULT_BASELINE_HOME="${RUNNER_TEMP:-/tmp}"
BASELINE_ANDROID_SDK_HOME="${BASELINE_ANDROID_SDK_HOME:-${DEFAULT_BASELINE_HOME}/android-sdk-home}"
BASELINE_ANDROID_AVD_HOME="${BASELINE_AVD_HOME:-${BASELINE_ANDROID_SDK_HOME}/avd}"
EMULATOR_PORT="${BASELINE_EMULATOR_PORT:-5554}"
BOOT_TIMEOUT_SECS="${BASELINE_BOOT_TIMEOUT_SECS:-900}"
DEVICE_READY_TIMEOUT_SECS="${BASELINE_DEVICE_READY_TIMEOUT_SECS:-300}"
BOOT_STABILIZE_SECS="${BASELINE_BOOT_STABILIZE_SECS:-30}"
PARTITION_SIZE_MB="${BASELINE_PARTITION_SIZE_MB:-2048}"
EMULATOR_MEMORY_MB="${BASELINE_EMULATOR_MEMORY_MB:-3072}"
GRADLE_TIMEOUT_SECS="${BASELINE_GRADLE_TIMEOUT_SECS:-2700}"
GRADLE_RETRIES="${BASELINE_GRADLE_RETRIES:-1}"
GRADLE_RETRY_DELAY_SECS="${BASELINE_GRADLE_RETRY_DELAY_SECS:-90}"
GRADLE_TASK="${BASELINE_GRADLE_TASK:-:app:generateReleaseBaselineProfile}"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${ANDROID_SDK_ROOT}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set."
  exit 1
fi

export ANDROID_SDK_ROOT

select_writable_dir() {
  local requested="$1"
  local fallback="$2"
  local label="$3"
  if mkdir -p "${requested}" 2>/dev/null && [[ -w "${requested}" ]]; then
    echo "${requested}"
    return 0
  fi
  echo "${label} path is not writable: ${requested}. Falling back to ${fallback}." >&2
  mkdir -p "${fallback}"
  echo "${fallback}"
}

export ANDROID_SDK_HOME="$(select_writable_dir "${BASELINE_ANDROID_SDK_HOME}" "${DEFAULT_BASELINE_HOME}/android-sdk-home" "ANDROID_SDK_HOME")"
export ANDROID_AVD_HOME="$(select_writable_dir "${BASELINE_ANDROID_AVD_HOME}" "${DEFAULT_BASELINE_HOME}/android-avd" "ANDROID_AVD_HOME")"
export PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/avdmanager"

MIN_FREE_MB="${BASELINE_MIN_FREE_MB:-}"
if [[ -z "${MIN_FREE_MB}" ]]; then
  MIN_FREE_MB=$((PARTITION_SIZE_MB + 4096))
fi

if [[ ! -x "${SDKMANAGER}" || ! -x "${AVDMANAGER}" ]]; then
  echo "Missing sdkmanager/avdmanager under ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin."
  echo "Ensure android-actions/setup-android@v3 runs before this script."
  exit 1
fi

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

rm -rf "${ANDROID_AVD_HOME:?}/${AVD_NAME}.avd" "${ANDROID_AVD_HOME:?}/${AVD_NAME}.ini"
echo "Using ANDROID_SDK_HOME=${ANDROID_SDK_HOME}"
echo "Using ANDROID_AVD_HOME=${ANDROID_AVD_HOME}"
echo "Using BASELINE_PARTITION_SIZE_MB=${PARTITION_SIZE_MB}"
echo "Using BASELINE_MIN_FREE_MB=${MIN_FREE_MB}"
echo "Using BASELINE_BOOT_STABILIZE_SECS=${BOOT_STABILIZE_SECS}"
echo "Using BASELINE_EMULATOR_MEMORY_MB=${EMULATOR_MEMORY_MB}"
echo "Using BASELINE_GRADLE_RETRIES=${GRADLE_RETRIES}"
echo "Using BASELINE_GRADLE_RETRY_DELAY_SECS=${GRADLE_RETRY_DELAY_SECS}"
echo "Disk usage before emulator boot:"
df -h "${ANDROID_AVD_HOME}" || true

available_kb="$(df -Pk "${ANDROID_AVD_HOME}" | awk 'NR==2 {print $4}')"
available_mb=$((available_kb / 1024))
if ((available_mb < MIN_FREE_MB)); then
  echo "Insufficient free disk for emulator boot."
  echo "Available: ${available_mb} MB, required minimum: ${MIN_FREE_MB} MB."
  exit 1
fi

set +o pipefail
yes 2>/dev/null | "${SDKMANAGER}" --licenses >/dev/null
set -o pipefail

"${SDKMANAGER}" --install \
  "platform-tools" \
  "emulator" \
  "platforms;android-${API_LEVEL}" \
  "${SYSTEM_IMAGE}" >/dev/null

echo "no" | "${AVDMANAGER}" create avd --force -n "${AVD_NAME}" --abi "${IMAGE_ABI}" --package "${SYSTEM_IMAGE}"

if ! emulator -list-avds | grep -Fxq "${AVD_NAME}"; then
  echo "AVD ${AVD_NAME} was not registered after creation."
  echo "Available AVDs:"
  emulator -list-avds || true
  echo "Contents of ${ANDROID_AVD_HOME}:"
  ls -la "${ANDROID_AVD_HOME}" || true
  exit 1
fi

emulator \
  -port "${EMULATOR_PORT}" \
  -avd "${AVD_NAME}" \
  -memory "${EMULATOR_MEMORY_MB}" \
  -no-window \
  -gpu swiftshader_indirect \
  -noaudio \
  -no-boot-anim \
  -accel off \
  -partition-size "${PARTITION_SIZE_MB}" \
  -no-metrics \
  -no-snapshot-load \
  -no-snapshot-save >"${EMULATOR_LOG}" 2>&1 &
EMULATOR_PID=$!
sleep 5
if ! kill -0 "${EMULATOR_PID}" 2>/dev/null; then
  echo "Emulator process exited before boot finished."
  tail -n 200 "${EMULATOR_LOG}" || true
  echo "Available AVDs:"
  emulator -list-avds || true
  exit 1
fi

if ! timeout "${BOOT_TIMEOUT_SECS}" adb -s "${EMULATOR_SERIAL}" wait-for-device; then
  echo "Emulator device was not detected within ${BOOT_TIMEOUT_SECS}s."
  echo "Available AVDs:"
  emulator -list-avds || true
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

wait_for_package_manager() {
  local timeout_secs="$1"
  local package_ready=""
  for ((elapsed=0; elapsed<timeout_secs; elapsed+=5)); do
    package_service_status="$(adb -s "${EMULATOR_SERIAL}" shell service check package 2>/dev/null | tr -d '\r')"
    if [[ "${package_service_status}" == *"Service package: found"* ]] &&
      adb -s "${EMULATOR_SERIAL}" shell cmd package path android >/dev/null 2>&1; then
      package_ready="1"
      break
    fi
    sleep 5
  done
  [[ "${package_ready}" == "1" ]]
}

# Some system services (notably Package Manager) may lag behind sys.boot_completed.
if ! wait_for_package_manager "${DEVICE_READY_TIMEOUT_SECS}"; then
  echo "Package Manager service was not ready within ${DEVICE_READY_TIMEOUT_SECS}s after boot."
  echo "sys.boot_completed=$(adb -s "${EMULATOR_SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  echo "dev.bootcomplete=$(adb -s "${EMULATOR_SERIAL}" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r')"
  echo "service check package:"
  adb -s "${EMULATOR_SERIAL}" shell service check package 2>/dev/null || true
  echo "tail of emulator log:"
  tail -n 200 "${EMULATOR_LOG}" || true
  exit 1
fi

# Give system services additional time to settle before APK install.
sleep "${BOOT_STABILIZE_SECS}"

adb -s "${EMULATOR_SERIAL}" shell input keyevent 82 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
adb -s "${EMULATOR_SERIAL}" shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true

attempt=1
while true; do
  if ! wait_for_package_manager "${DEVICE_READY_TIMEOUT_SECS}"; then
    echo "Package Manager service was not ready before Gradle attempt ${attempt}."
    tail -n 200 "${EMULATOR_LOG}" || true
    exit 1
  fi

  set +e
  timeout "${GRADLE_TIMEOUT_SECS}" ./gradlew --no-daemon "${GRADLE_TASK}" \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
  status=$?
  set -e
  if [[ "${status}" -eq 0 ]]; then
    break
  fi
  if [[ "${status}" -eq 124 ]]; then
    echo "Gradle baseline task timed out after ${GRADLE_TIMEOUT_SECS}s on attempt ${attempt}."
  else
    echo "Gradle baseline task failed with status ${status} on attempt ${attempt}."
  fi
  if (( attempt > GRADLE_RETRIES )); then
    tail -n 200 "${EMULATOR_LOG}" || true
    exit "${status}"
  fi
  echo "Retrying baseline Gradle task in ${GRADLE_RETRY_DELAY_SECS}s..."
  adb start-server >/dev/null 2>&1 || true
  sleep "${GRADLE_RETRY_DELAY_SECS}"
  attempt=$((attempt + 1))
done

if ! find app/src -type f \( -name "baseline-prof.txt" -o -name "startup-prof.txt" -o -path "*/generated/baselineProfiles/*.txt" \) | grep -q .; then
  fallback_profile="$(find app/build/intermediates -type f -name "baseline-prof.txt" \( -path "*/combined_art_profile/*" -o -path "*/merged_art_profile/*" \) | head -n 1)"
  if [[ -n "${fallback_profile}" ]]; then
    mkdir -p app/src/main/generated/baselineProfiles
    cp "${fallback_profile}" app/src/main/generated/baselineProfiles/baseline-prof.txt
    echo "Copied fallback baseline profile from ${fallback_profile} to app/src/main/generated/baselineProfiles/baseline-prof.txt"
  fi
fi

echo "Baseline profile files under app/src after ${GRADLE_TASK}:"
find app/src -type f \( -name "baseline-prof.txt" -o -name "startup-prof.txt" -o -path "*/generated/baselineProfiles/*.txt" \) | sort || true
