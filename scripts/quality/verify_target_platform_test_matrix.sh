#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
MATRIX_FILE=""
POLICY_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root) PROJECT_ROOT="${2:-}"; shift 2 ;;
    --matrix) MATRIX_FILE="${2:-}"; shift 2 ;;
    --policy) POLICY_FILE="${2:-}"; shift 2 ;;
    -h|--help)
      echo "Usage: verify_target_platform_test_matrix.sh [--project-root <path>] [--matrix <path>] [--policy <path>]"
      exit 0
      ;;
    *) echo "[target-platform-test-matrix][FAIL] unknown argument: $1" >&2; exit 1 ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
MATRIX_FILE="${MATRIX_FILE:-${PROJECT_ROOT}/scripts/quality/target_platform_test_matrix.properties}"
POLICY_FILE="${POLICY_FILE:-${PROJECT_ROOT}/scripts/quality/target_sdk_readiness.properties}"
[[ "${MATRIX_FILE}" == /* ]] || MATRIX_FILE="${PROJECT_ROOT}/${MATRIX_FILE}"
[[ "${POLICY_FILE}" == /* ]] || POLICY_FILE="${PROJECT_ROOT}/${POLICY_FILE}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/quality/target_readiness_values.sh
source "${SCRIPT_DIR}/target_readiness_values.sh"
# shellcheck source=scripts/quality/android_build_values.sh
source "${SCRIPT_DIR}/android_build_values.sh"

ERRORS=()
fail() { ERRORS+=("$1"); }

for required in "${MATRIX_FILE}" "${POLICY_FILE}" "${PROJECT_ROOT}/settings.gradle.kts" "${PROJECT_ROOT}/baselineprofile/build.gradle.kts" "${PROJECT_ROOT}/feature/login/build.gradle.kts"; do
  [[ -f "${required}" ]] || fail "required file is missing: ${required#"${PROJECT_ROOT}/"}"
done

REQUIRED_KEYS=(
  baseline_profile_api baseline_profile_device current_target_api current_target_blocking
  current_target_device current_target_smoke_classes current_target_login_feature_smoke_classes
  current_target_contract_tests
  release_device_evidence_required candidate_target_api candidate_target_blocking
  candidate_target_device candidate_smoke_classes candidate_target_login_feature_smoke_classes
  candidate_readiness_checks
)

if [[ -f "${MATRIX_FILE}" ]]; then
  for key in "${REQUIRED_KEYS[@]}"; do
    count="$(grep -Ec "^[[:space:]]*${key}[[:space:]]*=" "${MATRIX_FILE}" || true)"
    [[ "${count}" -eq 1 ]] || fail "matrix field ${key} must appear exactly once (found ${count})"
  done

  baseline_api="$(read_target_readiness_value "${MATRIX_FILE}" baseline_profile_api)"
  baseline_device="$(read_target_readiness_value "${MATRIX_FILE}" baseline_profile_device)"
  current_api="$(read_target_readiness_value "${MATRIX_FILE}" current_target_api)"
  current_blocking="$(read_target_readiness_value "${MATRIX_FILE}" current_target_blocking)"
  current_device="$(read_target_readiness_value "${MATRIX_FILE}" current_target_device)"
  smoke_classes="$(read_target_readiness_value "${MATRIX_FILE}" current_target_smoke_classes)"
  login_feature_classes="$(read_target_readiness_value "${MATRIX_FILE}" current_target_login_feature_smoke_classes)"
  contract_tests="$(read_target_readiness_value "${MATRIX_FILE}" current_target_contract_tests)"
  release_evidence="$(read_target_readiness_value "${MATRIX_FILE}" release_device_evidence_required)"
  candidate_api="$(read_target_readiness_value "${MATRIX_FILE}" candidate_target_api)"
  candidate_blocking="$(read_target_readiness_value "${MATRIX_FILE}" candidate_target_blocking)"
  candidate_device="$(read_target_readiness_value "${MATRIX_FILE}" candidate_target_device)"
  candidate_classes="$(read_target_readiness_value "${MATRIX_FILE}" candidate_smoke_classes)"
  candidate_login_feature_classes="$(read_target_readiness_value "${MATRIX_FILE}" candidate_target_login_feature_smoke_classes)"
  candidate_checks="$(read_target_readiness_value "${MATRIX_FILE}" candidate_readiness_checks)"

  settings_target="$(read_android_settings_release "${PROJECT_ROOT}/settings.gradle.kts" targetSdk)"
  approved_target="$(read_target_readiness_value "${POLICY_FILE}" approved_target_sdk)"
  policy_candidate="$(read_target_readiness_value "${POLICY_FILE}" candidate_target_sdk)"
  policy_promotion="$(read_target_readiness_value "${POLICY_FILE}" candidate_promotion)"

  [[ "${baseline_api}" == "33" ]] || fail "baseline_profile_api must remain 33"
  grep -Fq "create(\"${baseline_device}\")" "${PROJECT_ROOT}/baselineprofile/build.gradle.kts" || fail "baseline profile device ${baseline_device} is not configured"
  grep -Eq "apiLevel[[:space:]]*=[[:space:]]*${baseline_api}([^0-9]|$)" "${PROJECT_ROOT}/baselineprofile/build.gradle.kts" || fail "baseline profile API ${baseline_api} is not configured"
  grep -Fq "create(\"${current_device}\")" "${PROJECT_ROOT}/feature/login/build.gradle.kts" || fail "login feature current-target device ${current_device} is not configured"
  grep -Fq "create(\"${candidate_device}\")" "${PROJECT_ROOT}/feature/login/build.gradle.kts" || fail "login feature candidate device ${candidate_device} is not configured"
  grep -Eq "apiLevel[[:space:]]*=[[:space:]]*${current_api}([^0-9]|$)" "${PROJECT_ROOT}/feature/login/build.gradle.kts" || fail "login feature current-target API ${current_api} is not configured"
  grep -Eq "apiLevel[[:space:]]*=[[:space:]]*${candidate_api}([^0-9]|$)" "${PROJECT_ROOT}/feature/login/build.gradle.kts" || fail "login feature candidate API ${candidate_api} is not configured"
  [[ "${current_api}" == "${settings_target}" && "${current_api}" == "${approved_target}" ]] || fail "current target matrix API ${current_api} must match settings/approved target ${settings_target}/${approved_target}"
  [[ "${candidate_api}" == "${policy_candidate}" ]] || fail "candidate matrix API ${candidate_api} must match policy candidate ${policy_candidate}"
  [[ "${current_blocking}" == "true" ]] || fail "current_target_blocking must be true"
  [[ "${candidate_blocking}" == "false" ]] || fail "candidate_target_blocking must be false while target 37 is a readiness lane"
  [[ "${policy_promotion}" == "blocked" ]] || fail "candidate readiness lane requires candidate_promotion=blocked until evidence is complete"
  [[ "${current_device}" != "${candidate_device}" && "${current_device}" != "${baseline_device}" && "${candidate_device}" != "${baseline_device}" ]] || fail "API 33/36/37 devices must be separately named"
  [[ -n "${smoke_classes}" && -n "${candidate_classes}" ]] || fail "current and candidate app smoke class sets must not be empty"
  [[ -n "${login_feature_classes}" && -n "${candidate_login_feature_classes}" ]] || fail "current and candidate login feature smoke class sets must not be empty"

  for required_check in adaptive-window message-queue-reflection-native local-network certificate-transparency-network background-alarm-audio vendor-sdk-startup; do
    case ",${candidate_checks}," in
      *",${required_check},"*) ;;
      *) fail "candidate_readiness_checks is missing ${required_check}" ;;
    esac
  done

  for required_evidence in nfc location camera sales qlz tencent-face; do
    case ",${release_evidence}," in
      *",${required_evidence},"*) ;;
      *) fail "release_device_evidence_required is missing ${required_evidence}" ;;
    esac
  done

  IFS=',' read -r -a contract_paths <<< "${contract_tests}"
  for contract_path in "${contract_paths[@]}"; do
    [[ -f "${PROJECT_ROOT}/${contract_path}" ]] || fail "automatic contract test is missing: ${contract_path}"
  done

  class_status=0
  bash "${SCRIPT_DIR}/verify_instrumentation_smoke_classes.sh" \
    --project-root "${PROJECT_ROOT}" \
    --owned-field "${MATRIX_FILE}" current_target_smoke_classes app/src/androidTest \
    --owned-field "${MATRIX_FILE}" candidate_smoke_classes app/src/androidTest \
    --owned-field "${MATRIX_FILE}" current_target_login_feature_smoke_classes feature/login/src/androidTest \
    --owned-field "${MATRIX_FILE}" candidate_target_login_feature_smoke_classes feature/login/src/androidTest || class_status=$?
  [[ "${class_status}" -eq 0 ]] || fail "one or more matrix instrumentation classes do not resolve"
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[target-platform-test-matrix][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[target-platform-test-matrix][PASS] API 33/36/37 validation lanes are distinct and complete."
