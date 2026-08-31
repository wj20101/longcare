#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RETIREMENT_FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-retired-redundancy.XXXXXX")"

cleanup() {
  rm -rf -- "${RETIREMENT_FIXTURE_ROOT}"
}
trap cleanup EXIT

SCRIPTS=(
  scripts/quality/android_build_values.sh
  scripts/quality/test_affected_modules.sh
  scripts/quality/verify_architecture_boundaries.sh
  scripts/quality/verify_android_build_baseline.sh
  scripts/quality/test_android_build_baseline.sh
  scripts/quality/test_project_r8_rules.sh
  scripts/quality/verify_dependency_policy.sh
  scripts/quality/test_dependency_policy.sh
  scripts/quality/verify_instrumentation_smoke_classes.sh
  scripts/quality/test_instrumentation_smoke_classes.sh
  scripts/quality/verify_tech_stack_baseline.sh
  scripts/quality/test_tech_stack_baseline.sh
  scripts/quality/verify_target_sdk_upgrade.sh
  scripts/quality/test_target_sdk_upgrade.sh
  scripts/quality/target_readiness_values.sh
  scripts/quality/verify_target_sdk_readiness.sh
  scripts/quality/test_target_sdk_readiness.sh
  scripts/quality/verify_target_platform_test_matrix.sh
  scripts/quality/test_target_platform_test_matrix.sh
  scripts/quality/evaluate_api37_readiness.sh
)

for script in "${SCRIPTS[@]}"; do
  bash -n "${ROOT_DIR}/${script}"
done

expect_retired_guard_success() {
  local label="$1"
  local fixture_root="$2"
  local output
  output="$(bash "${ROOT_DIR}/scripts/quality/verify_architecture_boundaries.sh" "${fixture_root}" --retired-only 2>&1)" || {
    printf '%s\n' "${output}" >&2
    echo "[retired-redundancy-test][FAIL] ${label}: expected success" >&2
    exit 1
  }
  echo "[retired-redundancy-test][PASS] ${label}"
}

expect_retired_guard_failure() {
  local label="$1"
  local fixture_root="$2"
  local expected="$3"
  local output=""
  local status=0
  output="$(bash "${ROOT_DIR}/scripts/quality/verify_architecture_boundaries.sh" "${fixture_root}" --retired-only 2>&1)" || status=$?
  if [[ "${status}" -eq 0 || "${output}" != *"${expected}"* ]]; then
    printf '%s\n' "${output}" >&2
    echo "[retired-redundancy-test][FAIL] ${label}: expected ${expected}" >&2
    exit 1
  fi
  echo "[retired-redundancy-test][PASS] ${label}"
}

GOOD_RETIREMENT_ROOT="${RETIREMENT_FIXTURE_ROOT}/good"
mkdir -p "${GOOD_RETIREMENT_ROOT}"
expect_retired_guard_success "clean project" "${GOOD_RETIREMENT_ROOT}"

PLACEHOLDER_ROOT="${RETIREMENT_FIXTURE_ROOT}/placeholder"
mkdir -p "${PLACEHOLDER_ROOT}/core/common/src/main/kotlin/com/ytone/longcare/core/common"
printf '%s\n' 'object CoreCommonPlaceholder' > "${PLACEHOLDER_ROOT}/core/common/src/main/kotlin/com/ytone/longcare/core/common/Placeholder.kt"
expect_retired_guard_failure "placeholder returns" "${PLACEHOLDER_ROOT}" "retired-redundancy path=core/common"

FEATURE_ENTRY_ROOT="${RETIREMENT_FIXTURE_ROOT}/feature-entry"
mkdir -p "${FEATURE_ENTRY_ROOT}/feature/home/src/main/kotlin/com/ytone/longcare/feature/home"
printf '%s\n' 'object FeatureEntry' > "${FEATURE_ENTRY_ROOT}/feature/home/src/main/kotlin/com/ytone/longcare/feature/home/FeatureEntry.kt"
expect_retired_guard_failure "fake feature entry returns" "${FEATURE_ENTRY_ROOT}" "retired-redundancy path=feature/home"

SELECT_DEVICE_ROOT="${RETIREMENT_FIXTURE_ROOT}/select-device"
mkdir -p "${SELECT_DEVICE_ROOT}/app/src/main/kotlin/com/ytone/longcare/features/selectdevice"
printf '%s\n' 'class SelectDeviceScreen' > "${SELECT_DEVICE_ROOT}/app/src/main/kotlin/com/ytone/longcare/features/selectdevice/SelectDeviceScreen.kt"
expect_retired_guard_failure "select-device package returns" "${SELECT_DEVICE_ROOT}" "category=select-device-package"

UPDATE_DIALOG_ROOT="${RETIREMENT_FIXTURE_ROOT}/update-dialog"
mkdir -p "${UPDATE_DIALOG_ROOT}/app/src/main/kotlin/com/ytone/longcare/ui/components"
printf '%s\n' 'fun UpdateDialog() = Unit' > "${UPDATE_DIALOG_ROOT}/app/src/main/kotlin/com/ytone/longcare/ui/components/UpdateDialog.kt"
expect_retired_guard_failure "old update dialog returns" "${UPDATE_DIALOG_ROOT}" "retired-redundancy path=app/src/main/kotlin/com/ytone/longcare/ui/components/UpdateDialog.kt"

NAVIGATION_SCAFFOLD_ROOT="${RETIREMENT_FIXTURE_ROOT}/navigation-scaffold"
mkdir -p "${NAVIGATION_SCAFFOLD_ROOT}/app/src/main/kotlin/com/ytone/longcare/navigation"
printf '%s\n' 'data class SelectDeviceRoute(val id: Long)' > "${NAVIGATION_SCAFFOLD_ROOT}/app/src/main/kotlin/com/ytone/longcare/navigation/Routes.kt"
expect_retired_guard_failure "navigation scaffold returns" "${NAVIGATION_SCAFFOLD_ROOT}" "category=navigation-scaffolding"

bash "${ROOT_DIR}/scripts/quality/test_android_build_baseline.sh"
bash "${ROOT_DIR}/scripts/quality/test_affected_modules.sh"
bash "${ROOT_DIR}/scripts/quality/test_project_r8_rules.sh"
bash "${ROOT_DIR}/scripts/quality/test_dependency_policy.sh"
bash "${ROOT_DIR}/scripts/quality/test_instrumentation_smoke_classes.sh"
bash "${ROOT_DIR}/scripts/quality/test_tech_stack_baseline.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_sdk_upgrade.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_sdk_readiness.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_platform_test_matrix.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_acceptance_evidence.sh"

echo "[android-build-governance-test][PASS] syntax and focused fixtures passed."
