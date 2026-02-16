#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
EXIT_CODE=0

APP_ROOT="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare"
CORE_ROOT="${PROJECT_ROOT}/core"
CORE_DOMAIN_ROOT="${PROJECT_ROOT}/core/domain/src/main/kotlin"
FEATURE_ROOT="${PROJECT_ROOT}/feature"
LEGACY_APP_FEATURE_ROOT="${APP_ROOT}/features"
LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST="${PROJECT_ROOT}/scripts/quality/architecture_legacy_imports_allowlist.txt"
LEGACY_APP_FEATURE_FILE_ALLOWLIST="${PROJECT_ROOT}/scripts/quality/legacy_feature_files_allowlist.txt"

echo "[architecture] checking layer boundaries under: ${PROJECT_ROOT}"

run_rule() {
  local rule_name="$1"
  local pattern="$2"
  shift 2

  local scan_dirs=()
  local scan_dir
  for scan_dir in "$@"; do
    if [[ -d "${scan_dir}" ]]; then
      scan_dirs+=("${scan_dir}")
    fi
  done

  if [[ "${#scan_dirs[@]}" -eq 0 ]]; then
    echo "[architecture] ${rule_name} skipped (no matching directories)"
    return 0
  fi

  if rg -n "${pattern}" "${scan_dirs[@]}" --glob '*.kt'; then
    echo "[architecture][FAIL] ${rule_name}"
    EXIT_CODE=1
  fi
}

run_allowlisted_rule() {
  local rule_name="$1"
  local pattern="$2"
  local allowlist_file="$3"
  shift 3

  local scan_dirs=()
  local scan_dir
  for scan_dir in "$@"; do
    if [[ -d "${scan_dir}" ]]; then
      scan_dirs+=("${scan_dir}")
    fi
  done

  if [[ "${#scan_dirs[@]}" -eq 0 ]]; then
    echo "[architecture] ${rule_name} skipped (no matching directories)"
    return 0
  fi

  local raw_matches
  raw_matches="$(rg -n "${pattern}" "${scan_dirs[@]}" --glob '*.kt' || true)"
  if [[ -z "${raw_matches}" ]]; then
    return 0
  fi

  local normalized_matches
  normalized_matches="$(
    printf "%s\n" "${raw_matches}" |
      awk -v root="${PROJECT_ROOT%/}/" '
        {
          line = $0
          gsub("^" root, "", line)
          sub(/^.\//, "", line)
          sub(/:[0-9]+:/, ":", line)
          print line
        }
      ' |
      sort -u
  )"

  if [[ ! -f "${allowlist_file}" ]]; then
    echo "[architecture][FAIL] ${rule_name} (allowlist missing: ${allowlist_file})"
    printf "%s\n" "${normalized_matches}"
    EXIT_CODE=1
    return 0
  fi

  local unexpected_matches=()
  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    if ! grep -Fqx -- "${line}" "${allowlist_file}"; then
      unexpected_matches+=("${line}")
    fi
  done <<<"${normalized_matches}"

  if [[ "${#unexpected_matches[@]}" -gt 0 ]]; then
    echo "[architecture][FAIL] ${rule_name} (new violations detected)"
    printf "%s\n" "${unexpected_matches[@]}"
    EXIT_CODE=1
  fi
}

check_file_line_threshold() {
  local file_path="$1"
  local max_lines="$2"
  local rule_label="$3"
  if [[ ! -f "${file_path}" ]]; then
    echo "[architecture][FAIL] missing file for ${rule_label}: ${file_path}"
    EXIT_CODE=1
    return 0
  fi

  local line_count
  line_count="$(wc -l < "${file_path}" | tr -d ' ')"
  if [[ "${line_count}" -gt "${max_lines}" ]]; then
    echo "[architecture][FAIL] ${rule_label} has ${line_count} lines (max ${max_lines})"
    EXIT_CODE=1
  fi
}

