#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
LEGACY_FEATURE_DIR_REL="app/src/main/kotlin/com/ytone/longcare/features"
LEGACY_FEATURE_ALLOWLIST_REL="scripts/quality/legacy_feature_files_allowlist.txt"

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/verify_legacy_feature_file_allowlist.sh [options]

Options:
  --project-root <path>  Project root path (default: .)
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "[legacy-file-allowlist][FAIL] --project-root requires a non-empty path value" >&2
        echo "recommended_fix=use---project-root-<path>" >&2
        exit 1
      fi
      PROJECT_ROOT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[legacy-file-allowlist][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${PROJECT_ROOT}" ]]; then
  echo "[legacy-file-allowlist][FAIL] project root missing: ${PROJECT_ROOT}"
  echo "recommended_fix=provide-existing-project-root"
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
LEGACY_FEATURE_DIR="${PROJECT_ROOT}/${LEGACY_FEATURE_DIR_REL}"
LEGACY_FEATURE_ALLOWLIST="${PROJECT_ROOT}/${LEGACY_FEATURE_ALLOWLIST_REL}"

if [[ ! -d "${LEGACY_FEATURE_DIR}" ]]; then
  echo "[legacy-file-allowlist][FAIL] legacy feature directory missing"
  echo "scan_dir=${LEGACY_FEATURE_DIR_REL}"
  echo "recommended_fix=restore-legacy-feature-directory-or-update-governance-path"
  exit 1
fi

if [[ ! -f "${LEGACY_FEATURE_ALLOWLIST}" ]]; then
  echo "[legacy-file-allowlist][FAIL] allowlist missing"
  echo "rule_file=${LEGACY_FEATURE_ALLOWLIST_REL}"
  echo "recommended_fix=restore-governance-rule-file"
  exit 1
fi

collect_actual_files() {
  find "${LEGACY_FEATURE_DIR}" -type f -name '*.kt' |
    while IFS= read -r file_path; do
      printf '%s\n' "${file_path#${PROJECT_ROOT%/}/}"
    done |
    sort -u
}

collect_allowlist_files() {
  awk '
      {
        sub(/\r$/, "")
        line = $0
        sub(/^[[:space:]]+/, "", line)
        sub(/[[:space:]]+$/, "", line)
        if (line == "" || line ~ /^#/) {
          next
        }
        sub(/^\.\//, "", line)
        print line
      }
    ' "${LEGACY_FEATURE_ALLOWLIST}" |
    sort -u
}

ACTUAL_FILES="$(collect_actual_files)"
ALLOWLIST_FILES="$(collect_allowlist_files)"

UNEXPECTED_FILES="$({
  comm -23 \
    <(printf '%s\n' "${ACTUAL_FILES}" | awk 'NF') \
    <(printf '%s\n' "${ALLOWLIST_FILES}" | awk 'NF')
} || true)"

STALE_ENTRIES="$({
  comm -13 \
    <(printf '%s\n' "${ACTUAL_FILES}" | awk 'NF') \
    <(printf '%s\n' "${ALLOWLIST_FILES}" | awk 'NF')
} || true)"

EXIT_CODE=0

if [[ -n "${UNEXPECTED_FILES}" ]]; then
  echo "[legacy-file-allowlist][FAIL] found files outside allowlist"
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    echo "offending_file=${file_path}"
  done <<< "${UNEXPECTED_FILES}"
  echo "recommended_fix=move-to-feature-module-or-inline-into-allowlisted-file"
  EXIT_CODE=1
fi

if [[ -n "${STALE_ENTRIES}" ]]; then
  echo "[legacy-file-allowlist][FAIL] allowlist contains missing files"
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    echo "stale_entry=${file_path}"
  done <<< "${STALE_ENTRIES}"
  echo "recommended_fix=remove-stale-allowlist-entry-or-restore-intended-file"
  EXIT_CODE=1
fi

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  exit "${EXIT_CODE}"
fi

ACTUAL_COUNT="$(printf '%s\n' "${ACTUAL_FILES}" | awk 'NF' | wc -l | tr -d ' ')"
ALLOWLIST_COUNT="$(printf '%s\n' "${ALLOWLIST_FILES}" | awk 'NF' | wc -l | tr -d ' ')"
echo "[legacy-file-allowlist] actual=${ACTUAL_COUNT} allowlist=${ALLOWLIST_COUNT} sets match."
