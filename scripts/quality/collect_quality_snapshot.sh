#!/usr/bin/env bash
set -u -o pipefail

PROJECT_ROOT="."
OUTPUT_DIR="build/quality-snapshot"
LINT_REPORT="app/build/reports/lint-results-debug.txt"
SOURCE_ROOT="app/src/main/kotlin"
WORKFLOW_FILE=".github/workflows/android-ci.yml"
ENSURE_LINT_REPORT="true"

usage() {
  cat <<USAGE
Usage: $0 [options]

Options:
  --project-root <path>   Project root to run checks from (default: .)
  --output-dir <path>     Output directory for snapshot files (default: build/quality-snapshot)
  --lint-report <path>    Lint report path for lint allowlist check (default: app/build/reports/lint-results-debug.txt)
  --source-root <path>    Kotlin source root for source checks (default: app/src/main/kotlin)
  --workflow-file <path>  Workflow file for target sdk gate (default: .github/workflows/android-ci.yml)
  --skip-lint-bootstrap   Do not auto-run lint when lint report file is missing
  -h, --help              Show this help message
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --lint-report)
      LINT_REPORT="$2"
      shift 2
      ;;
    --source-root)
      SOURCE_ROOT="$2"
      shift 2
      ;;
    --workflow-file)
      WORKFLOW_FILE="$2"
      shift 2
      ;;
    --skip-lint-bootstrap)
      ENSURE_LINT_REPORT="false"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[quality-snapshot][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! command -v jq >/dev/null 2>&1; then
  echo "[quality-snapshot][FAIL] jq is required but was not found in PATH." >&2
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
mkdir -p "${PROJECT_ROOT}/${OUTPUT_DIR}"

OUTPUT_DIR_ABS="${PROJECT_ROOT}/${OUTPUT_DIR}"
LOG_DIR="${OUTPUT_DIR_ABS}/logs"
REPORT_JSON="${OUTPUT_DIR_ABS}/quality_snapshot.json"
REPORT_MD="${OUTPUT_DIR_ABS}/quality_snapshot.md"
CHECKS_JSONL="${OUTPUT_DIR_ABS}/checks.jsonl"
CHECKS_ARRAY_JSON="${OUTPUT_DIR_ABS}/checks.json"

mkdir -p "${LOG_DIR}"
: > "${CHECKS_JSONL}"

TIMESTAMP_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

LINT_REPORT_PATH="${PROJECT_ROOT}/${LINT_REPORT}"
SOURCE_ROOT_PATH="${PROJECT_ROOT}/${SOURCE_ROOT}"
WORKFLOW_FILE_PATH="${PROJECT_ROOT}/${WORKFLOW_FILE}"

if [[ "${ENSURE_LINT_REPORT}" == "true" ]] && [[ ! -f "${LINT_REPORT_PATH}" ]]; then
  LINT_BOOTSTRAP_LOG="${LOG_DIR}/0_lint_bootstrap.log"
  echo "[quality-snapshot] lint report missing, running :app:lintDebug to bootstrap..."
  (
    cd "${PROJECT_ROOT}"
    ./gradlew --no-daemon :app:lintDebug
  ) >"${LINT_BOOTSTRAP_LOG}" 2>&1
  if [[ $? -ne 0 ]]; then
    echo "[quality-snapshot][FAIL] lint bootstrap failed. see: ${LINT_BOOTSTRAP_LOG}" >&2
    exit 1
  fi
fi

CHECK_NAMES=(
  "Release Exported Component Allowlist"
  "Lint Warning Allowlist"
  "Lint Ignore Policy Guard"
  "Jetpack Compat API Guard"
  "Baseline Profile Journey Guard"
  "Coroutine Cancellation Guards"
  "No Empty Catch Blocks"
  "Target SDK Upgrade Gate"
  "Exact Alarm Permission Config"
  "Architecture Boundaries"
  "Module API Visibility"
  "CI Workflow Quality Guard"
)

CHECK_CMDS=(
  "bash scripts/quality/verify_release_exported_components.sh"
  "bash scripts/lint/verify_lint_warning_allowlist.sh \"${LINT_REPORT_PATH}\""
  "bash scripts/lint/verify_lint_ignore_policy.sh app/lint.xml"
  "bash scripts/quality/verify_jetpack_compat_apis.sh"
  "bash scripts/quality/verify_baselineprofile_journeys.sh"
  "bash scripts/quality/verify_cancellation_guards.sh \"${SOURCE_ROOT_PATH}\""
  "bash scripts/quality/verify_no_empty_catch_blocks.sh \"${SOURCE_ROOT_PATH}\""
  "bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts \"${WORKFLOW_FILE_PATH}\""
  "bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml"
  "bash scripts/quality/verify_architecture_boundaries.sh ."
  "bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare ."
  "bash scripts/quality/verify_ci_workflow_quality.sh"
)

sanitize_name() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '_' | sed -E 's/^_+|_+$//g'
}

