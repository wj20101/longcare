#!/usr/bin/env bash
set -euo pipefail

READY_TIMEOUT_SECS="${SMOKE_READY_TIMEOUT_SECS:-360}"
SMOKE_REPORT_DIR="${SMOKE_REPORT_DIR:-app/build/reports/androidTests/smoke}"
SMOKE_REPORT_FILE="${SMOKE_REPORT_FILE:-${SMOKE_REPORT_DIR}/instrumentation-smoke-output.txt}"
SMOKE_TEST_CLASS="${SMOKE_TEST_CLASS:-com.ytone.longcare.smoke.MainActivitySmokeTest}"
APP_ID="${APP_ID:-com.ytone.longcare}"

ensure_device_ready() {
  local elapsed=0
  while [ "${elapsed}" -lt "${READY_TIMEOUT_SECS}" ]; do
    local api_level
    local boot_completed
    api_level="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"

    if echo "${api_level}" | grep -Eq '^[0-9]+$' &&
      [ "${boot_completed}" = "1" ] &&
      adb shell cmd package list packages >/dev/null 2>&1 &&
      adb shell settings get global device_name >/dev/null 2>&1; then
      echo "Device is ready (API ${api_level}, boot_completed=${boot_completed})."
      return 0
    fi

    if [ $((elapsed % 30)) -eq 0 ]; then
      echo "Waiting for device readiness... ${elapsed}s/${READY_TIMEOUT_SECS}s"
    fi

    sleep 5
    elapsed=$((elapsed + 5))
  done

  echo "Device did not become ready in ${READY_TIMEOUT_SECS}s."
  adb devices -l || true
  adb shell getprop || true
  return 1
}

install_apks() {
  local app_apk
  local test_apk
  if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    app_apk="app/build/outputs/apk/debug/app-debug.apk"
  else
    app_apk="$(find app/build/outputs/apk -type f -name "app-debug*.apk" ! -name "*androidTest*" | sort | head -n 1)"
  fi

  if [ -f "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" ]; then
    test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  else
    test_apk="$(find app/build/outputs/apk -type f -name "*androidTest*.apk" | sort | head -n 1)"
  fi

  if [ -z "${app_apk}" ] || [ ! -f "${app_apk}" ]; then
    echo "Unable to find debug app APK under app/build/outputs/apk."
    return 1
  fi
  if [ -z "${test_apk}" ] || [ ! -f "${test_apk}" ]; then
    echo "Unable to find debug androidTest APK under app/build/outputs/apk."
    return 1
  fi

  echo "Using app APK: ${app_apk}"
  echo "Using test APK: ${test_apk}"

  adb uninstall "${APP_ID}.test" >/dev/null 2>&1 || true
  adb uninstall "${APP_ID}" >/dev/null 2>&1 || true

  adb install -r -d "${app_apk}"
  adb install -r -d -t "${test_apk}"
}

run_instrumentation() {
  local instrumentation
  instrumentation="$(adb shell pm list instrumentation | tr -d '\r' | grep "${APP_ID}" | head -n 1 | sed -E 's/^instrumentation:([^ ]+) .*/\1/')"
  if [ -z "${instrumentation}" ]; then
    echo "Unable to resolve instrumentation target for ${APP_ID}."
    adb shell pm list instrumentation || true
    return 1
  fi

  echo "Running instrumentation: ${instrumentation}, class=${SMOKE_TEST_CLASS}"
  mkdir -p "${SMOKE_REPORT_DIR}"

  # Keep both console output and a persisted artifact for debugging.
  adb shell am instrument -w -r \
    -e class "${SMOKE_TEST_CLASS}" \
    "${instrumentation}" | tee "${SMOKE_REPORT_FILE}"
}

adb start-server
adb wait-for-device
ensure_device_ready

install_apks
run_instrumentation
