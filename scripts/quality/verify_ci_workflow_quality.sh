#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXIT_CODE=0

require_pattern() {
  local file_path="$1"
  local pattern="$2"
  local message="$3"
  local matched="false"
  if command -v rg >/dev/null 2>&1; then
    if rg -q "${pattern}" "${file_path}"; then
      matched="true"
    fi
  elif grep -Eq "${pattern}" "${file_path}"; then
    matched="true"
  fi

  if [[ "${matched}" == "true" ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (${file_path})"
    EXIT_CODE=1
  fi
}

require_any_pattern() {
  local file_path="$1"
  local pattern_primary="$2"
  local pattern_alternative="$3"
  local message="$4"
  local matched="false"
  if command -v rg >/dev/null 2>&1; then
    if rg -q "${pattern_primary}" "${file_path}" || rg -q "${pattern_alternative}" "${file_path}"; then
      matched="true"
    fi
  elif grep -Eq "${pattern_primary}|${pattern_alternative}" "${file_path}"; then
    matched="true"
  fi

  if [[ "${matched}" == "true" ]]; then
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
  require_pattern "${workflow}" "cancel-in-progress:[[:space:]]*true" "cancel-in-progress enabled"
  require_pattern "${workflow}" "timeout-minutes:" "has job timeout"
  require_any_pattern "${workflow}" "uses:[[:space:]]*gradle/actions/setup-gradle@v5" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "uses setup-gradle action (direct or shared)"
  require_any_pattern "${workflow}" "bash scripts/quality/verify_gradle_stability\\.sh" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "runs Gradle stability gate (direct or shared)"
done

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "paths-ignore:" "android-ci has paths-ignore optimization"
require_any_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "android-ci runs workflow quality gate"
require_any_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "baseline-profile runs workflow quality gate"
require_any_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "android-release runs workflow quality gate"

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-ci uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "baseline-profile uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-release uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "android-ci uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "baseline-profile uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "android-release uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "name:[[:space:]]*Upload failure diagnostics" "android-ci uploads failure diagnostics"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "name:[[:space:]]*Upload failure diagnostics" "baseline-profile uploads failure diagnostics"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Upload failure diagnostics" "android-release uploads failure diagnostics"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[ci-workflow-quality] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[ci-workflow-quality] verification passed."