epoch_to_iso_utc() {
  local epoch="$1"
  if date -u -r "${epoch}" +"%Y-%m-%dT%H:%M:%SZ" >/dev/null 2>&1; then
    date -u -r "${epoch}" +"%Y-%m-%dT%H:%M:%SZ"
  else
    date -u -d "@${epoch}" +"%Y-%m-%dT%H:%M:%SZ"
  fi
}

OVERALL_EXIT=0

for i in "${!CHECK_NAMES[@]}"; do
  NAME="${CHECK_NAMES[$i]}"
  CMD="${CHECK_CMDS[$i]}"
  SLUG="$(sanitize_name "${NAME}")"
  LOG_FILE="${LOG_DIR}/$((i + 1))_${SLUG}.log"

  START_EPOCH="$(date +%s)"
  (
    cd "${PROJECT_ROOT}"
    bash -lc "${CMD}"
  ) >"${LOG_FILE}" 2>&1
  EXIT_CODE=$?
  END_EPOCH="$(date +%s)"
  DURATION=$((END_EPOCH - START_EPOCH))

  STATUS="PASS"
  if [[ "${EXIT_CODE}" -ne 0 ]]; then
    STATUS="FAIL"
    OVERALL_EXIT=1
  fi

  echo "[quality-snapshot] ${STATUS} - ${NAME} (${DURATION}s)"

  jq -n \
    --arg name "${NAME}" \
    --arg command "${CMD}" \
    --arg status "${STATUS}" \
    --arg log "${LOG_FILE}" \
    --arg started_at "$(epoch_to_iso_utc "${START_EPOCH}")" \
    --arg finished_at "$(epoch_to_iso_utc "${END_EPOCH}")" \
    --argjson exit_code "${EXIT_CODE}" \
    --argjson duration_seconds "${DURATION}" \
    '{
      name: $name,
      command: $command,
      status: $status,
      exit_code: $exit_code,
      duration_seconds: $duration_seconds,
      started_at: $started_at,
      finished_at: $finished_at,
      log: $log
    }' >> "${CHECKS_JSONL}"

done

jq -s '.' "${CHECKS_JSONL}" > "${CHECKS_ARRAY_JSON}"

TOTAL_CHECKS="$(jq 'length' "${CHECKS_ARRAY_JSON}")"
PASSED_CHECKS="$(jq '[.[] | select(.status == "PASS")] | length' "${CHECKS_ARRAY_JSON}")"
FAILED_CHECKS="$(jq '[.[] | select(.status == "FAIL")] | length' "${CHECKS_ARRAY_JSON}")"
OVERALL_STATUS="PASS"
if [[ "${OVERALL_EXIT}" -ne 0 ]]; then
  OVERALL_STATUS="FAIL"
fi

jq -n \
  --arg generated_at "${TIMESTAMP_UTC}" \
  --arg project_root "${PROJECT_ROOT}" \
  --arg output_dir "${OUTPUT_DIR_ABS}" \
  --arg lint_report "${LINT_REPORT_PATH}" \
  --arg workflow_file "${WORKFLOW_FILE_PATH}" \
  --arg overall_status "${OVERALL_STATUS}" \
  --argjson total_checks "${TOTAL_CHECKS}" \
  --argjson passed_checks "${PASSED_CHECKS}" \
  --argjson failed_checks "${FAILED_CHECKS}" \
  --slurpfile checks "${CHECKS_ARRAY_JSON}" \
  '{
    generated_at: $generated_at,
    project_root: $project_root,
    output_dir: $output_dir,
    lint_report: $lint_report,
    workflow_file: $workflow_file,
    summary: {
      overall_status: $overall_status,
      total_checks: $total_checks,
      passed_checks: $passed_checks,
      failed_checks: $failed_checks
    },
    checks: $checks[0]
  }' > "${REPORT_JSON}"

{
  echo "# Quality Snapshot"
  echo
  echo "- generated_at: \`${TIMESTAMP_UTC}\`"
  echo "- overall_status: \`${OVERALL_STATUS}\`"
  echo "- passed: \`${PASSED_CHECKS}/${TOTAL_CHECKS}\`"
  echo "- project_root: \`${PROJECT_ROOT}\`"
  echo
  echo "| Check | Status | Duration (s) | Exit Code | Log |"
  echo "|---|---|---:|---:|---|"
  jq -r '.[] | "| \(.name) | \(.status) | \(.duration_seconds) | \(.exit_code) | `\(.log)` |"' "${CHECKS_ARRAY_JSON}"
  echo
  echo "JSON report: \`${REPORT_JSON}\`"
} > "${REPORT_MD}"

echo "[quality-snapshot] markdown report: ${REPORT_MD}"
echo "[quality-snapshot] json report: ${REPORT_JSON}"

exit "${OVERALL_EXIT}"
