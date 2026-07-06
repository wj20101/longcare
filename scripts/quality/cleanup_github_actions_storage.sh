#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: cleanup_github_actions_storage.sh --repo OWNER/REPO [options]

Options:
  --repo OWNER/REPO             Repository to inspect and clean. Required.
  --run-keep-days N             Keep completed workflow runs newer than N days. Default: 7.
  --artifact-keep-days N        Keep artifacts newer than N days. Default: 2.
  --cache-max-total-mb N        Maximum allowed total cache size in MB. Default: 2048.
  --cache-keep-recent-days N    Protect caches created or accessed within N days. Default: 1.
  --dry-run                     Print deletion candidates without deleting.
  --help                        Show this help.
USAGE
}

repo=""
run_keep_days=7
artifact_keep_days=2
cache_max_total_mb=2048
cache_keep_recent_days=1
dry_run=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo="${2:-}"
      shift 2
      ;;
    --run-keep-days)
      run_keep_days="${2:-}"
      shift 2
      ;;
    --artifact-keep-days)
      artifact_keep_days="${2:-}"
      shift 2
      ;;
    --cache-max-total-mb)
      cache_max_total_mb="${2:-}"
      shift 2
      ;;
    --cache-keep-recent-days)
      cache_keep_recent_days="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_non_negative_int() {
  local name="$1"
  local value="$2"

  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "Invalid ${name}: ${value}" >&2
    exit 1
  fi
}

require_positive_int() {
  local name="$1"
  local value="$2"

  require_non_negative_int "${name}" "${value}"
  if [[ "${value}" -le 0 ]]; then
    echo "Invalid ${name}: ${value}" >&2
    exit 1
  fi
}

if [[ -z "${repo}" ]]; then
  echo "--repo OWNER/REPO is required." >&2
  usage >&2
  exit 1
fi

require_non_negative_int "--run-keep-days" "${run_keep_days}"
require_non_negative_int "--artifact-keep-days" "${artifact_keep_days}"
require_positive_int "--cache-max-total-mb" "${cache_max_total_mb}"
require_non_negative_int "--cache-keep-recent-days" "${cache_keep_recent_days}"

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required and must have actions:write permission." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required." >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cache_cleanup_script="${script_dir}/cleanup_github_actions_caches.sh"

if [[ ! -x "${cache_cleanup_script}" ]]; then
  echo "Cache cleanup script is missing or not executable: ${cache_cleanup_script}" >&2
  exit 1
fi

format_mb() {
  local bytes="$1"
  awk -v bytes="${bytes}" 'BEGIN { printf "%.2f", bytes / 1024 / 1024 }'
}

days_ago_epoch() {
  local days="$1"
  local epoch=""

  if epoch="$(date -u -d "${days} days ago" +%s 2>/dev/null)"; then
    echo "${epoch}"
    return
  fi

  date -u -v-"${days}"d +%s
}

date_to_epoch() {
  local value="$1"
  local normalized="${value}"
  local epoch=""

  if epoch="$(date -u -d "${value}" +%s 2>/dev/null)"; then
    echo "${epoch}"
    return
  fi

  if [[ "${normalized}" == *.*Z ]]; then
    normalized="${normalized%%.*}Z"
  fi

  date -u -j -f "%Y-%m-%dT%H:%M:%SZ" "${normalized}" +%s
}

emit_summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    tee -a "${GITHUB_STEP_SUMMARY}"
  else
    cat
  fi
}

delete_old_runs() {
  local cutoff_epoch
  cutoff_epoch="$(days_ago_epoch "${run_keep_days}")"

  local scanned=0
  local candidates=0
  local deleted=0
  local failed=0

  echo "[actions-storage-cleanup] Listing completed workflow runs for ${repo}..."

  while IFS=$'\t' read -r run_id created_at workflow_name conclusion html_url; do
    [[ -n "${run_id}" ]] || continue
    scanned=$((scanned + 1))

    local created_epoch
    created_epoch="$(date_to_epoch "${created_at}")"

    if (( created_epoch >= cutoff_epoch )); then
      continue
    fi

    candidates=$((candidates + 1))

    if [[ "${dry_run}" == "true" ]]; then
      echo "[DRY-RUN] would delete run=${run_id} workflow=${workflow_name} conclusion=${conclusion} created_at=${created_at} url=${html_url}"
      continue
    fi

    if gh run delete -R "${repo}" "${run_id}"; then
      deleted=$((deleted + 1))
      echo "[actions-storage-cleanup] Deleted run=${run_id} workflow=${workflow_name} created_at=${created_at}"
    else
      failed=$((failed + 1))
      echo "[actions-storage-cleanup][WARN] Failed to delete run=${run_id} workflow=${workflow_name}" >&2
    fi
  done < <(
    gh api --paginate "repos/${repo}/actions/runs?status=completed&per_page=100" \
      --jq '.workflow_runs[]? | [.id,.created_at,.name,.conclusion,.html_url] | @tsv'
  )

  {
    echo "# GitHub Actions Run Cleanup Summary"
    echo ""
    echo "- repo: \`${repo}\`"
    echo "- dry_run: \`${dry_run}\`"
    echo "- run_keep_days: \`${run_keep_days}\`"
    echo "- scanned_runs: \`${scanned}\`"
    echo "- older_than_window: \`${candidates}\`"
    if [[ "${dry_run}" == "true" ]]; then
      echo "- would_delete_runs: \`${candidates}\`"
    else
      echo "- deleted_runs: \`${deleted}\`"
      echo "- failed_run_deletions: \`${failed}\`"
    fi
    echo ""
  } | emit_summary

  if (( failed > 0 )); then
    return 1
  fi
}

