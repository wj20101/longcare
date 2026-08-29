#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

SCRIPTS=(
  scripts/quality/android_build_values.sh
  scripts/quality/verify_android_build_baseline.sh
  scripts/quality/test_android_build_baseline.sh
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

bash "${ROOT_DIR}/scripts/quality/test_android_build_baseline.sh"
bash "${ROOT_DIR}/scripts/quality/test_dependency_policy.sh"
bash "${ROOT_DIR}/scripts/quality/test_instrumentation_smoke_classes.sh"
bash "${ROOT_DIR}/scripts/quality/test_tech_stack_baseline.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_sdk_upgrade.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_sdk_readiness.sh"
bash "${ROOT_DIR}/scripts/quality/test_target_platform_test_matrix.sh"

echo "[android-build-governance-test][PASS] syntax and focused fixtures passed."
