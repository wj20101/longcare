#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
CHANGED_ONLY="false"
BASE_REF_VALUE=""
CHANGED_ONLY_FALLBACK_ALL="false"
CHANGED_ONLY_FALLBACK_REASON=""
CHANGED_ADDED_FILES=""

LEGACY_FEATURE_DIR_REL="app/src/main/kotlin/com/ytone/longcare/features"
LEGACY_FEATURE_ALLOWLIST_REL="scripts/quality/legacy_feature_files_allowlist.txt"
LEGACY_IMPORT_ALLOWLIST_REL="scripts/quality/architecture_legacy_imports_allowlist.txt"

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/check_new_files_guard.sh [options]

Options:
  --project-root <path>  Project root path (default: .)
  --changed-only         Check only newly added files in current branch/worktree
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "[new-files-guard][FAIL] --project-root requires a non-empty path value" >&2
        echo "recommended_fix=use---project-root-<path>" >&2
        exit 1
      fi
      PROJECT_ROOT="${2}"
      shift 2
      ;;
    --changed-only)
      CHANGED_ONLY="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[new-files-guard][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
LEGACY_FEATURE_DIR="${PROJECT_ROOT}/${LEGACY_FEATURE_DIR_REL}"
LEGACY_FEATURE_ALLOWLIST="${PROJECT_ROOT}/${LEGACY_FEATURE_ALLOWLIST_REL}"
LEGACY_IMPORT_ALLOWLIST="${PROJECT_ROOT}/${LEGACY_IMPORT_ALLOWLIST_REL}"

ensure_rule_file() {
  local rule_file="$1"
  local rule_file_rel="$2"
  if [[ ! -f "${rule_file}" ]]; then
    echo "[new-files-guard][FAIL] governance rule file missing"
    echo "rule_file=${rule_file_rel}"
    echo "recommended_fix=restore-governance-rule-file"
    exit 1
  fi
}

resolve_base_ref() {
  BASE_REF_VALUE=""
  if [[ -n "${BASE_REF:-}" ]]; then
    if git -C "${PROJECT_ROOT}" rev-parse --verify "${BASE_REF}" >/dev/null 2>&1; then
      BASE_REF_VALUE="${BASE_REF}"
      return 0
    fi
    CHANGED_ONLY_FALLBACK_REASON="invalid-BASE_REF:${BASE_REF}"
    return 1
  fi

  if [[ -n "${GITHUB_BASE_REF:-}" ]] && git -C "${PROJECT_ROOT}" rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/${GITHUB_BASE_REF}"
    return 0
  fi

  if git -C "${PROJECT_ROOT}" rev-parse --verify origin/master >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/master"
    return 0
  fi

  if git -C "${PROJECT_ROOT}" rev-parse --verify origin/main >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/main"
    return 0
  fi

  CHANGED_ONLY_FALLBACK_REASON="no-strong-base-ref"
  return 1
}

collect_all_kotlin_files() {
  if [[ ! -d "${LEGACY_FEATURE_DIR}" ]]; then
    return 0
  fi

  find "${LEGACY_FEATURE_DIR}" -type f -name '*.kt' |
    awk -v root="${PROJECT_ROOT%/}/" '
      {
        line = $0
        gsub("^" root, "", line)
        sub(/^.\//, "", line)
        print line
      }
    ' |
    sort -u
}

collect_changed_added_kotlin_files() {
  CHANGED_ONLY_FALLBACK_ALL="false"
  CHANGED_ONLY_FALLBACK_REASON=""
  CHANGED_ADDED_FILES=""

  if ! git -C "${PROJECT_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="not-a-git-work-tree"
    return 0
  fi

  if ! resolve_base_ref; then
    CHANGED_ONLY_FALLBACK_ALL="true"
    return 0
  fi

  local base_added=""
  local base_status=0
  if base_added="$(git -C "${PROJECT_ROOT}" diff --name-only --diff-filter=A "${BASE_REF_VALUE}...HEAD" 2>/dev/null)"; then
    base_status=0
  else
    base_status=$?
  fi
  if [[ "${base_status}" -gt 1 ]]; then
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-diff-failed:${BASE_REF_VALUE}...HEAD"
    return 0
  fi

  local staged_added=""
  if staged_added="$(git -C "${PROJECT_ROOT}" diff --cached --name-only --diff-filter=A 2>/dev/null)"; then
    :
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-diff-cached-failed"
    return 0
  fi

  local untracked_added=""
  if untracked_added="$(git -C "${PROJECT_ROOT}" ls-files --others --exclude-standard 2>/dev/null)"; then
    :
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-ls-files-failed"
    return 0
  fi

  CHANGED_ADDED_FILES="$(
    {
      printf "%s\n" "${base_added}"
      printf "%s\n" "${staged_added}"
      printf "%s\n" "${untracked_added}"
    } |
      awk 'NF' |
      sort -u |
      grep -E '\.kt$' || true
  )"
}

ensure_rule_file "${LEGACY_FEATURE_ALLOWLIST}" "${LEGACY_FEATURE_ALLOWLIST_REL}"
ensure_rule_file "${LEGACY_IMPORT_ALLOWLIST}" "${LEGACY_IMPORT_ALLOWLIST_REL}"

if [[ "${CHANGED_ONLY}" == "true" ]]; then
  MODE_LABEL="changed-only"
  collect_changed_added_kotlin_files
  if [[ "${CHANGED_ONLY_FALLBACK_ALL}" == "true" ]]; then
    MODE_LABEL="changed-only-fallback-all-files"
    CANDIDATE_FILES="$(collect_all_kotlin_files)"
    echo "[new-files-guard][WARN] changed-only base could not be resolved accurately (${CHANGED_ONLY_FALLBACK_REASON}); falling back to full frozen-directory scan."
  else
    CANDIDATE_FILES="${CHANGED_ADDED_FILES}"
  fi
else
  MODE_LABEL="all-files"
  CANDIDATE_FILES="$(collect_all_kotlin_files)"
fi

VIOLATIONS=()
while IFS= read -r relative_file; do
  [[ -z "${relative_file}" ]] && continue
  if [[ "${relative_file}" != ${LEGACY_FEATURE_DIR_REL}/* ]]; then
    continue
  fi
  if ! grep -Fqx -- "${relative_file}" "${LEGACY_FEATURE_ALLOWLIST}"; then
    VIOLATIONS+=("${relative_file}")
  fi
done <<< "${CANDIDATE_FILES}"

if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
  echo "[new-files-guard][FAIL] Frozen legacy directory"
  echo "rule_file=${LEGACY_FEATURE_ALLOWLIST_REL}"
  echo "rule_file=${LEGACY_IMPORT_ALLOWLIST_REL}"
  for offending_file in "${VIOLATIONS[@]}"; do
    echo "offending_file=${offending_file}"
    echo "recommended_fix=move-to-feature-module-or-inline-into-allowlisted-file"
  done
  echo "recommended_fix=avoid-adding-new-kotlin-files-in-frozen-legacy-directory"
  exit 1
fi

echo "[new-files-guard] mode=${MODE_LABEL} no frozen-legacy Kotlin file violations found."
