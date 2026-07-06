#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: cleanup_github_actions_caches.sh --repo OWNER/REPO [options]

Options:
  --repo OWNER/REPO       Repository to inspect and clean. Required.
  --max-total-mb N        Maximum allowed total cache size in MB. Default: 2048.
  --keep-recent-days N    Protect caches created or accessed within N days. Default: 1.
  --dry-run               Print deletion candidates without deleting.
  --help                  Show this help.
USAGE
}

repo=""
max_total_mb=2048
keep_recent_days=1
dry_run=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo="${2:-}"
      shift 2
      ;;
    --max-total-mb)
      max_total_mb="${2:-}"
      shift 2
      ;;
    --keep-recent-days)
      keep_recent_days="${2:-}"
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

if [[ -z "${repo}" ]]; then
  echo "--repo OWNER/REPO is required." >&2
  usage >&2
  exit 1
fi

if ! [[ "${max_total_mb}" =~ ^[0-9]+$ ]] || [[ "${max_total_mb}" -le 0 ]]; then
  echo "Invalid --max-total-mb: ${max_total_mb}" >&2
  exit 1
fi

if ! [[ "${keep_recent_days}" =~ ^[0-9]+$ ]]; then
  echo "Invalid --keep-recent-days: ${keep_recent_days}" >&2
  exit 1
fi

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required and must have actions:write permission." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

threshold_bytes=$((max_total_mb * 1024 * 1024))
now_epoch="$(date -u +%s)"
cutoff_epoch=$((now_epoch - keep_recent_days * 24 * 60 * 60))
cache_json_file="$(mktemp)"
candidate_tsv_file="$(mktemp)"
summary_file="$(mktemp)"

cleanup() {
  rm -f "${cache_json_file}" "${candidate_tsv_file}" "${summary_file}"
}
trap cleanup EXIT

echo "[actions-cache-cleanup] Listing caches for ${repo}..."
gh api --paginate "repos/${repo}/actions/caches?per_page=100" \
  --jq '.actions_caches[]?' > "${cache_json_file}"

scanned_count="$(jq -s 'length' "${cache_json_file}")"
total_bytes="$(jq -s '[.[].size_in_bytes] | add // 0' "${cache_json_file}")"

jq -r -s --argjson cutoff "${cutoff_epoch}" '
  def clean_time:
    sub("\\.[0-9]+Z$"; "Z");
  def epoch:
    clean_time | fromdateiso8601;

  [
    .[]
    | .created_epoch = (.created_at | epoch)
    | .last_accessed_epoch = ((.last_accessed_at // .created_at) | epoch)
  ]
  | sort_by(.last_accessed_epoch, .created_epoch)
  | .[]
  | [
      .id,
      .key,
      .ref,
      .size_in_bytes,
      .created_at,
      (.last_accessed_at // .created_at),
      .created_epoch,
      .last_accessed_epoch
    ]
  | @tsv
' "${cache_json_file}" > "${candidate_tsv_file}"

scanned_sorted_count="$(wc -l < "${candidate_tsv_file}" | tr -d ' ')"
remaining_bytes="${total_bytes}"
deleted_count=0
failed_count=0
reclaimed_bytes=0
would_delete_count=0
would_reclaim_bytes=0
deletion_candidate_count=0
stale_candidate_count=0
capacity_candidate_count=0

format_mb() {
  local bytes="$1"
  awk -v bytes="${bytes}" 'BEGIN { printf "%.2f", bytes / 1024 / 1024 }'
}

echo "[actions-cache-cleanup] total=$(format_mb "${total_bytes}")MB threshold=${max_total_mb}MB scanned=${scanned_count} sorted=${scanned_sorted_count} dry_run=${dry_run}"

if (( total_bytes <= threshold_bytes )); then
  echo "[actions-cache-cleanup] Cache total is below threshold; nothing to delete."
else
  while IFS=$'\t' read -r cache_id cache_key cache_ref size_bytes created_at last_accessed_at created_epoch last_accessed_epoch; do
    [[ -n "${cache_id}" ]] || continue
    if (( remaining_bytes <= threshold_bytes )); then
      break
    fi

    reason="over_capacity"
    if (( created_epoch < cutoff_epoch && last_accessed_epoch < cutoff_epoch )); then
      reason="stale"
      stale_candidate_count=$((stale_candidate_count + 1))
    else
      capacity_candidate_count=$((capacity_candidate_count + 1))
    fi
    deletion_candidate_count=$((deletion_candidate_count + 1))

    if [[ "${dry_run}" == "true" ]]; then
      would_delete_count=$((would_delete_count + 1))
      would_reclaim_bytes=$((would_reclaim_bytes + size_bytes))
      remaining_bytes=$((remaining_bytes - size_bytes))
      echo "[DRY-RUN] would delete id=${cache_id} reason=${reason} size=$(format_mb "${size_bytes}")MB ref=${cache_ref} created_at=${created_at} last_accessed_at=${last_accessed_at} key=${cache_key}"
      continue
    fi

    if gh api -X DELETE "repos/${repo}/actions/caches/${cache_id}" >/dev/null; then
      deleted_count=$((deleted_count + 1))
      reclaimed_bytes=$((reclaimed_bytes + size_bytes))
      remaining_bytes=$((remaining_bytes - size_bytes))
      echo "[actions-cache-cleanup] Deleted id=${cache_id} reason=${reason} size=$(format_mb "${size_bytes}")MB ref=${cache_ref} key=${cache_key}"
    else
      failed_count=$((failed_count + 1))
      echo "[actions-cache-cleanup][WARN] Failed to delete id=${cache_id} ref=${cache_ref} key=${cache_key}" >&2
    fi
  done < "${candidate_tsv_file}"
fi

if (( total_bytes > threshold_bytes && remaining_bytes > threshold_bytes )); then
  echo "[actions-cache-cleanup][WARN] Cache total remains above threshold. remaining=$(format_mb "${remaining_bytes}")MB threshold=${max_total_mb}MB"
fi

{
  echo "# GitHub Actions Cache Cleanup Summary"
  echo ""
  echo "- repo: \`${repo}\`"
  echo "- dry_run: \`${dry_run}\`"
  echo "- max_total_mb: \`${max_total_mb}\`"
  echo "- keep_recent_days: \`${keep_recent_days}\`"
  echo "- scanned_caches: \`${scanned_count}\`"
  echo "- deletion_candidates: \`${deletion_candidate_count}\`"
  echo "- stale_candidates: \`${stale_candidate_count}\`"
  echo "- capacity_candidates: \`${capacity_candidate_count}\`"
  echo "- total_before_mb: \`$(format_mb "${total_bytes}")\`"
  echo "- threshold_mb: \`${max_total_mb}\`"
  if [[ "${dry_run}" == "true" ]]; then
    echo "- would_delete_caches: \`${would_delete_count}\`"
    echo "- would_reclaim_mb: \`$(format_mb "${would_reclaim_bytes}")\`"
  else
    echo "- deleted_caches: \`${deleted_count}\`"
    echo "- failed_deletions: \`${failed_count}\`"
    echo "- reclaimed_mb: \`$(format_mb "${reclaimed_bytes}")\`"
  fi
  echo "- estimated_total_after_mb: \`$(format_mb "${remaining_bytes}")\`"
} > "${summary_file}"

cat "${summary_file}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "${summary_file}" >> "${GITHUB_STEP_SUMMARY}"
fi

if (( failed_count > 0 )); then
  exit 1
fi
