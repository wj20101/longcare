#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STARTUP_FILE="${ROOT_DIR}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/StartupBenchmarks.kt"
GENERATOR_FILE="${ROOT_DIR}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt"

for required_file in "${STARTUP_FILE}" "${GENERATOR_FILE}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "[baselineprofile][FAIL] missing file: ${required_file}" >&2
    exit 1
  fi
done

if grep -n "TODO" "${STARTUP_FILE}" "${GENERATOR_FILE}" >/dev/null; then
  echo "[baselineprofile][FAIL] Found TODO markers in baseline profile journeys." >&2
  grep -n "TODO" "${STARTUP_FILE}" "${GENERATOR_FILE}" >&2 || true
  exit 1
fi

if ! grep -q "Until\\.hasObject" "${STARTUP_FILE}"; then
  echo "[baselineprofile][FAIL] Startup benchmark must wait for app readiness (Until.hasObject)." >&2
  exit 1
fi

if ! grep -q "device\\.swipe" "${GENERATOR_FILE}"; then
  echo "[baselineprofile][FAIL] Baseline profile generator must include at least one swipe journey." >&2
  exit 1
fi

if ! grep -q "device\\.pressBack" "${GENERATOR_FILE}"; then
  echo "[baselineprofile][FAIL] Baseline profile generator must include at least one back-navigation journey." >&2
  exit 1
fi

echo "[baselineprofile][PASS] baseline profile journeys are non-template and include readiness + navigation interactions."
