#!/usr/bin/env bash
set -euo pipefail

CONSTANTS_FILE="${1:-constants.gradle.kts}"
CI_WORKFLOW_FILE="${2:-.github/workflows/android-ci.yml}"

if [[ ! -f "${CONSTANTS_FILE}" ]]; then
  echo "Constants file not found: ${CONSTANTS_FILE}" >&2
  exit 1
fi

if [[ ! -f "${CI_WORKFLOW_FILE}" ]]; then
  echo "CI workflow file not found: ${CI_WORKFLOW_FILE}" >&2
  exit 1
fi

extract_sdk_value() {
  local key="$1"
  sed -nE "s/.*${key} by extra\\(([0-9]+)\\).*/\\1/p" "${CONSTANTS_FILE}" | head -n1
}

compile_sdk="$(extract_sdk_value "appCompileSdkVersion")"
target_sdk="$(extract_sdk_value "appTargetSdkVersion")"
min_sdk="$(extract_sdk_value "appMinSdkVersion")"

if [[ -z "${compile_sdk}" || -z "${target_sdk}" || -z "${min_sdk}" ]]; then
  echo "Failed to parse SDK versions from ${CONSTANTS_FILE}" >&2
  exit 1
fi

if (( min_sdk > target_sdk )); then
  echo "Invalid SDK config: minSdk(${min_sdk}) > targetSdk(${target_sdk})" >&2
  exit 1
fi

if (( target_sdk > compile_sdk )); then
  echo "Invalid SDK config: targetSdk(${target_sdk}) > compileSdk(${compile_sdk})" >&2
  exit 1
fi

ci_api_levels="$(sed -nE 's/^[[:space:]]*api-level:[[:space:]]*([0-9]+).*/\1/p' "${CI_WORKFLOW_FILE}" || true)"
has_dynamic_target_api="false"
if grep -Eq '^[[:space:]]*api-level:[[:space:]]*"?\$\{\{[[:space:]]*steps\.[A-Za-z0-9_-]+\.outputs\.[A-Za-z0-9_-]+[[:space:]]*\}\}"?' "${CI_WORKFLOW_FILE}"; then
  has_dynamic_target_api="true"
fi

max_ci_api=""
if [[ -n "${ci_api_levels}" ]]; then
  max_ci_api="$(echo "${ci_api_levels}" | sort -n | tail -n1)"
fi

if [[ "${has_dynamic_target_api}" == "true" ]]; then
  if [[ -z "${max_ci_api}" || "${target_sdk}" -gt "${max_ci_api}" ]]; then
    max_ci_api="${target_sdk}"
  fi
fi

if [[ -z "${max_ci_api}" ]]; then
  echo "No emulator api-level found in ${CI_WORKFLOW_FILE}" >&2
  exit 1
fi

if (( max_ci_api < target_sdk )); then
  echo "Target SDK upgrade gate failed: max emulator API in CI is ${max_ci_api}, but targetSdk is ${target_sdk}." >&2
  echo "Please update emulator api-level in ${CI_WORKFLOW_FILE} to >= ${target_sdk}." >&2
  exit 1
fi

echo "Target SDK gate passed."
echo "  - minSdk=${min_sdk}"
echo "  - targetSdk=${target_sdk}"
echo "  - compileSdk=${compile_sdk}"
echo "  - maxCiEmulatorApi=${max_ci_api}"
