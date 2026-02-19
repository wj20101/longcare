#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-yyg20101/longcare}"
LIMIT="${2:-50}"
THRESHOLD_FILE="${3:-scripts/quality/ci_health_thresholds.json}"
OUTPUT_DIR="${4:-build/ci-health}"
RUNS_JSON_FILE="${5:-}"
BRANCH="${6:-}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ci-health][FAIL] required command missing: $1"
    exit 1
  fi
}

is_positive_int() {
  [[ "$1" =~ ^[0-9]+$ ]] && [[ "$1" -gt 0 ]]
}

is_less_than() {
  awk -v lhs="$1" -v rhs="$2" 'BEGIN { exit !(lhs < rhs) }'
}

is_greater_than() {
  awk -v lhs="$1" -v rhs="$2" 'BEGIN { exit !(lhs > rhs) }'
}

require_cmd jq
require_cmd awk
require_cmd sed

if ! is_positive_int "${LIMIT}"; then
  echo "[ci-health][FAIL] limit must be a positive integer, got: ${LIMIT}"
  exit 1
fi

if [[ ! -f "${THRESHOLD_FILE}" ]]; then
  echo "[ci-health][FAIL] threshold file not found: ${THRESHOLD_FILE}"
  exit 1
fi

if ! jq empty "${THRESHOLD_FILE}" >/dev/null 2>&1; then
  echo "[ci-health][FAIL] threshold file is not valid JSON: ${THRESHOLD_FILE}"
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
METRICS_FILE="${OUTPUT_DIR}/ci_health_metrics.json"
REPORT_FILE="${OUTPUT_DIR}/ci_health_report.md"
VIOLATIONS_FILE="${OUTPUT_DIR}/ci_health_violations.txt"

if [[ -n "${RUNS_JSON_FILE}" ]]; then
  if [[ ! -f "${RUNS_JSON_FILE}" ]]; then
    echo "[ci-health][FAIL] runs json file not found: ${RUNS_JSON_FILE}"
    exit 1
  fi
  RUNS_JSON="$(cat "${RUNS_JSON_FILE}")"
else
  require_cmd gh

  if [[ -n "${GITHUB_PAT_TOKEN:-}" && -z "${GH_TOKEN:-}" ]]; then
    export GH_TOKEN="${GITHUB_PAT_TOKEN}"
  fi

  if ! gh auth status -h github.com >/dev/null 2>&1; then
    if [[ -z "${GH_TOKEN:-}" ]]; then
      echo "[ci-health][FAIL] GitHub authentication missing."
      echo "[ci-health][FAIL] run gh auth login or set GH_TOKEN/GITHUB_PAT_TOKEN."
      exit 1
    fi
    echo "[ci-health][INFO] gh auth session not detected; using GH_TOKEN from environment."
  fi

  RUN_LIST_ARGS=(
    run list
    -R "${REPO}"
    --limit "${LIMIT}"
    --json workflowName,status,conclusion,createdAt,updatedAt,url
  )
  if [[ -n "${BRANCH}" ]]; then
    RUN_LIST_ARGS+=(--branch "${BRANCH}")
  fi

  set +e
  RUNS_JSON="$(gh "${RUN_LIST_ARGS[@]}" 2>&1)"
  GH_EXIT=$?
  set -e

  if [[ "${GH_EXIT}" -ne 0 ]]; then
    echo "[ci-health][FAIL] failed to fetch runs for ${REPO}."
    echo "[ci-health][DETAIL] ${RUNS_JSON}"
    exit 1
  fi
fi

if [[ -z "${RUNS_JSON}" || "${RUNS_JSON}" == "[]" ]]; then
  cat > "${REPORT_FILE}" <<EOF
# CI health report

