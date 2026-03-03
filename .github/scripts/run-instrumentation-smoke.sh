#!/usr/bin/env bash
set -euo pipefail

READY_TIMEOUT_SECS="${SMOKE_READY_TIMEOUT_SECS:-360}"
POST_BOOT_STABILIZATION_SECS="${SMOKE_POST_BOOT_STABILIZATION_SECS:-180}"
ADB_INSTALL_RETRY_COUNT="${SMOKE_ADB_INSTALL_RETRY_COUNT:-3}"
ADB_INSTALL_RETRY_DELAY_SECS="${SMOKE_ADB_INSTALL_RETRY_DELAY_SECS:-8}"
INSTRUMENTATION_RETRY_COUNT="${SMOKE_INSTRUMENTATION_RETRY_COUNT:-2}"
LOGCAT_MAX_LINES="${SMOKE_LOGCAT_MAX_LINES:-400}"
SMOKE_REPORT_DIR="${SMOKE_REPORT_DIR:-app/build/reports/androidTests/smoke}"
SMOKE_REPORT_FILE="${SMOKE_REPORT_FILE:-${SMOKE_REPORT_DIR}/instrumentation-smoke-output.txt}"
SMOKE_TEST_CLASS="${SMOKE_TEST_CLASS:-com.ytone.longcare.smoke.MainActivitySmokeTest}"
SMOKE_TEST_CLASSES="${SMOKE_TEST_CLASSES:-${SMOKE_TEST_CLASS}}"
SMOKE_TEST_CLASSES_FILE="${SMOKE_TEST_CLASSES_FILE:-}"
APP_ID="${APP_ID:-com.ytone.longcare}"
TARGET_SERIAL="${ANDROID_SERIAL:-${SMOKE_DEVICE_SERIAL:-}}"
ADB_BIN="${ADB_BIN:-}"

