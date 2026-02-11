#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONSTANTS_FILE="${1:-${ROOT_DIR}/constants.gradle.kts}"
READY_TIMEOUT_SECS="${TARGET_SDK_SMOKE_READY_TIMEOUT_SECS:-360}"
EMULATOR_BOOT_TIMEOUT_SECS="${TARGET_SDK_SMOKE_BOOT_TIMEOUT_SECS:-240}"
SMOKE_CLASSES="${SMOKE_TEST_CLASSES:-com.ytone.longcare.smoke.MainActivitySmokeTest,com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest}"
AVD_NAME="${TARGET_SDK_AVD:-}"
ADB_BIN="${ADB_BIN:-}"
EMULATOR_BIN="${EMULATOR_BIN:-}"
STARTED_EMULATOR="false"

resolve_bin() {
  local explicit="$1"
  shift
  if [ -n "${explicit}" ] && [ -x "${explicit}" ]; then
    echo "${explicit}"
    return 0
  fi

  local candidate
  for candidate in "$@"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ]; then
      echo "${candidate}"
      return 0
    fi
  done

  return 1
}

extract_target_sdk() {
  sed -nE 's/.*appTargetSdkVersion by extra\(([0-9]+)\).*/\1/p' "${CONSTANTS_FILE}" | head -n1
}

list_emulator_serials() {
  "${ADB_BIN}" devices | sed -nE 's/^(emulator-[0-9]+)[[:space:]]+device$/\1/p'
}

find_ready_target_serial() {
  local serial
  while IFS= read -r serial; do
    [ -n "${serial}" ] || continue
    local sdk
    local boot
    sdk="$("${ADB_BIN}" -s "${serial}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    boot="$("${ADB_BIN}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [ "${sdk}" = "${TARGET_SDK}" ] && [ "${boot}" = "1" ]; then
      echo "${serial}"
      return 0
    fi
  done < <(list_emulator_serials)

  return 1
}

resolve_avd_name() {
  local listed
  listed="$("${EMULATOR_BIN}" -list-avds)"

  if [ -n "${AVD_NAME}" ]; then
    if echo "${listed}" | grep -Fxq "${AVD_NAME}"; then
      return 0
    fi
    echo "Configured TARGET_SDK_AVD not found: ${AVD_NAME}" >&2
    echo "Available AVDs:" >&2
    echo "${listed}" >&2
    exit 1
  fi

  AVD_NAME="$(echo "${listed}" | grep -E "API_${TARGET_SDK}(\.|_|$)" | head -n1 || true)"
  if [ -z "${AVD_NAME}" ]; then
    echo "No AVD matched targetSdk=${TARGET_SDK}. Set TARGET_SDK_AVD explicitly." >&2
    echo "Available AVDs:" >&2
    echo "${listed}" >&2
    exit 1
  fi
}

start_target_emulator() {
  resolve_avd_name
  echo "Starting emulator AVD=${AVD_NAME} for targetSdk=${TARGET_SDK}..."
  nohup "${EMULATOR_BIN}" -avd "${AVD_NAME}" \
    -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot-load -no-snapshot-save \
    > /tmp/target_sdk_local_smoke_emulator.log 2>&1 &
  STARTED_EMULATOR="true"
}

cleanup() {
  if [ "${STARTED_EMULATOR}" = "true" ]; then
    local serial
    serial="$(find_ready_target_serial || true)"
    if [ -n "${serial}" ]; then
      "${ADB_BIN}" -s "${serial}" emu kill >/dev/null 2>&1 || true
    fi
  fi
}

trap cleanup EXIT

if [ ! -f "${CONSTANTS_FILE}" ]; then
  echo "Constants file not found: ${CONSTANTS_FILE}" >&2
  exit 1
fi

ADB_BIN="$(resolve_bin "${ADB_BIN}" "$(command -v adb || true)" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" "${ANDROID_HOME:-}/platform-tools/adb" "${HOME}/Library/Android/sdk/platform-tools/adb")"
EMULATOR_BIN="$(resolve_bin "${EMULATOR_BIN}" "$(command -v emulator || true)" "${ANDROID_SDK_ROOT:-}/emulator/emulator" "${ANDROID_HOME:-}/emulator/emulator" "${HOME}/Library/Android/sdk/emulator/emulator")"
TARGET_SDK="$(extract_target_sdk)"

if [ -z "${TARGET_SDK}" ]; then
  echo "Failed to parse targetSdk from ${CONSTANTS_FILE}" >&2
  exit 1
fi

echo "Using adb: ${ADB_BIN}"
echo "Using emulator: ${EMULATOR_BIN}"
echo "Target SDK: ${TARGET_SDK}"

"${ADB_BIN}" start-server >/dev/null

TARGET_SERIAL="$(find_ready_target_serial || true)"
if [ -z "${TARGET_SERIAL}" ]; then
  if [ -n "$(list_emulator_serials)" ]; then
    echo "No ready emulator with API ${TARGET_SDK} found among running emulators:" >&2
    while IFS= read -r serial; do
      [ -n "${serial}" ] || continue
      sdk="$("${ADB_BIN}" -s "${serial}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
      boot="$("${ADB_BIN}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
      echo "  - ${serial}: sdk=${sdk:-unknown}, boot=${boot:-unknown}" >&2
    done < <(list_emulator_serials)

    start_target_emulator
  else
    start_target_emulator
  fi

  elapsed=0
  while [ "${elapsed}" -lt "${EMULATOR_BOOT_TIMEOUT_SECS}" ]; do
    TARGET_SERIAL="$(find_ready_target_serial || true)"
    if [ -n "${TARGET_SERIAL}" ]; then
      break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done

  if [ -z "${TARGET_SERIAL}" ]; then
    echo "Timed out waiting for API ${TARGET_SDK} emulator to boot." >&2
    echo "Recent emulator log:" >&2
    tail -n 80 /tmp/target_sdk_local_smoke_emulator.log >&2 || true
    exit 1
  fi
fi

echo "Using emulator serial: ${TARGET_SERIAL}"

cd "${ROOT_DIR}"
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest -Pbaseline.enableX86_64=true

ADB_BIN="${ADB_BIN}" \
SMOKE_DEVICE_SERIAL="${TARGET_SERIAL}" \
SMOKE_READY_TIMEOUT_SECS="${READY_TIMEOUT_SECS}" \
SMOKE_TEST_CLASSES="${SMOKE_CLASSES}" \
bash .github/scripts/run-instrumentation-smoke.sh

echo "Local target SDK smoke verification passed."
