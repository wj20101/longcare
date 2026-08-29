#!/usr/bin/env bash
set -euo pipefail

SETTINGS_FILE="${1:-settings.gradle.kts}"
CI_WORKFLOW_FILE="${2:-.github/workflows/android-ci.yml}"
POLICY_FILE="${3:-scripts/quality/target_sdk_readiness.properties}"
MANIFEST_FILE="${4:-app/src/main/AndroidManifest.xml}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/quality/android_build_values.sh
source "${SCRIPT_DIR}/android_build_values.sh"
# shellcheck source=scripts/quality/target_readiness_values.sh
source "${SCRIPT_DIR}/target_readiness_values.sh"

if [[ ! -f "${SETTINGS_FILE}" ]]; then
  echo "Settings file not found: ${SETTINGS_FILE}" >&2
  exit 1
fi

if [[ ! -f "${CI_WORKFLOW_FILE}" ]]; then
  echo "CI workflow file not found: ${CI_WORKFLOW_FILE}" >&2
  exit 1
fi

if [[ ! -f "${POLICY_FILE}" ]]; then
  echo "Readiness policy file not found: ${POLICY_FILE}" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${SETTINGS_FILE}")" && pwd)"
MATRIX_FILE="${5:-${project_root}/scripts/quality/target_platform_test_matrix.properties}"
readiness_status=0
bash "${SCRIPT_DIR}/verify_target_sdk_readiness.sh" \
  --project-root "${project_root}" \
  --policy "${POLICY_FILE}" \
  --manifest "${MANIFEST_FILE}" || readiness_status=$?
if [[ "${readiness_status}" -ne 0 ]]; then
  exit "${readiness_status}"
fi

compile_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "compileSdk")"
target_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "targetSdk")"
min_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "minSdk")"

if [[ -z "${compile_sdk}" || -z "${target_sdk}" || -z "${min_sdk}" ]]; then
  echo "Failed to parse SDK versions from ${SETTINGS_FILE}" >&2
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

approved_target="$(read_target_readiness_value "${POLICY_FILE}" approved_target_sdk)"
candidate_target="$(read_target_readiness_value "${POLICY_FILE}" candidate_target_sdk)"
platform_channel="$(read_target_readiness_value "${POLICY_FILE}" candidate_platform_channel)"
candidate_promotion="$(read_target_readiness_value "${POLICY_FILE}" candidate_promotion)"
platform_status="$(read_target_readiness_value "${POLICY_FILE}" platform_behavior_status)"
vendor_status="$(read_target_readiness_value "${POLICY_FILE}" vendor_compatibility_status)"
adaptive_status="$(read_target_readiness_value "${POLICY_FILE}" adaptive_compatibility_status)"
matrix_status="$(read_target_readiness_value "${POLICY_FILE}" test_matrix_status)"
candidate_change_id="$(read_target_readiness_value "${POLICY_FILE}" candidate_change_id)"

if (( target_sdk < approved_target )); then
  echo "Target SDK gate failed: targetSdk=${target_sdk} regresses below approved target ${approved_target}." >&2
  exit 1
fi

if (( target_sdk > approved_target )); then
  upgrade_errors=()
  [[ "${target_sdk}" == "${candidate_target}" ]] || upgrade_errors+=("targetSdk=${target_sdk} does not match candidate_target_sdk=${candidate_target}")
  [[ "${platform_channel}" == "stable" ]] || upgrade_errors+=("candidate_platform_channel must be stable (current=${platform_channel})")
  [[ "${candidate_promotion}" == "approved" ]] || upgrade_errors+=("candidate_promotion must be approved (current=${candidate_promotion})")
  [[ "${platform_status}" == "verified" ]] || upgrade_errors+=("platform_behavior_status must be verified (current=${platform_status})")
  [[ "${vendor_status}" == "verified" ]] || upgrade_errors+=("vendor_compatibility_status must be verified (current=${vendor_status})")
  [[ "${adaptive_status}" == "verified" ]] || upgrade_errors+=("adaptive_compatibility_status must be verified (current=${adaptive_status})")
  [[ "${matrix_status}" == "verified" ]] || upgrade_errors+=("test_matrix_status must be verified (current=${matrix_status})")
  [[ -n "${candidate_change_id}" ]] || upgrade_errors+=("candidate_change_id must identify an independent OpenSpec target upgrade change")
  if [[ ${#upgrade_errors[@]} -gt 0 ]]; then
    echo "Target SDK promotion gate failed for targetSdk=${target_sdk}:" >&2
    for error in "${upgrade_errors[@]}"; do
      echo "  - ${error}" >&2
    done
    exit 1
  fi
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

# Gradle Managed Devices are declared in the Android build and invoked by task
# name in CI, so there may be no runner-level `api-level` YAML field to parse.
# Count only the blocking current-target lane; the API 37 readiness lane is
# intentionally non-blocking and must not make the approved target look covered.
if [[ -f "${MATRIX_FILE}" ]]; then
  matrix_target_api="$(read_target_readiness_value "${MATRIX_FILE}" current_target_api)"
  matrix_target_device="$(read_target_readiness_value "${MATRIX_FILE}" current_target_device)"
  matrix_target_blocking="$(read_target_readiness_value "${MATRIX_FILE}" current_target_blocking)"

  if [[ "${matrix_target_blocking}" == "true" ]]; then
    if [[ ! "${matrix_target_api}" =~ ^[0-9]+$ || -z "${matrix_target_device}" ]]; then
      echo "Target SDK upgrade gate failed: invalid current-target GMD configuration in ${MATRIX_FILE}." >&2
      exit 1
    fi
    if ! grep -Fq ":${matrix_target_device}DebugAndroidTest" "${CI_WORKFLOW_FILE}"; then
      echo "Target SDK upgrade gate failed: blocking GMD :${matrix_target_device}DebugAndroidTest is not invoked by ${CI_WORKFLOW_FILE}." >&2
      exit 1
    fi
    if [[ -z "${max_ci_api}" || "${matrix_target_api}" -gt "${max_ci_api}" ]]; then
      max_ci_api="${matrix_target_api}"
    fi
  fi
fi

if [[ -z "${max_ci_api}" ]]; then
  echo "Target SDK gate passed."
  echo "  - minSdk=${min_sdk}"
  echo "  - targetSdk=${target_sdk}"
  echo "  - compileSdk=${compile_sdk}"
  echo "  - maxCiEmulatorApi=<none>"
  echo "  - emulatorApiCheck=skipped; no emulator api-level is configured in ${CI_WORKFLOW_FILE}"
  exit 0
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
