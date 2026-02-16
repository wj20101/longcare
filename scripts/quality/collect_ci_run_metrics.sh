#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-yyg20101/longcare}"
LIMIT="${2:-50}"

if ! command -v gh >/dev/null 2>&1; then
  echo "[ci-metrics][FAIL] gh CLI is required."
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[ci-metrics][FAIL] jq is required."
  exit 1
fi

if ! [[ "${LIMIT}" =~ ^[0-9]+$ ]] || [[ "${LIMIT}" -le 0 ]]; then
  echo "[ci-metrics][FAIL] limit must be a positive integer."
  exit 1
fi

# Prefer project PAT when available while keeping compatibility with GH CLI token env.
if [[ -n "${GITHUB_PAT_TOKEN:-}" && -z "${GH_TOKEN:-}" ]]; then
  export GH_TOKEN="${GITHUB_PAT_TOKEN}"
fi

if ! gh auth status -h github.com >/dev/null 2>&1; then
  if [[ -z "${GH_TOKEN:-}" ]]; then
    echo "[ci-metrics][FAIL] GitHub authentication missing."
    echo "[ci-metrics][FAIL] run gh auth login or set GH_TOKEN/GITHUB_PAT_TOKEN."
    exit 1
  fi
  echo "[ci-metrics][INFO] gh auth session not detected; using GH_TOKEN from environment."
fi

set +e
RUNS_JSON="$(gh run list -R "${REPO}" --limit "${LIMIT}" --json workflowName,status,conclusion,createdAt,updatedAt 2>&1)"
GH_EXIT=$?
set -e

if [[ "${GH_EXIT}" -ne 0 ]]; then
  echo "[ci-metrics][FAIL] failed to fetch runs for ${REPO}."
  echo "[ci-metrics][FAIL] ensure token has Actions read permissions."
  echo "[ci-metrics][DETAIL] ${RUNS_JSON}"
  exit 1
fi

if [[ -z "${RUNS_JSON}" || "${RUNS_JSON}" == "[]" ]]; then
  echo "[ci-metrics][WARN] no runs found for ${REPO}."
  exit 0
fi

echo "CI run metrics baseline (repo=${REPO}, sample=${LIMIT})"
echo "| Workflow | Runs | Success | Failure | Cancelled | Success % | Avg Duration (s) |"
echo "|---|---:|---:|---:|---:|---:|---:|"

echo "${RUNS_JSON}" | jq -r '
  sort_by(.workflowName)
  | group_by(.workflowName)[]
  | . as $runs
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
  | ($durations | length) as $duration_count
  | (if $duration_count > 0 then (($durations | add) / $duration_count) else 0 end) as $avg_seconds
  | [.[0].workflowName, $total, $success, $failure, $cancelled, $avg_seconds]
  | @tsv
' | while IFS=$'\t' read -r workflow total success failure cancelled avg_seconds; do
  if [[ "${total}" -gt 0 ]]; then
    success_rate="$(awk -v s="${success}" -v t="${total}" 'BEGIN { printf "%.1f", (s * 100.0) / t }')"
  else
    success_rate="0.0"
  fi
  avg_seconds_int="$(awk -v avg="${avg_seconds}" 'BEGIN { printf "%.0f", avg }')"
  echo "| ${workflow} | ${total} | ${success} | ${failure} | ${cancelled} | ${success_rate} | ${avg_seconds_int} |"
done

echo "[ci-metrics][PASS] baseline generated."