- repo: \`${REPO}\`
- sample size: \`${LIMIT}\`
- result: no runs found
EOF
  cat > "${METRICS_FILE}" <<EOF
{"repo":"${REPO}","sample_size":${LIMIT},"overall":{"runs":0},"workflows":[]}
EOF
  : > "${VIOLATIONS_FILE}"
  echo "[ci-health][WARN] no runs found for ${REPO}."
  cat "${REPORT_FILE}"
  exit 0
fi

TARGET_WORKFLOWS_JSON="$(jq -c '.workflows | keys // []' "${THRESHOLD_FILE}")"

echo "${RUNS_JSON}" | jq -c \
  --arg repo "${REPO}" \
  --arg branch "${BRANCH}" \
  --argjson limit "${LIMIT}" \
  --argjson target_workflows "${TARGET_WORKFLOWS_JSON}" '
  def pct($p; $t):
    if $t > 0 then (($p * 10000 / $t) | round / 100) else 0 end;
  def filter_workflows($runs; $targets):
    if ($targets | length) > 0
    then ($runs | map(select(.workflowName as $wf | $targets | index($wf))))
    else $runs
    end;
  . as $all
  | (filter_workflows($all; $target_workflows)) as $filtered
  | {
      repo: $repo,
      branch: $branch,
      sample_size: $limit,
      generated_at: (now | todateiso8601),
      workflows: (
        $filtered
        | sort_by(.workflowName)
        | group_by(.workflowName)
        | map(
            . as $runs
            | ($runs | length) as $total
            | ($runs | map(select(.conclusion == "success")) | length) as $success
            | ($runs | map(select(.conclusion == "failure")) | length) as $failure
            | ($runs | map(select(.conclusion == "cancelled")) | length) as $cancelled
            | (
                $runs
                | map(
                    select(.status == "completed" and .createdAt != null and .updatedAt != null)
                    | ((.updatedAt | fromdateiso8601) - (.createdAt | fromdateiso8601))
                  )
              ) as $durations
            | ($durations | length) as $duration_runs
            | ($durations | add // 0) as $total_duration_seconds
            | {
                workflow: (.[0].workflowName),
                runs: $total,
                success: $success,
                failure: $failure,
                cancelled: $cancelled,
                non_cancelled_runs: ($success + $failure),
                success_rate: pct($success; $total),
                non_cancelled_success_rate: pct($success; ($success + $failure)),
                failure_rate: pct($failure; $total),
                cancelled_rate: pct($cancelled; $total),
                duration_runs: $duration_runs,
                total_duration_seconds: $total_duration_seconds,
                avg_duration_seconds: (
                  if $duration_runs > 0
                  then (($total_duration_seconds / $duration_runs) | round)
                  else 0
                  end
                )
              }
          )
      )
    }
  | .overall = (
      .workflows as $wf
      | ($wf | map(.runs) | add // 0) as $runs
      | ($wf | map(.success) | add // 0) as $success
      | ($wf | map(.failure) | add // 0) as $failure
      | ($wf | map(.cancelled) | add // 0) as $cancelled
      | ($wf | map(.duration_runs) | add // 0) as $duration_runs
      | ($wf | map(.total_duration_seconds) | add // 0) as $total_duration_seconds
      | {
          runs: $runs,
          success: $success,
          failure: $failure,
          cancelled: $cancelled,
          non_cancelled_runs: ($success + $failure),
          success_rate: pct($success; $runs),
          non_cancelled_success_rate: pct($success; ($success + $failure)),
          failure_rate: pct($failure; $runs),
          cancelled_rate: pct($cancelled; $runs),
          duration_runs: $duration_runs,
          total_duration_seconds: $total_duration_seconds,
          avg_duration_seconds: (
            if $duration_runs > 0
            then (($total_duration_seconds / $duration_runs) | round)
            else 0
            end
          )
        }
    )
' > "${METRICS_FILE}"

declare -a WARNINGS=()
declare -a VIOLATIONS=()

OVERALL_RUNS="$(jq -r '.overall.runs // 0' "${METRICS_FILE}")"
OVERALL_NON_CANCELLED_SUCCESS_RATE="$(jq -r '.overall.non_cancelled_success_rate // 0' "${METRICS_FILE}")"
OVERALL_CANCELLED_RATE="$(jq -r '.overall.cancelled_rate // 0' "${METRICS_FILE}")"

OVERALL_MIN_SUCCESS_RATE="$(jq -r '.overall.min_success_rate // empty' "${THRESHOLD_FILE}")"
OVERALL_MAX_CANCELLED_RATE="$(jq -r '.overall.max_cancelled_rate // empty' "${THRESHOLD_FILE}")"
OVERALL_ENFORCE_CANCELLED_RATE="$(jq -r '.overall.enforce_cancelled_rate // false' "${THRESHOLD_FILE}")"

if [[ "${OVERALL_RUNS}" == "0" ]]; then
  WARNINGS+=("no target workflow runs found in sample; overall threshold checks skipped")
else
  if [[ -n "${OVERALL_MIN_SUCCESS_RATE}" ]] && is_less_than "${OVERALL_NON_CANCELLED_SUCCESS_RATE}" "${OVERALL_MIN_SUCCESS_RATE}"; then
    VIOLATIONS+=("overall non_cancelled_success_rate ${OVERALL_NON_CANCELLED_SUCCESS_RATE}% < min_success_rate ${OVERALL_MIN_SUCCESS_RATE}%")
  fi

  if [[ -n "${OVERALL_MAX_CANCELLED_RATE}" ]] && is_greater_than "${OVERALL_CANCELLED_RATE}" "${OVERALL_MAX_CANCELLED_RATE}"; then
    if [[ "${OVERALL_ENFORCE_CANCELLED_RATE}" == "true" ]]; then
      VIOLATIONS+=("overall cancelled_rate ${OVERALL_CANCELLED_RATE}% > max_cancelled_rate ${OVERALL_MAX_CANCELLED_RATE}%")
    else
      WARNINGS+=("overall cancelled_rate ${OVERALL_CANCELLED_RATE}% > max_cancelled_rate ${OVERALL_MAX_CANCELLED_RATE}%")
    fi
  fi
fi

while IFS= read -r WORKFLOW_NAME; do
  [[ -z "${WORKFLOW_NAME}" ]] && continue

  WORKFLOW_COUNT="$(jq -r --arg wf "${WORKFLOW_NAME}" '[.workflows[] | select(.workflow == $wf)] | length' "${METRICS_FILE}")"
  if [[ "${WORKFLOW_COUNT}" == "0" ]]; then
    WARNINGS+=("workflow '${WORKFLOW_NAME}' not found in recent sample")
    continue
  fi

  WF_RUNS="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .runs' "${METRICS_FILE}")"
  WF_SUCCESS_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .success_rate' "${METRICS_FILE}")"
  WF_NON_CANCELLED_SUCCESS_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .non_cancelled_success_rate' "${METRICS_FILE}")"
  WF_FAILURE_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .failure_rate' "${METRICS_FILE}")"
  WF_CANCELLED_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .cancelled_rate' "${METRICS_FILE}")"
  WF_AVG_DURATION="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[] | select(.workflow == $wf) | .avg_duration_seconds' "${METRICS_FILE}")"

  WF_MIN_RUNS="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].min_runs // empty' "${THRESHOLD_FILE}")"
  WF_MIN_SUCCESS_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].min_success_rate // empty' "${THRESHOLD_FILE}")"
  WF_MAX_FAILURE_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].max_failure_rate // empty' "${THRESHOLD_FILE}")"
  WF_MAX_CANCELLED_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].max_cancelled_rate // empty' "${THRESHOLD_FILE}")"
  WF_ENFORCE_CANCELLED_RATE="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].enforce_cancelled_rate // false' "${THRESHOLD_FILE}")"
  WF_MAX_AVG_DURATION="$(jq -r --arg wf "${WORKFLOW_NAME}" '.workflows[$wf].max_avg_duration_seconds // empty' "${THRESHOLD_FILE}")"

  if [[ -n "${WF_MIN_RUNS}" ]] && is_less_than "${WF_RUNS}" "${WF_MIN_RUNS}"; then
    WARNINGS+=("${WORKFLOW_NAME} runs ${WF_RUNS} < min_runs ${WF_MIN_RUNS} (sample too small)")
  fi

  if [[ -n "${WF_MIN_SUCCESS_RATE}" ]] && is_less_than "${WF_NON_CANCELLED_SUCCESS_RATE}" "${WF_MIN_SUCCESS_RATE}"; then
    VIOLATIONS+=("${WORKFLOW_NAME} non_cancelled_success_rate ${WF_NON_CANCELLED_SUCCESS_RATE}% < min_success_rate ${WF_MIN_SUCCESS_RATE}%")
  fi

  if [[ -n "${WF_MAX_FAILURE_RATE}" ]] && is_greater_than "${WF_FAILURE_RATE}" "${WF_MAX_FAILURE_RATE}"; then
    VIOLATIONS+=("${WORKFLOW_NAME} failure_rate ${WF_FAILURE_RATE}% > max_failure_rate ${WF_MAX_FAILURE_RATE}%")
  fi

  if [[ -n "${WF_MAX_CANCELLED_RATE}" ]] && is_greater_than "${WF_CANCELLED_RATE}" "${WF_MAX_CANCELLED_RATE}"; then
    if [[ "${WF_ENFORCE_CANCELLED_RATE}" == "true" ]]; then
      VIOLATIONS+=("${WORKFLOW_NAME} cancelled_rate ${WF_CANCELLED_RATE}% > max_cancelled_rate ${WF_MAX_CANCELLED_RATE}%")
    else
      WARNINGS+=("${WORKFLOW_NAME} cancelled_rate ${WF_CANCELLED_RATE}% > max_cancelled_rate ${WF_MAX_CANCELLED_RATE}%")
    fi
  fi

  if [[ -n "${WF_MAX_AVG_DURATION}" ]] && is_greater_than "${WF_AVG_DURATION}" "${WF_MAX_AVG_DURATION}"; then
    VIOLATIONS+=("${WORKFLOW_NAME} avg_duration_seconds ${WF_AVG_DURATION}s > max_avg_duration_seconds ${WF_MAX_AVG_DURATION}s")
  fi
done < <(jq -r '.workflows | keys[]' "${THRESHOLD_FILE}")

{
  echo "# CI health report"
  echo ""
  echo "- repo: \`${REPO}\`"
  echo "- sample size: \`${LIMIT}\`"
  if [[ -n "${BRANCH}" ]]; then
    echo "- branch: \`${BRANCH}\`"
  fi
  echo "- generated_at: \`$(jq -r '.generated_at' "${METRICS_FILE}")\`"
  echo ""
  echo "## Overall"
  echo ""
  echo "| Runs | Success % | Non-cancelled Success % | Failure % | Cancelled % | Avg Duration (s) |"
  echo "|---:|---:|---:|---:|---:|---:|"
  jq -r '"| \(.overall.runs) | \(.overall.success_rate) | \(.overall.non_cancelled_success_rate) | \(.overall.failure_rate) | \(.overall.cancelled_rate) | \(.overall.avg_duration_seconds) |"' "${METRICS_FILE}"
  echo ""
  echo "## Workflow metrics"
  echo ""
  echo "| Workflow | Runs | Success % | Non-cancelled Success % | Failure % | Cancelled % | Avg Duration (s) |"
  echo "|---|---:|---:|---:|---:|---:|---:|"
  jq -r '.workflows[] | "| \(.workflow) | \(.runs) | \(.success_rate) | \(.non_cancelled_success_rate) | \(.failure_rate) | \(.cancelled_rate) | \(.avg_duration_seconds) |"' "${METRICS_FILE}"
  echo ""
  echo "## Threshold evaluation"
  echo ""
  if [[ "${#WARNINGS[@]}" -gt 0 ]]; then
    echo "### Warnings"
    echo ""
    for item in "${WARNINGS[@]}"; do
      echo "- ${item}"
    done
    echo ""
  fi
  if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
    echo "### Violations"
    echo ""
    for item in "${VIOLATIONS[@]}"; do
      echo "- ${item}"
    done
    echo ""
    echo "**Status:** FAIL"
  else
    echo "**Status:** PASS"
  fi
} > "${REPORT_FILE}"

if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
  printf '%s\n' "${VIOLATIONS[@]}" > "${VIOLATIONS_FILE}"
else
  : > "${VIOLATIONS_FILE}"
fi

cat "${REPORT_FILE}"

if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
  echo "[ci-health][FAIL] threshold violations detected."
  exit 2
fi

echo "[ci-health][PASS] threshold checks passed."
