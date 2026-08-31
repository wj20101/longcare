#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SETTINGS_FILE="${ROOT_DIR}/settings.gradle.kts"
PLATFORM_API=""
READY_TIMEOUT_SECS="${TARGET_SDK_SMOKE_READY_TIMEOUT_SECS:-360}"
EMULATOR_BOOT_TIMEOUT_SECS="${TARGET_SDK_SMOKE_BOOT_TIMEOUT_SECS:-240}"
MATRIX_FILE="${TARGET_PLATFORM_MATRIX_FILE:-${ROOT_DIR}/scripts/quality/target_platform_test_matrix.properties}"
AVD_NAME="${TARGET_SDK_AVD:-}"
ADB_BIN="${ADB_BIN:-}"
EMULATOR_BIN="${EMULATOR_BIN:-}"
STARTED_EMULATOR="false"

# shellcheck source=scripts/quality/android_build_values.sh
source "${ROOT_DIR}/scripts/quality/android_build_values.sh"
# shellcheck source=scripts/quality/target_readiness_values.sh
source "${ROOT_DIR}/scripts/quality/target_readiness_values.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --settings)
      SETTINGS_FILE="${2:-}"
      shift 2
      ;;
    --platform-api)
      PLATFORM_API="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: run_target_sdk_local_smoke.sh [--settings <path>] [--platform-api <api>]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -n "${SMOKE_TEST_CLASSES:-}" ]]; then
  SMOKE_CLASSES="${SMOKE_TEST_CLASSES}"
elif [[ -n "${PLATFORM_API}" && "${PLATFORM_API}" != "$(read_target_readiness_value "${MATRIX_FILE}" current_target_api)" ]]; then
  SMOKE_CLASSES="$(read_target_readiness_value "${MATRIX_FILE}" candidate_smoke_classes)"
else
  SMOKE_CLASSES="$(read_target_readiness_value "${MATRIX_FILE}" current_target_smoke_classes)"
fi

if [[ -n "${LOGIN_FEATURE_SMOKE_TEST_CLASSES:-}" ]]; then
  LOGIN_FEATURE_SMOKE_CLASSES="${LOGIN_FEATURE_SMOKE_TEST_CLASSES}"
elif [[ -n "${PLATFORM_API}" && "${PLATFORM_API}" != "$(read_target_readiness_value "${MATRIX_FILE}" current_target_api)" ]]; then
  LOGIN_FEATURE_SMOKE_CLASSES="$(
    read_target_readiness_value "${MATRIX_FILE}" candidate_target_login_feature_smoke_classes
  )"
else
  LOGIN_FEATURE_SMOKE_CLASSES="$(
    read_target_readiness_value "${MATRIX_FILE}" current_target_login_feature_smoke_classes
  )"
fi

if [[ -n "${HOME_FEATURE_SMOKE_TEST_CLASSES:-}" ]]; then
  HOME_FEATURE_SMOKE_CLASSES="${HOME_FEATURE_SMOKE_TEST_CLASSES}"
elif [[ -n "${PLATFORM_API}" && "${PLATFORM_API}" != "$(read_target_readiness_value "${MATRIX_FILE}" current_target_api)" ]]; then
  HOME_FEATURE_SMOKE_CLASSES="$(
    read_target_readiness_value "${MATRIX_FILE}" candidate_target_home_feature_smoke_classes
  )"
else
  HOME_FEATURE_SMOKE_CLASSES="$(
    read_target_readiness_value "${MATRIX_FILE}" current_target_home_feature_smoke_classes
  )"
fi

if [[ -z "${SMOKE_CLASSES}" ]]; then
  echo "Failed to resolve smoke classes from ${MATRIX_FILE}" >&2
  exit 1
fi
if [[ -z "${LOGIN_FEATURE_SMOKE_CLASSES}" ]]; then
  echo "Failed to resolve login feature smoke classes from ${MATRIX_FILE}" >&2
  exit 1
fi
if [[ -z "${HOME_FEATURE_SMOKE_CLASSES}" ]]; then
  echo "Failed to resolve Home feature smoke classes from ${MATRIX_FILE}" >&2
  exit 1
