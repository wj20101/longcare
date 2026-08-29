#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
POLICY_FILE=""
MANIFEST_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --policy)
      POLICY_FILE="${2:-}"
      shift 2
      ;;
    --manifest)
      MANIFEST_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: verify_target_sdk_readiness.sh [--project-root <path>] [--policy <path>] [--manifest <path>]"
      exit 0
      ;;
    *)
      echo "[target-sdk-readiness][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
POLICY_FILE="${POLICY_FILE:-${PROJECT_ROOT}/scripts/quality/target_sdk_readiness.properties}"
MANIFEST_FILE="${MANIFEST_FILE:-${PROJECT_ROOT}/app/src/main/AndroidManifest.xml}"
[[ "${POLICY_FILE}" == /* ]] || POLICY_FILE="${PROJECT_ROOT}/${POLICY_FILE}"
[[ "${MANIFEST_FILE}" == /* ]] || MANIFEST_FILE="${PROJECT_ROOT}/${MANIFEST_FILE}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/quality/target_readiness_values.sh
source "${SCRIPT_DIR}/target_readiness_values.sh"

ERRORS=()
fail() { ERRORS+=("$1"); }

[[ -f "${POLICY_FILE}" ]] || fail "policy file is missing: ${POLICY_FILE}"
[[ -f "${MANIFEST_FILE}" ]] || fail "manifest file is missing: ${MANIFEST_FILE}"

REQUIRED_KEYS=(
  approved_target_sdk
  candidate_target_sdk
  candidate_platform_channel
  candidate_promotion
  platform_behavior_status
  vendor_compatibility_status
  adaptive_compatibility_status
  test_matrix_status
  candidate_change_id
)

if [[ -f "${POLICY_FILE}" ]]; then
  while IFS= read -r raw_line; do
    [[ "${raw_line}" =~ ^[[:space:]]*$ || "${raw_line}" =~ ^[[:space:]]*# ]] && continue
    if [[ "${raw_line}" != *=* ]]; then
      fail "malformed policy line: ${raw_line}"
      continue
    fi
    key="${raw_line%%=*}"
    key="$(printf '%s' "${key}" | tr -d '[:space:]')"
    case "${key}" in
      approved_target_sdk|candidate_target_sdk|candidate_platform_channel|candidate_promotion|platform_behavior_status|vendor_compatibility_status|adaptive_compatibility_status|test_matrix_status|candidate_change_id) ;;
      *) fail "unknown policy field: ${key}" ;;
    esac
  done < "${POLICY_FILE}"

  for key in "${REQUIRED_KEYS[@]}"; do
    count="$(grep -Ec "^[[:space:]]*${key}[[:space:]]*=" "${POLICY_FILE}" || true)"
    if [[ "${count}" -ne 1 ]]; then
      fail "policy field ${key} must appear exactly once (found ${count})"
    fi
  done

  approved="$(read_target_readiness_value "${POLICY_FILE}" approved_target_sdk)"
  candidate="$(read_target_readiness_value "${POLICY_FILE}" candidate_target_sdk)"
  channel="$(read_target_readiness_value "${POLICY_FILE}" candidate_platform_channel)"
  promotion="$(read_target_readiness_value "${POLICY_FILE}" candidate_promotion)"
  platform_status="$(read_target_readiness_value "${POLICY_FILE}" platform_behavior_status)"
  vendor_status="$(read_target_readiness_value "${POLICY_FILE}" vendor_compatibility_status)"
  adaptive_status="$(read_target_readiness_value "${POLICY_FILE}" adaptive_compatibility_status)"
  matrix_status="$(read_target_readiness_value "${POLICY_FILE}" test_matrix_status)"
  change_id="$(read_target_readiness_value "${POLICY_FILE}" candidate_change_id)"

  [[ "${approved}" =~ ^[0-9]+$ ]] || fail "approved_target_sdk must be numeric"
  [[ "${candidate}" =~ ^[0-9]+$ ]] || fail "candidate_target_sdk must be numeric"
  if [[ "${approved}" =~ ^[0-9]+$ && "${candidate}" =~ ^[0-9]+$ ]] && (( candidate <= approved )); then
    fail "candidate_target_sdk(${candidate}) must be greater than approved_target_sdk(${approved})"
  fi
  [[ "${channel}" == "beta" || "${channel}" == "stable" ]] || fail "illegal candidate_platform_channel=${channel}"
  [[ "${promotion}" == "blocked" || "${promotion}" == "approved" ]] || fail "illegal candidate_promotion=${promotion}"

  for status_pair in \
    "platform_behavior_status:${platform_status}" \
    "vendor_compatibility_status:${vendor_status}" \
    "adaptive_compatibility_status:${adaptive_status}" \
    "test_matrix_status:${matrix_status}"; do
    status_key="${status_pair%%:*}"
    status_value="${status_pair#*:}"
    if [[ "${status_value}" != "unverified" && "${status_value}" != "blocked" && "${status_value}" != "verified" ]]; then
      fail "illegal ${status_key}=${status_value}"
    fi
  done

  has_adaptive_manifest_constraint="false"
  if [[ -f "${MANIFEST_FILE}" ]] && {
    grep -Fq 'android:screenOrientation=' "${MANIFEST_FILE}" ||
      grep -Fq 'android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY' "${MANIFEST_FILE}"
  }; then
    has_adaptive_manifest_constraint="true"
  fi

  if [[ "${adaptive_status}" == "verified" && "${has_adaptive_manifest_constraint}" == "true" ]]; then
    fail "adaptive_compatibility_status cannot be verified while production activities retain fixed orientation or restricted-resizability compatibility"
  fi

  if [[ "${promotion}" == "approved" ]]; then
    [[ "${channel}" == "stable" ]] || fail "candidate_promotion=approved requires candidate_platform_channel=stable"
    [[ "${platform_status}" == "verified" ]] || fail "candidate_promotion=approved requires platform_behavior_status=verified"
    [[ "${vendor_status}" == "verified" ]] || fail "candidate_promotion=approved requires vendor_compatibility_status=verified"
    [[ "${adaptive_status}" == "verified" ]] || fail "candidate_promotion=approved requires adaptive_compatibility_status=verified"
    [[ "${matrix_status}" == "verified" ]] || fail "candidate_promotion=approved requires test_matrix_status=verified"
    if [[ ! "${change_id}" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
      fail "candidate_promotion=approved requires a valid candidate_change_id"
    elif [[ ! -d "${PROJECT_ROOT}/openspec/changes/${change_id}" ]]; then
      fail "candidate_change_id=${change_id} does not resolve to an OpenSpec change"
    fi
  elif [[ -n "${change_id}" ]]; then
    fail "candidate_change_id must remain empty while candidate_promotion=blocked"
  fi
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[target-sdk-readiness][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[target-sdk-readiness][PASS] readiness policy is internally coherent."
echo "  - approvedTarget=${approved}"
echo "  - candidateTarget=${candidate}"
echo "  - platformChannel=${channel}"
echo "  - promotion=${promotion}"
echo "  - adaptiveManifestConstraint=${has_adaptive_manifest_constraint}"
