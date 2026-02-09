#!/usr/bin/env bash
set -euo pipefail

ensure_device_ready() {
  local i=1
  while [ "$i" -le 12 ]; do
    local api_level
    api_level="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    if echo "${api_level}" | grep -Eq '^[0-9]+$'; then
      echo "Device API level: ${api_level}"
      return 0
    fi

    echo "Device API level unavailable (attempt ${i}/12), restarting adb..."
    adb kill-server || true
    adb start-server
    adb wait-for-device
    sleep 5
    i=$((i + 1))
  done

  echo "Device never reported a valid API level."
  adb devices -l || true
  return 1
}

run_smoke_test() {
  ./gradlew --no-daemon :app:connectedDebugAndroidTest \
    -Pbaseline.enableX86_64=true \
    -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.smoke.MainActivitySmokeTest
}

adb start-server
adb wait-for-device
ensure_device_ready

if ! run_smoke_test; then
  echo "Smoke test failed once. Restarting adb and retrying..."
  adb kill-server || true
  adb start-server
  adb wait-for-device
  ensure_device_ready
  run_smoke_test
fi
