#!/usr/bin/env bash
set -euo pipefail

READY_TIMEOUT_SECS="${SMOKE_READY_TIMEOUT_SECS:-360}"

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

adb start-server
adb wait-for-device
ensure_device_ready

./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pbaseline.enableX86_64=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.smoke.MainActivitySmokeTest \
  --stacktrace