delete_old_artifacts() {
  local cutoff_epoch
  cutoff_epoch="$(days_ago_epoch "${artifact_keep_days}")"

  local scanned=0
  local candidates=0
  local deleted=0
  local failed=0
  local candidate_bytes=0
  local reclaimed_bytes=0

  echo "[actions-storage-cleanup] Listing artifacts for ${repo}..."

  while IFS=$'\t' read -r artifact_id name size_bytes created_at expired archive_url; do
    [[ -n "${artifact_id}" ]] || continue
    scanned=$((scanned + 1))
    size_bytes="${size_bytes:-0}"

    local created_epoch
    created_epoch="$(date_to_epoch "${created_at}")"

    if [[ "${expired}" != "true" ]] && (( created_epoch >= cutoff_epoch )); then
      continue
    fi

    candidates=$((candidates + 1))
    candidate_bytes=$((candidate_bytes + size_bytes))

    if [[ "${dry_run}" == "true" ]]; then
      echo "[DRY-RUN] would delete artifact=${artifact_id} name=${name} size=$(format_mb "${size_bytes}")MB expired=${expired} created_at=${created_at} url=${archive_url}"
      continue
    fi

    if gh api -X DELETE "repos/${repo}/actions/artifacts/${artifact_id}" >/dev/null; then
      deleted=$((deleted + 1))
      reclaimed_bytes=$((reclaimed_bytes + size_bytes))
      echo "[actions-storage-cleanup] Deleted artifact=${artifact_id} name=${name} size=$(format_mb "${size_bytes}")MB created_at=${created_at}"
    else
      failed=$((failed + 1))
      echo "[actions-storage-cleanup][WARN] Failed to delete artifact=${artifact_id} name=${name}" >&2
    fi
  done < <(
    gh api --paginate "repos/${repo}/actions/artifacts?per_page=100" \
      --jq '.artifacts[]? | [.id,.name,.size_in_bytes,.created_at,.expired,.archive_download_url] | @tsv'
  )

  {
    echo "# GitHub Actions Artifact Cleanup Summary"
    echo ""
    echo "- repo: \`${repo}\`"
    echo "- dry_run: \`${dry_run}\`"
    echo "- artifact_keep_days: \`${artifact_keep_days}\`"
    echo "- scanned_artifacts: \`${scanned}\`"
    echo "- deletion_candidates: \`${candidates}\`"
    echo "- candidate_size_mb: \`$(format_mb "${candidate_bytes}")\`"
    if [[ "${dry_run}" == "true" ]]; then
      echo "- would_delete_artifacts: \`${candidates}\`"
      echo "- would_reclaim_mb: \`$(format_mb "${candidate_bytes}")\`"
    else
      echo "- deleted_artifacts: \`${deleted}\`"
      echo "- failed_artifact_deletions: \`${failed}\`"
      echo "- reclaimed_mb: \`$(format_mb "${reclaimed_bytes}")\`"
    fi
    echo ""
  } | emit_summary

  if (( failed > 0 )); then
    return 1
  fi
}

cleanup_caches() {
  local cache_args=(
    --repo "${repo}"
    --max-total-mb "${cache_max_total_mb}"
    --keep-recent-days "${cache_keep_recent_days}"
  )

  if [[ "${dry_run}" == "true" ]]; then
    cache_args+=(--dry-run)
  fi

  "${cache_cleanup_script}" "${cache_args[@]}"
}

delete_old_runs
delete_old_artifacts
cleanup_caches
