#!/usr/bin/env bash
set -u -o pipefail

PROJECT_ROOT="."
OUTPUT_DIR="build/quality-snapshot"
LINT_REPORT="app/build/reports/lint-results-debug.txt"
SOURCE_ROOT="app/src/main/kotlin"
WORKFLOW_FILE=".github/workflows/android-ci.yml"
ENSURE_LINT_REPORT="true"
REGISTRY_FILE="scripts/quality/quality_gate_registry.json"

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

if [[ "${OUTPUT_DIR}" = /* ]]; then
  OUTPUT_DIR_ABS="${OUTPUT_DIR}"
else
  OUTPUT_DIR_ABS="${PROJECT_ROOT}/${OUTPUT_DIR}"
fi
mkdir -p "${OUTPUT_DIR_ABS}"

LOG_DIR="${OUTPUT_DIR_ABS}/logs"
REPORT_JSON="${OUTPUT_DIR_ABS}/quality_snapshot.json"
REPORT_MD="${OUTPUT_DIR_ABS}/quality_snapshot.md"
CHECKS_JSONL="${OUTPUT_DIR_ABS}/checks.jsonl"
CHECKS_ARRAY_JSON="${OUTPUT_DIR_ABS}/checks.json"

mkdir -p "${LOG_DIR}"
: > "${CHECKS_JSONL}"

TIMESTAMP_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

if [[ "${LINT_REPORT}" = /* ]]; then
  LINT_REPORT_PATH="${LINT_REPORT}"
else
  LINT_REPORT_PATH="${PROJECT_ROOT}/${LINT_REPORT}"
fi

if [[ "${SOURCE_ROOT}" = /* ]]; then
  SOURCE_ROOT_PATH="${SOURCE_ROOT}"
else
  SOURCE_ROOT_PATH="${PROJECT_ROOT}/${SOURCE_ROOT}"
fi

if [[ "${WORKFLOW_FILE}" = /* ]]; then
  WORKFLOW_FILE_PATH="${WORKFLOW_FILE}"
else
  WORKFLOW_FILE_PATH="${PROJECT_ROOT}/${WORKFLOW_FILE}"
fi

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
  "No Tracked Keystore Files"
  "Debug Mock Network Safety"
  "Release Exported Component Allowlist"
  "Vendor SDK Release Readiness"
  "Lint Warning Allowlist"
  "Lint Ignore Policy Guard"
  "Jetpack Compat API Guard"
  "Baseline Profile Journey Guard"
  "Coroutine Cancellation Guards"
  "No Empty Catch Blocks"
  "Target SDK Upgrade Gate"
  "Android Build Baseline"
  "Dependency Stability Policy"
  "Target SDK Readiness Policy"
  "Target Platform Test Matrix"
  "Instrumentation Smoke Class Integrity"
  "Tech Stack Documentation Baseline"
  "Exact Alarm Permission Config"
  "Architecture Boundaries"
  "Module Dependency Whitelist"
  "Module API Visibility"
  "CI Workflow Quality Guard"
)

CHECK_IDS=(
  "no_tracked_keystore_files"
  "debug_mock_network_safety"
  "release_exported_components"
  "vendor_sdk_release_readiness"
  "lint_warning_allowlist"
  "lint_ignore_policy"
  "jetpack_compat_api_guard"
  "startup_profile_semantics"
  "coroutine_cancellation_guards"
  "no_empty_catch_blocks"
  "target_sdk_upgrade_gate"
  "android_build_baseline"
  "dependency_policy"
  "target_sdk_readiness"
  "target_platform_test_matrix"
  "instrumentation_smoke_classes"
  "tech_stack_baseline"
  "exact_alarm_permission_config"
  "architecture_boundaries"
  "module_dependency_whitelist"
  "module_api_visibility"
  "workflow_quality"
)

CHECK_CMDS=(
  "bash scripts/quality/verify_no_tracked_keystore_files.sh ."
  "python3 scripts/quality/verify_debug_mock_network.py --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_release_exported_components.sh"
  "bash scripts/quality/verify_vendor_sdk_release_readiness.sh \"${LINT_REPORT_PATH}\""
  "bash scripts/lint/verify_lint_warning_allowlist.sh \"${LINT_REPORT_PATH}\""
  "bash scripts/lint/verify_lint_ignore_policy.sh app/lint.xml"
  "bash scripts/quality/verify_jetpack_compat_apis.sh"
  "bash scripts/quality/verify_baselineprofile_journeys.sh"
  "bash scripts/quality/verify_cancellation_guards.sh \"${SOURCE_ROOT_PATH}\""
  "bash scripts/quality/verify_no_empty_catch_blocks.sh \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_target_sdk_upgrade.sh settings.gradle.kts \"${WORKFLOW_FILE_PATH}\""
  "bash scripts/quality/verify_android_build_baseline.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_dependency_policy.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_target_sdk_readiness.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_target_platform_test_matrix.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_instrumentation_smoke_classes.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_tech_stack_baseline.sh --project-root \"${PROJECT_ROOT}\""
  "bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml"
  "bash scripts/quality/verify_architecture_boundaries.sh ."
  "bash scripts/quality/verify_module_dependency_whitelist.sh ."
  "bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare ."
  "bash scripts/quality/verify_ci_workflow_quality.sh"
)

CHECK_TIERS=(
  "ci-required"
  "ci-required"
  "release-required"
  "release-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
  "ci-required"
)

CHECK_CATEGORIES=(
  "secrets"
  "network-safety"
  "release-safety"
  "vendor-security"
  "lint-policy"
  "lint-policy"
  "api-compatibility"
  "performance-guardrail"
  "concurrency-safety"
  "exception-safety"
  "sdk-governance"
  "build-governance"
  "dependency-governance"
  "sdk-governance"
  "test-governance"
  "test-governance"
  "documentation-governance"
  "manifest-policy"
  "architecture"
  "module-governance"
  "module-governance"
  "workflow-governance"
)

CHECK_LIKELY_FIXES=(
  "remove-tracked-keystore-and-use-secret-distribution"
  "restore-default-off-fail-closed-debug-mock-and-release-isolation"
  "align-exported-components-with-release-allowlist"
  "replace-production-blocking-vendor-sdk-binaries"
  "fix-lint-warning-or-add-approved-waiver-entry"
  "remove-forbidden-lint-ignore-and-fix-root-warning"
  "align-jetpack-usage-with-compat-guardrails"
  "update-baseline-profile-journeys-to-cover-required-flows"
  "add-structured-cancellation-handling-in-coroutines"
  "replace-empty-catch-with-explicit-handling-or-rethrow"
  "align-workflow-emulator-api-level-with-target-sdk"
  "restore-settings-owned-sdk-and-matching-toolchain-baseline"
  "use-stable-dependency-or-complete-exact-preview-waiver"
  "restore-coherent-candidate-readiness-status"
  "restore-distinct-api33-api36-api37-validation-lanes"
  "replace-stale-smoke-class-selector"
  "synchronize-documentation-with-executable-baseline"
  "align-exact-alarm-permission-config-with-policy"
  "move-code-to-allowed-layer-or-update-allowlist-policy"
  "restore-allowed-module-dependencies-or-update-whitelist"
  "move-public-contracts-to-approved-api-boundaries"
  "restore-tiered-workflow-structure-and-governance-guards"
)

CHECK_SOURCE_OF_TRUTH=(
  "scripts/quality/verify_no_tracked_keystore_files.sh"
  "app/build.gradle.kts,app/src/debug,scripts/quality/verify_debug_mock_network.py"
  "scripts/quality/verify_release_exported_components.sh"
  "scripts/quality/verify_vendor_sdk_release_readiness.sh"
  "scripts/lint/lint_warning_waivers.json"
  "app/lint.xml"
  "scripts/quality/verify_jetpack_compat_apis.sh"
  "scripts/quality/verify_baselineprofile_journeys.sh"
  "scripts/quality/verify_cancellation_guards.sh"
  "scripts/quality/verify_no_empty_catch_blocks.sh"
  "settings.gradle.kts,.github/workflows/android-ci.yml"
  "settings.gradle.kts,constants.gradle.kts,gradle/libs.versions.toml"
  "scripts/quality/dependency_preview_allowlist.txt"
  "scripts/quality/target_sdk_readiness.properties"
  "scripts/quality/target_platform_test_matrix.properties"
  "scripts/quality/verify_instrumentation_smoke_classes.sh"
  "docs/architecture/tech-stack.md"
  "app/src/main/AndroidManifest.xml"
  "scripts/quality/verify_architecture_boundaries.sh"
  "scripts/quality/module_dependency_allowlist.txt"
  "scripts/quality/verify_module_api_visibility.sh"
  "scripts/quality/verify_ci_workflow_quality.sh"
)

if [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_CMDS[@]} ]] || \
  [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_IDS[@]} ]] || \
  [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_TIERS[@]} ]] || \
  [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_CATEGORIES[@]} ]] || \
  [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_LIKELY_FIXES[@]} ]] || \
  [[ ${#CHECK_NAMES[@]} -ne ${#CHECK_SOURCE_OF_TRUTH[@]} ]]; then
  echo "[quality-snapshot][FAIL] quality check metadata arrays are out of sync." >&2
  exit 1
fi

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

if [[ "${REGISTRY_FILE}" = /* ]]; then
  REGISTRY_FILE_PATH="${REGISTRY_FILE}"
else
  REGISTRY_FILE_PATH="${PROJECT_ROOT}/${REGISTRY_FILE}"
fi

registry_gate_field() {
  local gate_id="$1"
  local field="$2"

  if [[ ! -f "${REGISTRY_FILE_PATH}" ]]; then
    return 1
  fi

  jq -r --arg id "${gate_id}" --arg field "${field}" '
    .gates[]
    | select(.id == $id)
    | .[$field] // empty
  ' "${REGISTRY_FILE_PATH}" | head -n 1
}

resolve_gate_metadata() {
  local gate_id="$1"
  local default_layer="$2"
  local default_source_of_truth="$3"
  local default_likely_fix="$4"

  local registered
  local owner
  local layer
  local source_of_truth
  local likely_fix

  registered="false"
  owner="$(registry_gate_field "${gate_id}" "owner" || true)"
  layer="$(registry_gate_field "${gate_id}" "layer" || true)"
  source_of_truth="$(registry_gate_field "${gate_id}" "source_of_truth" || true)"
  likely_fix="$(registry_gate_field "${gate_id}" "likely_fix" || true)"

  if [[ -n "${owner}" || -n "${layer}" || -n "${source_of_truth}" || -n "${likely_fix}" ]]; then
    registered="true"
  fi

  if [[ "${registered}" != "true" ]]; then
    owner=""
    layer=""
    source_of_truth="${default_source_of_truth}"
    likely_fix="${default_likely_fix}"
  else
    if [[ -z "${layer}" ]]; then
      layer="${default_layer}"
    fi
    if [[ -z "${source_of_truth}" ]]; then
      source_of_truth="${default_source_of_truth}"
    fi
    if [[ -z "${likely_fix}" ]]; then
      likely_fix="${default_likely_fix}"
    fi
  fi

  printf '%s\t%s\t%s\t%s\t%s\n' "${registered}" "${owner}" "${layer}" "${source_of_truth}" "${likely_fix}"
}

OVERALL_EXIT=0

for i in "${!CHECK_NAMES[@]}"; do
  ID="${CHECK_IDS[$i]}"
  NAME="${CHECK_NAMES[$i]}"
  CMD="${CHECK_CMDS[$i]}"
  TIER="${CHECK_TIERS[$i]}"
  CATEGORY="${CHECK_CATEGORIES[$i]}"
  REGISTRY_METADATA="$(resolve_gate_metadata "${ID}" "${TIER}" "${CHECK_SOURCE_OF_TRUTH[$i]}" "${CHECK_LIKELY_FIXES[$i]}")"
  REGISTRY_REGISTERED="$(printf '%s' "${REGISTRY_METADATA}" | cut -f1)"
  OWNER="$(printf '%s' "${REGISTRY_METADATA}" | cut -f2)"
  LAYER="$(printf '%s' "${REGISTRY_METADATA}" | cut -f3)"
  SOURCE_OF_TRUTH="$(printf '%s' "${REGISTRY_METADATA}" | cut -f4)"
  LIKELY_FIX="$(printf '%s' "${REGISTRY_METADATA}" | cut -f5)"
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
    --arg id "${ID}" \
    --arg name "${NAME}" \
    --arg owner "${OWNER}" \
    --arg layer "${LAYER}" \
    --argjson registry_registered "$(if [[ "${REGISTRY_REGISTERED}" == "true" ]]; then echo true; else echo false; fi)" \
    --arg tier "${TIER}" \
    --arg category "${CATEGORY}" \
    --arg likely_fix "${LIKELY_FIX}" \
    --arg source_of_truth "${SOURCE_OF_TRUTH}" \
    --arg command "${CMD}" \
    --arg status "${STATUS}" \
    --arg log "${LOG_FILE}" \
    --arg started_at "$(epoch_to_iso_utc "${START_EPOCH}")" \
    --arg finished_at "$(epoch_to_iso_utc "${END_EPOCH}")" \
    --argjson exit_code "${EXIT_CODE}" \
    --argjson duration_seconds "${DURATION}" \
    '{
      id: $id,
      name: $name,
      owner: $owner,
      layer: $layer,
      registry_registered: $registry_registered,
      tier: $tier,
      category: $category,
      likely_fix: $likely_fix,
      source_of_truth: $source_of_truth,
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
  echo "| Check | Tier | Category | Status | Likely Fix | Source Of Truth | Duration (s) | Exit Code | Log |"
  echo "|---|---|---|---|---|---|---:|---:|---|"
  jq -r '.[] | "| \(.name) | \(.tier) | \(.category) | \(.status) | \(.likely_fix) | `\(.source_of_truth)` | \(.duration_seconds) | \(.exit_code) | `\(.log)` |"' "${CHECKS_ARRAY_JSON}"
  if [[ "${FAILED_CHECKS}" -gt 0 ]]; then
    echo
    echo "## Failed Check Diagnostics"
    jq -r '
      .[]
      | select(.status == "FAIL")
      | if .registry_registered then
          "- \(.name): owner=`\(.owner)`, layer=`\(.layer)`, source_of_truth=`\(.source_of_truth)`, likely_fix=\(.likely_fix)"
        else
          "- \(.name): registry_entry=`missing` (layer/owner/source_of_truth/likely_fix unavailable in registry)"
        end
    ' "${CHECKS_ARRAY_JSON}"
  fi
  echo
  echo "JSON report: \`${REPORT_JSON}\`"
} > "${REPORT_MD}"

echo "[quality-snapshot] markdown report: ${REPORT_MD}"
echo "[quality-snapshot] json report: ${REPORT_JSON}"

exit "${OVERALL_EXIT}"