check_kotlin_file_allowlist() {
  local rule_label="$1"
  local scan_dir="$2"
  local allowlist_file="$3"

  if [[ ! -d "${scan_dir}" ]]; then
    echo "[architecture] ${rule_label} skipped (directory missing: ${scan_dir})"
    return 0
  fi

  if [[ ! -f "${allowlist_file}" ]]; then
    echo "[architecture][FAIL] ${rule_label} allowlist missing: ${allowlist_file}"
    EXIT_CODE=1
    return 0
  fi

  local current_files
  current_files="$(find "${scan_dir}" -type f -name '*.kt' | awk -v root="${PROJECT_ROOT%/}/" '
      {
        line = $0
        gsub("^" root, "", line)
        sub(/^.\//, "", line)
        print line
      }
    ' | sort -u)"

  local unexpected_files=()
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    if ! grep -Fqx -- "${file_path}" "${allowlist_file}"; then
      unexpected_files+=("${file_path}")
    fi
  done <<<"${current_files}"

  if [[ "${#unexpected_files[@]}" -gt 0 ]]; then
    echo "[architecture][FAIL] ${rule_label} found new files outside allowlist"
    printf "%s\n" "${unexpected_files[@]}"
    EXIT_CODE=1
  fi
}

echo "[architecture] rule-1: domain must not depend on android.*"
run_rule \
  "domain layer imports android.*" \
  '^\s*import\s+android\.' \
  "${APP_ROOT}/domain" \
  "${CORE_DOMAIN_ROOT}"

echo "[architecture] rule-2: feature/shared/ui layers must not import data implementation classes"
run_rule \
  "presentation layer imports data implementation classes" \
  '^\s*import\s+com\.ytone\.longcare\.data\..*Impl' \
  "${APP_ROOT}/features" \
  "${APP_ROOT}/shared" \
  "${APP_ROOT}/ui" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-3: feature/shared layers must not reference *RepositoryImpl symbols"
run_rule \
  "presentation layer references repository implementation symbols" \
  '\b[A-Za-z0-9_]+RepositoryImpl\b' \
  "${APP_ROOT}/features" \
  "${APP_ROOT}/shared" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4: feature modules must not import app data/di/db/api internals"
run_rule \
  "feature modules import app internals (data/di/db/api)" \
  '^\s*import\s+com\.ytone\.longcare\.(data|di|db|api)\.' \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4b: legacy app/features imports app internals are frozen by allowlist"
run_allowlisted_rule \
  "legacy app/features import app internals (data/di/db/api)" \
  '^\s*import\s+com\.ytone\.longcare\.(data|di|db|api)\.' \
  "${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST}" \
  "${LEGACY_APP_FEATURE_ROOT}"

echo "[architecture] rule-5: core modules must not import feature packages"
run_rule \
  "core modules import feature packages" \
  '^\s*import\s+com\.ytone\.longcare\.feature\.' \
  "${CORE_ROOT}"

echo "[architecture] rule-6: app/domain must remain empty after G4 migration"
if [[ -d "${APP_ROOT}/domain" ]]; then
  if find "${APP_ROOT}/domain" -type f -name '*.kt' -print | grep -q .; then
    find "${APP_ROOT}/domain" -type f -name '*.kt' -print
    echo "[architecture][FAIL] app/domain contains Kotlin files"
    EXIT_CODE=1
  fi
fi

echo "[architecture] rule-7: feature UI layers must not import NavController directly"
run_rule \
  "feature UI imports NavController directly" \
  '^\s*import\s+androidx\.navigation\.NavController\b' \
  "${APP_ROOT}/features" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-8: AppNavigation.kt line count must stay within threshold"
APP_NAVIGATION_FILE="${APP_ROOT}/navigation/AppNavigation.kt"
APP_NAVIGATION_MAX_LINES=300
check_file_line_threshold "${APP_NAVIGATION_FILE}" "${APP_NAVIGATION_MAX_LINES}" "AppNavigation.kt"

echo "[architecture] rule-9: split nav graph files must stay within threshold"
check_file_line_threshold "${APP_ROOT}/navigation/AppNavGraphsEntry.kt" 200 "AppNavGraphsEntry.kt"
check_file_line_threshold "${APP_ROOT}/navigation/AppNavGraphsServiceFlow.kt" 300 "AppNavGraphsServiceFlow.kt"
check_file_line_threshold "${APP_ROOT}/navigation/AppNavGraphsSupport.kt" 250 "AppNavGraphsSupport.kt"

echo "[architecture] rule-10: legacy app/features kotlin files are frozen by allowlist"
check_kotlin_file_allowlist \
  "legacy app/features kotlin files" \
  "${LEGACY_APP_FEATURE_ROOT}" \
  "${LEGACY_APP_FEATURE_FILE_ALLOWLIST}"

echo "[architecture] rule-11: identification UI split files must stay within threshold"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/ui/IdentificationScreen.kt" \
  320 \
  "IdentificationScreen.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/ui/IdentificationCard.kt" \
  400 \
  "IdentificationCard.kt"

echo "[architecture] rule-12: identification ViewModel must stay within threshold"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationViewModel.kt" \
  580 \
  "IdentificationViewModel.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationFaceVerifyCallbacks.kt" \
  120 \
  "IdentificationFaceVerifyCallbacks.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationElderPhotoFlow.kt" \
  120 \
  "IdentificationElderPhotoFlow.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationFaceSetupFlow.kt" \
  140 \
  "IdentificationFaceSetupFlow.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationFaceVerificationRequestFactory.kt" \
  120 \
  "IdentificationFaceVerificationRequestFactory.kt"
check_file_line_threshold \
  "${APP_ROOT}/features/identification/vm/IdentificationFaceSetupPreparation.kt" \
  120 \
  "IdentificationFaceSetupPreparation.kt"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[architecture] boundary verification failed."
  exit "${EXIT_CODE}"
fi

echo "[architecture] boundary verification passed."
