#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
OUTPUT_FILE="build/reports/api37-readiness/summary.md"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root) PROJECT_ROOT="${2:-}"; shift 2 ;;
    --output) OUTPUT_FILE="${2:-}"; shift 2 ;;
    -h|--help)
      echo "Usage: evaluate_api37_readiness.sh [--project-root <path>] [--output <path>]"
      exit 0
      ;;
    *) echo "[api37-readiness][FAIL] unknown argument: $1" >&2; exit 1 ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
[[ "${OUTPUT_FILE}" == /* ]] || OUTPUT_FILE="${PROJECT_ROOT}/${OUTPUT_FILE}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_FILE="${PROJECT_ROOT}/scripts/quality/target_sdk_readiness.properties"
MATRIX_FILE="${PROJECT_ROOT}/scripts/quality/target_platform_test_matrix.properties"

# shellcheck source=scripts/quality/target_readiness_values.sh
source "${SCRIPT_DIR}/target_readiness_values.sh"

bash "${SCRIPT_DIR}/verify_target_sdk_readiness.sh" --project-root "${PROJECT_ROOT}"
bash "${SCRIPT_DIR}/verify_target_platform_test_matrix.sh" --project-root "${PROJECT_ROOT}"

approved="$(read_target_readiness_value "${POLICY_FILE}" approved_target_sdk)"
candidate="$(read_target_readiness_value "${POLICY_FILE}" candidate_target_sdk)"
channel="$(read_target_readiness_value "${POLICY_FILE}" candidate_platform_channel)"
promotion="$(read_target_readiness_value "${POLICY_FILE}" candidate_promotion)"
platform="$(read_target_readiness_value "${POLICY_FILE}" platform_behavior_status)"
vendor="$(read_target_readiness_value "${POLICY_FILE}" vendor_compatibility_status)"
adaptive="$(read_target_readiness_value "${POLICY_FILE}" adaptive_compatibility_status)"
matrix="$(read_target_readiness_value "${POLICY_FILE}" test_matrix_status)"
checks="$(read_target_readiness_value "${MATRIX_FILE}" candidate_readiness_checks)"

manifest_constraint="absent"
if grep -Fq 'android:screenOrientation=' "${PROJECT_ROOT}/app/src/main/AndroidManifest.xml" || \
  grep -Fq 'android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY' "${PROJECT_ROOT}/app/src/main/AndroidManifest.xml"; then
  manifest_constraint="present"
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
{
  echo "# Android 17 / API 37 readiness"
  echo
  echo "- Approved production target: ${approved}"
  echo "- Candidate target: ${candidate}"
  echo "- Platform channel: ${channel}"
  echo "- Promotion: ${promotion}"
  echo "- Platform behavior: ${platform}"
  echo "- Vendor compatibility: ${vendor}"
  echo "- Adaptive compatibility: ${adaptive}"
  echo "- Test matrix: ${matrix}"
  echo "- Fixed-orientation/restricted-resizability declarations: ${manifest_constraint}"
  echo "- Readiness scope: ${checks}"
  echo
  echo "API 37 is a candidate-only lane. API 33 Profile and API 36 smoke results cannot promote it."
} > "${OUTPUT_FILE}"

if [[ "${channel}" != "stable" || "${promotion}" != "approved" || "${platform}" != "verified" || \
  "${vendor}" != "verified" || "${adaptive}" != "verified" || "${matrix}" != "verified" || \
  "${manifest_constraint}" != "absent" ]]; then
  echo "[api37-readiness][BLOCKED] candidate ${candidate} is not approved; see ${OUTPUT_FILE}" >&2
  exit 1
fi

echo "[api37-readiness][PASS] candidate ${candidate} has complete promotion evidence."
