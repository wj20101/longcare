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
    if rg -q -- "${pattern}" "${file_path}"; then
      matched="true"
    fi
  elif grep -Eq -- "${pattern}" "${file_path}"; then
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
    if rg -q -- "${pattern_primary}" "${file_path}" || rg -q -- "${pattern_alternative}" "${file_path}"; then
      matched="true"
    fi
  elif grep -Eq -- "${pattern_primary}|${pattern_alternative}" "${file_path}"; then
    matched="true"
  fi

  if [[ "${matched}" == "true" ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (${file_path})"
    EXIT_CODE=1
  fi
}

require_absent_pattern() {
  local file_path="$1"
  local pattern="$2"
  local message="$3"
  local matched="false"
  if command -v rg >/dev/null 2>&1; then
    if rg -q -- "${pattern}" "${file_path}"; then
      matched="true"
    fi
  elif grep -Eq -- "${pattern}" "${file_path}"; then
    matched="true"
  fi

  if [[ "${matched}" == "false" ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (${file_path})"
    EXIT_CODE=1
  fi
}

check_retention_values() {
  local file_path="$1"
  local allowed_values_csv="$2"
  local message="$3"
  local values=""

  if command -v rg >/dev/null 2>&1; then
    values="$(rg -o --replace '$1' 'retention-days:[[:space:]]*([0-9]+)' "${file_path}" 2>/dev/null || true)"
  else
    values="$(grep -Eo -- 'retention-days:[[:space:]]*[0-9]+' "${file_path}" 2>/dev/null | grep -Eo -- '[0-9]+' || true)"
  fi

  if [[ -z "${values}" ]]; then
    echo "[ci-workflow-quality][FAIL] ${message} (no retention-days found in ${file_path})"
    EXIT_CODE=1
    return
  fi

  local invalid_values=()
  local value=""
  while IFS= read -r value; do
    [[ -z "${value}" ]] && continue
    case ",${allowed_values_csv}," in
      *,"${value}",*) ;;
      *) invalid_values+=("${value}") ;;
    esac
  done <<< "${values}"

  if [[ ${#invalid_values[@]} -eq 0 ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    local invalid_joined
    invalid_joined="$(printf "%s," "${invalid_values[@]}")"
    invalid_joined="${invalid_joined%,}"
    echo "[ci-workflow-quality][FAIL] ${message} (invalid retention-days: ${invalid_joined} in ${file_path})"
    EXIT_CODE=1
  fi
}

check_job_timeout() {
  local file_path="$1"
  local job_name="$2"
  local expected_timeout="$3"
  local message="$4"
  local actual_timeout=""

  actual_timeout="$(
    awk -v target_job="${job_name}" '
      BEGIN {
        in_target_job = 0
      }
      $0 ~ "^  " target_job ":$" {
        in_target_job = 1
        next
      }
      in_target_job && $0 ~ "^  [A-Za-z0-9_-]+:$" {
        in_target_job = 0
      }
      in_target_job && $0 ~ "^[[:space:]]{4}timeout-minutes:[[:space:]]*[0-9]+" {
        sub(/^[[:space:]]*timeout-minutes:[[:space:]]*/, "", $0)
        print $0
        exit
      }
    ' "${file_path}"
  )"

  if [[ -z "${actual_timeout}" ]]; then
    echo "[ci-workflow-quality][FAIL] ${message} (missing timeout-minutes for job '${job_name}' in ${file_path})"
    EXIT_CODE=1
  elif [[ "${actual_timeout}" == "${expected_timeout}" ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (expected ${expected_timeout}, got ${actual_timeout} in ${file_path})"
    EXIT_CODE=1
  fi
}

check_upload_artifact_step_policies() {
  local file_path="$1"
  local message="$2"
  local policy_issues=""

  policy_issues="$(
    awk '
      function finalize_upload_step() {
        if (!in_upload_step) {
          return
        }
        missing = ""
        if (!has_if_no_files_found) {
          missing = missing "if-no-files-found "
        }
        if (!has_retention_days) {
          missing = missing "retention-days "
        }
        if (missing != "") {
          gsub(/[[:space:]]+$/, "", missing)
          printf "%s [missing: %s]\n", current_step_name, missing
          has_errors = 1
        }
      }
      BEGIN {
        in_upload_step = 0
        has_if_no_files_found = 0
        has_retention_days = 0
        current_step_name = "unknown-step"
        has_errors = 0
      }
      /^[[:space:]]*-[[:space:]]+name:[[:space:]]*/ {
        finalize_upload_step()
        current_step_name = $0
        sub(/^[[:space:]]*-[[:space:]]+name:[[:space:]]*/, "", current_step_name)
        in_upload_step = 0
        has_if_no_files_found = 0
        has_retention_days = 0
      }
      /uses:[[:space:]]*actions\/upload-artifact@v(6|7)/ {
        in_upload_step = 1
        has_if_no_files_found = 0
        has_retention_days = 0
      }
      in_upload_step && /if-no-files-found:[[:space:]]*(warn|error)/ {
        has_if_no_files_found = 1
      }
      in_upload_step && /retention-days:[[:space:]]*[0-9]+/ {
        has_retention_days = 1
      }
      END {
        finalize_upload_step()
        if (has_errors) {
          exit 1
        }
      }
    ' "${file_path}" 2>/dev/null
  )"

  if [[ $? -eq 0 ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (${file_path})"
    if [[ -n "${policy_issues}" ]]; then
      echo "${policy_issues}" | sed 's/^/  - /'
    fi
    EXIT_CODE=1
  fi
}

check_step_contains_pattern() {
  local file_path="$1"
  local step_name="$2"
  local pattern="$3"
  local message="$4"
  local step_content=""
  local matched="false"

  step_content="$(
    awk -v target_step="${step_name}" '
      function strip_step_name(line) {
        sub(/^[[:space:]]*-[[:space:]]+name:[[:space:]]*/, "", line)
        return line
      }
      /^[[:space:]]*-[[:space:]]+name:[[:space:]]*/ {
        current_step = strip_step_name($0)
        if (capture == 1 && current_step != target_step) {
          exit
        }
        if (current_step == target_step) {
          capture = 1
          next
        }
      }
      capture == 1 {
        print
      }
    ' "${file_path}"
  )"

  if [[ -z "${step_content}" ]]; then
    echo "[ci-workflow-quality][FAIL] ${message} (step not found: ${step_name} in ${file_path})"
    EXIT_CODE=1
    return
  fi

  if command -v rg >/dev/null 2>&1; then
    if printf '%s\n' "${step_content}" | rg -q -- "${pattern}"; then
      matched="true"
    fi
  elif printf '%s\n' "${step_content}" | grep -Eq -- "${pattern}"; then
    matched="true"
  fi

  if [[ "${matched}" == "true" ]]; then
    echo "[ci-workflow-quality][PASS] ${message}"
  else
    echo "[ci-workflow-quality][FAIL] ${message} (missing pattern '${pattern}' in step '${step_name}' of ${file_path})"
    EXIT_CODE=1
  fi
}

WORKFLOWS=(
  "${ROOT_DIR}/.github/workflows/android-ci.yml"
  "${ROOT_DIR}/.github/workflows/baseline-profile.yml"
  "${ROOT_DIR}/.github/workflows/android-release.yml"
  "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml"
)
SHARED_ANDROID_BUILD_ENV_ACTION="${ROOT_DIR}/.github/actions/android-build-env/action.yml"

for workflow in "${WORKFLOWS[@]}"; do
  if [[ ! -f "${workflow}" ]]; then
    echo "[ci-workflow-quality][FAIL] missing workflow: ${workflow}"
    EXIT_CODE=1
    continue
  fi

  require_pattern "${workflow}" "concurrency:" "has concurrency block"
  require_pattern "${workflow}" "cancel-in-progress:" "cancel-in-progress configured"
  require_absent_pattern "${workflow}" "cancel-in-progress:[[:space:]]*false" "cancel-in-progress is not disabled"
  require_pattern "${workflow}" "permissions:" "has permissions block"
  require_pattern "${workflow}" "timeout-minutes:" "has job timeout"
  require_pattern "${workflow}" "uses:[[:space:]]*actions/checkout@v6" "uses pinned checkout action"
  require_absent_pattern "${workflow}" "uses:[[:space:]]*[^[:space:]]+@(main|master|HEAD)" "does not use mutable action refs"
  require_pattern "${workflow}" "uses:[[:space:]]*actions/upload-artifact@v(6|7)" "uses supported pinned upload-artifact action"
  require_absent_pattern "${workflow}" "uses:[[:space:]]*actions/upload-artifact@v([0-58-9]|[1-9][0-9]+)" "does not use unsupported upload-artifact action"
  require_any_pattern "${workflow}" "uses:[[:space:]]*gradle/actions/setup-gradle@v5" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "uses setup-gradle action (direct or shared)"
  require_any_pattern "${workflow}" "bash scripts/quality/verify_gradle_stability\\.sh" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "runs Gradle stability gate (direct or shared)"
done

if [[ ! -f "${SHARED_ANDROID_BUILD_ENV_ACTION}" ]]; then
  echo "[ci-workflow-quality][FAIL] missing shared action: ${SHARED_ANDROID_BUILD_ENV_ACTION}"
  EXIT_CODE=1
else
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/quality/verify_gradle_stability\\.sh" "shared android build env runs lightweight gradle stability guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "shared android build env supports optional workflow quality guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "run-lint-ignore-policy-check:" "shared android build env exposes optional lint ignore guard input"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "run-jetpack-compat-check:" "shared android build env exposes optional jetpack compat guard input"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "run-baselineprofile-journey-check:" "shared android build env exposes optional baseline journey guard input"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "run-module-dependency-check:" "shared android build env exposes optional module dependency guard input"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/lint/verify_lint_ignore_policy\\.sh" "shared android build env supports reusable lint ignore guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/quality/verify_jetpack_compat_apis\\.sh" "shared android build env supports reusable jetpack compat guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/quality/verify_baselineprofile_journeys\\.sh" "shared android build env supports reusable baseline journey guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "bash scripts/quality/verify_module_dependency_whitelist\\.sh" "shared android build env supports reusable module dependency guard"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*actions/setup-java@v5" "shared android build env pins setup-java@v5"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*gradle/actions/setup-gradle@v5" "shared android build env pins setup-gradle@v5"
  require_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*android-actions/setup-android@v3" "shared android build env pins setup-android@v3"
  require_absent_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*actions/setup-java@v([0-46-9]|[1-9][0-9]+)" "shared android build env does not use unexpected setup-java version"
  require_absent_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*gradle/actions/setup-gradle@v([0-46-9]|[1-9][0-9]+)" "shared android build env does not use unexpected setup-gradle version"
  require_absent_pattern "${SHARED_ANDROID_BUILD_ENV_ACTION}" "uses:[[:space:]]*android-actions/setup-android@v([0-24-9]|[1-9][0-9]+)" "shared android build env does not use unexpected setup-android version"
fi

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "paths-ignore:" "android-ci has paths-ignore optimization"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "^[[:space:]]{2}push:" "android-ci keeps push trigger"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "^[[:space:]]{2}pull_request:" "android-ci keeps pull_request trigger"
check_job_timeout "${ROOT_DIR}/.github/workflows/android-ci.yml" "detect-affected" "10" "android-ci detect-affected keeps timeout budget 10"
check_job_timeout "${ROOT_DIR}/.github/workflows/android-ci.yml" "verify-build" "45" "android-ci verify-build keeps timeout budget 45"
check_job_timeout "${ROOT_DIR}/.github/workflows/android-ci.yml" "instrumentation-smoke" "60" "android-ci instrumentation-smoke keeps timeout budget 60"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "-[[:space:]]*\"docs/\\*\\*\"" "android-ci paths-ignore includes docs directory"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "-[[:space:]]*\"\\*\\*/\\*\\.md\"" "android-ci paths-ignore includes markdown files"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "-[[:space:]]*\"task_plan\\.md\"" "android-ci paths-ignore includes task_plan.md"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "-[[:space:]]*\"findings\\.md\"" "android-ci paths-ignore includes findings.md"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "-[[:space:]]*\"progress\\.md\"" "android-ci paths-ignore includes progress.md"
require_absent_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "^[[:space:]]{2}push:" "baseline-profile disables push trigger"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "^[[:space:]]{2}workflow_dispatch:" "baseline-profile keeps workflow_dispatch trigger"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "^[[:space:]]{2}schedule:" "baseline-profile keeps schedule trigger"
check_job_timeout "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "generate-baseline-profile" "120" "baseline-profile generate-baseline-profile keeps timeout budget 120"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "cron:[[:space:]]*'0 2 \\* \\* 1'" "baseline-profile keeps weekly schedule at 02:00 UTC Monday"
require_absent_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "^[[:space:]]{4}branches:" "android-release push trigger does not include branches"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "^[[:space:]]{2}push:" "android-release keeps push trigger"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "^[[:space:]]{4}tags:" "android-release push trigger uses tags filter"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "-[[:space:]]*'v\\*'" "android-release tags filter stays on v*"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "^[[:space:]]{2}workflow_dispatch:" "android-release keeps workflow_dispatch trigger"
check_job_timeout "${ROOT_DIR}/.github/workflows/android-release.yml" "release-build" "120" "android-release release-build keeps timeout budget 120"
require_absent_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "^[[:space:]]{2}push:" "face-sdk-migration-check disables push trigger"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "^[[:space:]]{2}pull_request:" "face-sdk-migration-check keeps pull_request trigger"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "^[[:space:]]{4}paths:" "face-sdk-migration-check pull_request keeps paths filter"
check_job_timeout "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "maven-switch-compile" "45" "face-sdk-migration-check maven-switch-compile keeps timeout budget 45"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/face-sdk/\\*\\*\"" "face-sdk-migration-check paths filter includes face-sdk scripts"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"\\.github/actions/android-build-env/action\\.yml\"" "face-sdk-migration-check paths filter includes shared action"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/lint/verify_lint_warning_allowlist\\.sh\"" "face-sdk-migration-check paths filter includes lint allowlist script"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/lint/lint_warning_waivers\\.json\"" "face-sdk-migration-check paths filter includes lint waiver manifest"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/lint/verify_lint_ignore_policy\\.sh\"" "face-sdk-migration-check paths filter includes lint ignore policy script"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/quality/verify_ci_workflow_quality\\.sh\"" "face-sdk-migration-check paths filter includes ci quality guard script"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/quality/verify_jetpack_compat_apis\\.sh\"" "face-sdk-migration-check paths filter includes jetpack compat guard script"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/quality/verify_baselineprofile_journeys\\.sh\"" "face-sdk-migration-check paths filter includes baselineprofile journey guard script"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/quality/verify_module_dependency_whitelist\\.sh\"" "face-sdk-migration-check paths filter includes module dependency whitelist guard script"
require_absent_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/lint/\\*\\*\"" "face-sdk-migration-check does not use broad lint path filter"
require_absent_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "-[[:space:]]*\"scripts/quality/\\*\\*\"" "face-sdk-migration-check does not use broad quality path filter"
require_any_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "android-ci runs workflow quality gate"
require_any_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "baseline-profile runs workflow quality gate"
require_any_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "android-release runs workflow quality gate"
require_any_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "run-workflow-quality-check:[[:space:]]*'true'" "face-sdk-migration-check runs workflow quality gate"
require_absent_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "run-workflow-quality-check:[[:space:]]*'true'" "android-ci keeps ci-required workflow quality ownership inside workflow steps"
require_absent_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "run-workflow-quality-check:[[:space:]]*'true'" "android-release keeps ci-required workflow quality ownership inside workflow steps"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "name:[[:space:]]*Run ci-required quality gates" "android-ci defines explicit ci-required gate step"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "name:[[:space:]]*Collect ci-required quality snapshot" "android-ci defines explicit ci-required snapshot step"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Run ci-required quality gates" "android-release defines explicit ci-required gate step"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Collect ci-required quality snapshot" "android-release defines explicit ci-required snapshot step"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Run release-required signing safety checks" "android-release defines explicit release-required signing step"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Run release-required exported component guard" "android-release defines explicit release-required exported-component step"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "run-lint-ignore-policy-check:[[:space:]]*'false'" "android-ci disables shared lint-ignore guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "run-jetpack-compat-check:[[:space:]]*'false'" "android-ci disables shared jetpack guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "run-baselineprofile-journey-check:[[:space:]]*'false'" "android-ci disables shared baseline journey guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "run-module-dependency-check:[[:space:]]*'false'" "android-ci disables shared module dependency guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "run-lint-ignore-policy-check:[[:space:]]*'false'" "android-release disables shared lint-ignore guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "run-jetpack-compat-check:[[:space:]]*'false'" "android-release disables shared jetpack guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "run-baselineprofile-journey-check:[[:space:]]*'false'" "android-release disables shared baseline journey guard to keep ownership explicit"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "run-module-dependency-check:[[:space:]]*'false'" "android-release disables shared module dependency guard to keep ownership explicit"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "Run ci-required quality gates" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "android-ci ci-required step runs workflow quality guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "Run ci-required quality gates" "bash scripts/lint/verify_lint_ignore_policy\\.sh app/lint\\.xml" "android-ci ci-required step runs lint ignore guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "Run ci-required quality gates" "bash scripts/quality/verify_jetpack_compat_apis\\.sh" "android-ci ci-required step runs jetpack compat guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "Run ci-required quality gates" "bash scripts/quality/verify_baselineprofile_journeys\\.sh" "android-ci ci-required step runs baseline journey guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "Run ci-required quality gates" "bash scripts/quality/verify_module_dependency_whitelist\\.sh \\." "android-ci ci-required step runs module dependency guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "Run ci-required quality gates" "bash scripts/quality/verify_ci_workflow_quality\\.sh" "android-release ci-required step runs workflow quality guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "Run ci-required quality gates" "bash scripts/lint/verify_lint_ignore_policy\\.sh app/lint\\.xml" "android-release ci-required step runs lint ignore guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "Run ci-required quality gates" "bash scripts/quality/verify_jetpack_compat_apis\\.sh" "android-release ci-required step runs jetpack compat guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "Run ci-required quality gates" "bash scripts/quality/verify_baselineprofile_journeys\\.sh" "android-release ci-required step runs baseline journey guard command"
check_step_contains_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "Run ci-required quality gates" "bash scripts/quality/verify_module_dependency_whitelist\\.sh \\." "android-release ci-required step runs module dependency guard command"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "contents:[[:space:]]*read" "android-ci uses read-only contents permission"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "contents:[[:space:]]*read" "face-sdk-migration-check uses read-only contents permission"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "contents:[[:space:]]*write" "android-release uses writable contents permission"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "contents:[[:space:]]*write" "baseline-profile uses writable contents permission"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "pull-requests:[[:space:]]*write" "baseline-profile uses writable pull-requests permission"

require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-ci uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "baseline-profile uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "bash scripts/quality/free_runner_disk_space\\.sh" "android-release uses disk cleanup script"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "android-ci uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "baseline-profile uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "android-release uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "uses:[[:space:]]*\\./\\.github/actions/android-build-env" "face-sdk-migration-check uses shared android build env action"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "name:[[:space:]]*Upload failure diagnostics" "android-ci uploads failure diagnostics"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "name:[[:space:]]*Upload failure diagnostics" "baseline-profile uploads failure diagnostics"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Upload failure diagnostics" "android-release uploads failure diagnostics"
require_pattern "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "name:[[:space:]]*Upload failure diagnostics" "face-sdk-migration-check uploads failure diagnostics"
check_upload_artifact_step_policies "${ROOT_DIR}/.github/workflows/android-ci.yml" "android-ci upload-artifact steps keep if-no-files-found and retention-days"
check_upload_artifact_step_policies "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "baseline-profile upload-artifact steps keep if-no-files-found and retention-days"
check_upload_artifact_step_policies "${ROOT_DIR}/.github/workflows/android-release.yml" "android-release upload-artifact steps keep if-no-files-found and retention-days"
check_upload_artifact_step_policies "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "face-sdk-migration-check upload-artifact steps keep if-no-files-found and retention-days"
check_retention_values "${ROOT_DIR}/.github/workflows/android-ci.yml" "7" "android-ci keeps artifact retention-days at 7"
check_retention_values "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "7" "baseline-profile keeps artifact retention-days at 7"
check_retention_values "${ROOT_DIR}/.github/workflows/face-sdk-migration-check.yml" "7" "face-sdk-migration-check keeps artifact retention-days at 7"
check_retention_values "${ROOT_DIR}/.github/workflows/android-release.yml" "7" "android-release keeps artifact retention-days at 7"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "name:[[:space:]]*Upload release artifacts" "android-release keeps release artifact upload step"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "retention-days:[[:space:]]*7" "android-release keeps 7-day retention for release and diagnostics artifacts"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "name:[[:space:]]*Publish affected plan summary" "android-ci publishes affected plan summary step"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "GITHUB_STEP_SUMMARY" "android-ci writes affected plan into GITHUB_STEP_SUMMARY"
require_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "uses:[[:space:]]*reactivecircus/android-emulator-runner@v2" "android-ci pins emulator runner action"
require_absent_pattern "${ROOT_DIR}/.github/workflows/android-ci.yml" "uses:[[:space:]]*reactivecircus/android-emulator-runner@v([013-9]|[1-9][0-9]+)" "android-ci does not use unexpected emulator runner version"
require_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "uses:[[:space:]]*peter-evans/create-pull-request@v8" "baseline-profile pins create-pull-request action"
require_absent_pattern "${ROOT_DIR}/.github/workflows/baseline-profile.yml" "uses:[[:space:]]*peter-evans/create-pull-request@v([0-79]|[1-9][0-9]+)" "baseline-profile does not use unexpected create-pull-request version"
require_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "uses:[[:space:]]*softprops/action-gh-release@v2" "android-release pins gh-release action"
require_absent_pattern "${ROOT_DIR}/.github/workflows/android-release.yml" "uses:[[:space:]]*softprops/action-gh-release@v([013-9]|[1-9][0-9]+)" "android-release does not use unexpected gh-release version"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[ci-workflow-quality] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[ci-workflow-quality] verification passed."