resolve_adb_bin() {
  if [ -n "${ADB_BIN}" ] && [ -x "${ADB_BIN}" ]; then
    return 0
  fi

  local command_adb
  command_adb="$(command -v adb || true)"
  local candidates=(
    "${command_adb}"
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
    "${ANDROID_HOME:-}/platform-tools/adb"
    "${HOME}/Library/Android/sdk/platform-tools/adb"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [ -n "${candidate}" ] && [ -x "${candidate}" ]; then
      ADB_BIN="${candidate}"
      return 0
    fi
  done

  echo "Unable to find adb. Set ADB_BIN or ensure platform-tools/adb is available." >&2
  exit 1
}

resolve_target_serial() {
  if [ -n "${TARGET_SERIAL}" ]; then
    return 0
  fi

  local emulators
  emulators="$("${ADB_BIN}" devices | sed -nE 's/^(emulator-[0-9]+)[[:space:]]+device$/\1/p')"
  if [ -n "${emulators}" ]; then
    TARGET_SERIAL="$(echo "${emulators}" | head -n1)"
    return 0
  fi

  local devices
  devices="$("${ADB_BIN}" devices | sed -nE 's/^([[:alnum:]_.:-]+)[[:space:]]+device$/\1/p')"
  local count
  count="$(printf '%s\n' "${devices}" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "${count}" = "1" ]; then
    TARGET_SERIAL="$(printf '%s\n' "${devices}" | sed '/^$/d' | head -n1)"
  fi
}

adb_cmd() {
  if [ -n "${TARGET_SERIAL}" ]; then
    "${ADB_BIN}" -s "${TARGET_SERIAL}" "$@"
  else
    "${ADB_BIN}" "$@"
  fi
}

ensure_device_ready() {
  local elapsed=0
  while [ "${elapsed}" -lt "${READY_TIMEOUT_SECS}" ]; do
    local api_level
    local boot_completed
    api_level="$(adb_cmd shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    boot_completed="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"

    if echo "${api_level}" | grep -Eq '^[0-9]+$' &&
      [ "${boot_completed}" = "1" ] &&
      adb_cmd shell cmd package list packages >/dev/null 2>&1 &&
      adb_cmd shell settings get global device_name >/dev/null 2>&1; then
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
  "${ADB_BIN}" devices -l || true
  adb_cmd shell getprop || true
  return 1
}

wait_for_post_boot_stabilization() {
  if [ "${POST_BOOT_STABILIZATION_SECS}" -le 0 ]; then
    return 0
  fi

  echo "Waiting ${POST_BOOT_STABILIZATION_SECS}s for post-boot stabilization..."
  sleep "${POST_BOOT_STABILIZATION_SECS}"
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

  adb_cmd uninstall "${APP_ID}.test" >/dev/null 2>&1 || true
  adb_cmd uninstall "${APP_ID}" >/dev/null 2>&1 || true

  adb_install_with_retry "app APK" install -r -d "${app_apk}"
  adb_install_with_retry "test APK" install -r -d -t "${test_apk}"
}

adb_install_with_retry() {
  local artifact_label="$1"
  shift

  local attempt=1
  local output=""
  while [ "${attempt}" -le "${ADB_INSTALL_RETRY_COUNT}" ]; do
    echo "Installing ${artifact_label} (attempt ${attempt}/${ADB_INSTALL_RETRY_COUNT})..."
    if output="$(adb_cmd "$@" 2>&1)"; then
      if [ -n "${output}" ]; then
        echo "${output}"
      fi
      return 0
    fi

    if [ -n "${output}" ]; then
      echo "${output}" >&2
    fi

    if [ "${attempt}" -ge "${ADB_INSTALL_RETRY_COUNT}" ]; then
      echo "Failed to install ${artifact_label} after ${ADB_INSTALL_RETRY_COUNT} attempts." >&2
      return 1
    fi

    echo "Install attempt ${attempt} failed. Restarting adb connection and retrying in ${ADB_INSTALL_RETRY_DELAY_SECS}s..."
    "${ADB_BIN}" kill-server >/dev/null 2>&1 || true
    "${ADB_BIN}" start-server >/dev/null 2>&1 || true
    if [ -n "${TARGET_SERIAL}" ]; then
      adb_cmd wait-for-device || true
    else
      "${ADB_BIN}" wait-for-device || true
    fi
    ensure_device_ready || true
    sleep "${ADB_INSTALL_RETRY_DELAY_SECS}"
    attempt=$((attempt + 1))
  done

  return 1
}

run_single_instrumentation() {
  local instrumentation="$1"
  local test_class="$2"
  local attempt=1
  local output_tmp
  local logcat_file

  while [ "${attempt}" -le "${INSTRUMENTATION_RETRY_COUNT}" ]; do
    output_tmp="$(mktemp)"
    logcat_file="${SMOKE_REPORT_DIR}/logcat-${test_class//[^[:alnum:]_.-]/_}-attempt${attempt}.txt"

    echo "Instrumentation attempt ${attempt}/${INSTRUMENTATION_RETRY_COUNT} for ${test_class}" | tee -a "${SMOKE_REPORT_FILE}"
    adb_cmd logcat -c >/dev/null 2>&1 || true

    # Keep both console output and a persisted artifact for debugging.
    adb_cmd shell am instrument -w -r \
      -e class "${test_class}" \
      "${instrumentation}" | tee -a "${SMOKE_REPORT_FILE}" | tee "${output_tmp}"

    if grep -q "FAILURES!!!" "${output_tmp}" ||
      grep -q "INSTRUMENTATION_STATUS_CODE: -2" "${output_tmp}" ||
      grep -q "INSTRUMENTATION_RESULT: shortMsg=" "${output_tmp}"; then
      echo "Instrumentation test failed on attempt ${attempt}: ${test_class}"
      adb_cmd logcat -d -t "${LOGCAT_MAX_LINES}" | tee "${logcat_file}" | tee -a "${SMOKE_REPORT_FILE}" >/dev/null || true

      if grep -q "INSTRUMENTATION_RESULT: shortMsg=Process crashed." "${output_tmp}" &&
        [ "${attempt}" -lt "${INSTRUMENTATION_RETRY_COUNT}" ]; then
        echo "Detected process crash. Retrying class ${test_class} after force-stop..." | tee -a "${SMOKE_REPORT_FILE}"
        adb_cmd shell am force-stop "${APP_ID}" >/dev/null 2>&1 || true
        sleep 3
        rm -f "${output_tmp}"
        attempt=$((attempt + 1))
        continue
      fi

      rm -f "${output_tmp}"
      return 1
    fi

    if ! grep -q "OK (" "${output_tmp}"; then
      echo "Instrumentation result for ${test_class} did not contain an OK marker."
      adb_cmd logcat -d -t "${LOGCAT_MAX_LINES}" | tee "${logcat_file}" | tee -a "${SMOKE_REPORT_FILE}" >/dev/null || true
      rm -f "${output_tmp}"
      return 1
    fi

    rm -f "${output_tmp}"
    return 0
  done

  echo "Instrumentation test exhausted retries: ${test_class}"
  return 1
}

run_instrumentation() {
  local instrumentation
  local classes_payload
  instrumentation="$(adb_cmd shell pm list instrumentation | tr -d '\r' | grep "${APP_ID}" | head -n 1 | sed -E 's/^instrumentation:([^ ]+) .*/\1/')"
  if [ -z "${instrumentation}" ]; then
    echo "Unable to resolve instrumentation target for ${APP_ID}."
    adb_cmd shell pm list instrumentation || true
    return 1
  fi

  echo "Running instrumentation target: ${instrumentation}"
  mkdir -p "${SMOKE_REPORT_DIR}"
  : > "${SMOKE_REPORT_FILE}"

  classes_payload="${SMOKE_TEST_CLASSES}"
  if [ -n "${SMOKE_TEST_CLASSES_FILE}" ] && [ -f "${SMOKE_TEST_CLASSES_FILE}" ]; then
    classes_payload="$(paste -sd',' "${SMOKE_TEST_CLASSES_FILE}")"
  fi
  echo "Smoke test classes: ${classes_payload}"

  IFS=',' read -r -a classes <<< "${classes_payload}"
  for raw_class in "${classes[@]}"; do
    test_class="$(echo "${raw_class}" | xargs)"
    if [ -z "${test_class}" ]; then
      continue
    fi
    echo "Running class: ${test_class}"
    run_single_instrumentation "${instrumentation}" "${test_class}"
  done
}

resolve_adb_bin
echo "Using adb binary: ${ADB_BIN}"
"${ADB_BIN}" start-server
resolve_target_serial
if [ -n "${TARGET_SERIAL}" ]; then
  echo "Using adb target serial: ${TARGET_SERIAL}"
  adb_cmd wait-for-device
else
  "${ADB_BIN}" wait-for-device
fi
ensure_device_ready

wait_for_post_boot_stabilization

install_apks
run_instrumentation