fi

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
  read_android_settings_release "${SETTINGS_FILE}" "targetSdk"
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
    if [ "${sdk}" = "${EMULATOR_API}" ] && [ "${boot}" = "1" ]; then
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

  AVD_NAME="$(echo "${listed}" | grep -E "API_${EMULATOR_API}(\.|_|$)" | head -n1 || true)"
  if [ -z "${AVD_NAME}" ]; then
    echo "No AVD matched platform API ${EMULATOR_API}. Set TARGET_SDK_AVD explicitly." >&2
    echo "Available AVDs:" >&2
    echo "${listed}" >&2
    exit 1
  fi
}

start_target_emulator() {
  resolve_avd_name
  echo "Starting emulator AVD=${AVD_NAME} for platform API ${EMULATOR_API}..."
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

if [ ! -f "${SETTINGS_FILE}" ]; then
  echo "Settings file not found: ${SETTINGS_FILE}" >&2
  exit 1
fi

ADB_BIN="$(resolve_bin "${ADB_BIN}" "$(command -v adb || true)" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" "${ANDROID_HOME:-}/platform-tools/adb" "${HOME}/Library/Android/sdk/platform-tools/adb")"
EMULATOR_BIN="$(resolve_bin "${EMULATOR_BIN}" "$(command -v emulator || true)" "${ANDROID_SDK_ROOT:-}/emulator/emulator" "${ANDROID_HOME:-}/emulator/emulator" "${HOME}/Library/Android/sdk/emulator/emulator")"
TARGET_SDK="$(extract_target_sdk)"
EMULATOR_API="${PLATFORM_API:-${TARGET_SDK}}"

if [ -z "${TARGET_SDK}" ]; then
  echo "Failed to parse targetSdk from ${SETTINGS_FILE}" >&2
  exit 1
fi
if [[ ! "${EMULATOR_API}" =~ ^[0-9]+$ ]]; then
  echo "Invalid platform API: ${EMULATOR_API}" >&2
  exit 1
fi

echo "Using adb: ${ADB_BIN}"
echo "Using emulator: ${EMULATOR_BIN}"
echo "Target SDK: ${TARGET_SDK}"
echo "Emulator API: ${EMULATOR_API}"

"${ADB_BIN}" start-server >/dev/null

TARGET_SERIAL="$(find_ready_target_serial || true)"
if [ -z "${TARGET_SERIAL}" ]; then
  if [ -n "$(list_emulator_serials)" ]; then
    echo "No ready emulator with API ${EMULATOR_API} found among running emulators:" >&2
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
    echo "Timed out waiting for API ${EMULATOR_API} emulator to boot." >&2
    echo "Recent emulator log:" >&2
    tail -n 80 /tmp/target_sdk_local_smoke_emulator.log >&2 || true
    exit 1
  fi
fi

echo "Using emulator serial: ${TARGET_SERIAL}"

cd "${ROOT_DIR}"
./gradlew --no-daemon \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :feature:home:assembleDebugAndroidTest \
  :feature:login:assembleDebugAndroidTest \
  -Pbaseline.enableX86_64=true

ADB_BIN="${ADB_BIN}" \
SMOKE_DEVICE_SERIAL="${TARGET_SERIAL}" \
SMOKE_READY_TIMEOUT_SECS="${READY_TIMEOUT_SECS}" \
SMOKE_TEST_CLASSES="${SMOKE_CLASSES}" \
bash .github/scripts/run-instrumentation-smoke.sh

ANDROID_SERIAL="${TARGET_SERIAL}" \
./gradlew --no-daemon :feature:home:connectedDebugAndroidTest \
  -Pbaseline.enableX86_64=true \
  -Pandroid.testInstrumentationRunnerArguments.class="${HOME_FEATURE_SMOKE_CLASSES}"

ANDROID_SERIAL="${TARGET_SERIAL}" \
./gradlew --no-daemon :feature:login:connectedDebugAndroidTest \
  -Pbaseline.enableX86_64=true \
  -Pandroid.testInstrumentationRunnerArguments.class="${LOGIN_FEATURE_SMOKE_CLASSES}"

echo "Local target SDK smoke verification passed."
