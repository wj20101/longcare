#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXIT_CODE=0

require_pattern() {
  local file_path="$1"
  local pattern="$2"
  local message="$3"
  if rg -q --multiline "${pattern}" "${file_path}"; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (${file_path})"
    EXIT_CODE=1
  fi
}

WORKFLOWS=(
  "${ROOT_DIR}/.github/workflows/android-ci.yml"
  "${ROOT_DIR}/.github/workflows/baseline-profile.yml"
  "${ROOT_DIR}/.github/workflows/android-release.yml"
)

for workflow in "${WORKFLOWS[@]}"; do
  if [[ ! -f "${workflow}" ]]; then
    echo "[ci-workflow-quality][FAIL] missing workflow: ${workflow}"
    EXIT_CODE=1
    continue
  fi

  require_pattern "${workflow}" "concurrency:" "has concurrency block"
  require_pattern "${workflow}" "cancel-in-progress:\\s*true" "cancel-in-progress enabled"
  require_pattern "${workflow}" "timeout-minutes:" "has job timeout"
  require_pattern "${workflow}" "uses:\\s*gradle/actions/setup-gradle@v5" "uses setup-gradle action"
  require_pattern "${workflow}" "bash scripts/quality/verify_gradle_stability\\.sh" "runs Gradle stability gate"
done

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "paths-ignore:" "android-ci has paths-ignore optimization"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "android-ci runs workflow quality gate"

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-ci uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "baseline-profile uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-release uses disk cleanup script"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[ci-workflow-quality] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[ci-workflow-quality] verification passed."
