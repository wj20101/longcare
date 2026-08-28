#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
EXIT_CODE=0
RG_LAST_OUTPUT=""
RG_LAST_STATUS=1
ARCH_SCANNER=""

APP_ROOT="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare"
APP_DEBUG_ROOT="${PROJECT_ROOT}/app/src/debug/kotlin/com/ytone/longcare"
APP_TEST_ROOT="${PROJECT_ROOT}/app/src/test/kotlin/com/ytone/longcare"
CORE_ROOT="${PROJECT_ROOT}/core"
CORE_MODEL_API_ROOT="${PROJECT_ROOT}/core/model/src/main/kotlin/com/ytone/longcare/api"
CORE_DOMAIN_ROOT="${PROJECT_ROOT}/core/domain/src/main/kotlin"
FEATURE_ROOT="${PROJECT_ROOT}/feature"
LEGACY_APP_FEATURE_ROOT="${APP_ROOT}/features"
LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST="${PROJECT_ROOT}/scripts/quality/architecture_legacy_imports_allowlist.txt"
LEGACY_APP_INTERNAL_IMPORT_BUDGET_FILE="${PROJECT_ROOT}/scripts/quality/architecture_legacy_import_budget.txt"
LEGACY_APP_FEATURE_FILE_ALLOWLIST="${PROJECT_ROOT}/scripts/quality/legacy_feature_files_allowlist.txt"

echo "[architecture] checking layer boundaries under: ${PROJECT_ROOT}"

resolve_arch_scanner() {
  if [[ -n "${ARCH_SCANNER}" ]]; then
    return 0
  fi

  if command -v rg >/dev/null 2>&1; then
    ARCH_SCANNER="rg"
    return 0
  fi

  if command -v grep >/dev/null 2>&1; then
    ARCH_SCANNER="grep"
    echo "[architecture][WARN] ripgrep not found; falling back to grep"
    return 0
  fi

  ARCH_SCANNER=""
  return 1
}

normalize_pattern_for_grep() {
  local original_pattern="$1"
  local normalized_pattern="${original_pattern}"

  normalized_pattern="${normalized_pattern//\\s/[[:space:]]}"

  if [[ "${normalized_pattern}" == \\b* ]]; then
    normalized_pattern="(^|[^[:alnum:]_])${normalized_pattern:2}"
  fi
  if [[ "${normalized_pattern}" == *\\b ]]; then
    normalized_pattern="${normalized_pattern%\\b}([^[:alnum:]_]|$)"
  fi

  if [[ "${normalized_pattern}" == *\\b* ]]; then
    echo "[architecture][FAIL] grep fallback cannot safely normalize regex: ${original_pattern}"
    echo "[architecture][HINT] install 'rg' to preserve boundary regex semantics"
    return 1
  fi

  printf "%s" "${normalized_pattern}"
}

collect_scan_kotlin_files() {
  local scan_dirs=("$@")
  local scan_dir
  local git_file
  local abs_file

  if git -C "${PROJECT_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    while IFS= read -r git_file; do
      [[ -z "${git_file}" ]] && continue
      abs_file="${PROJECT_ROOT%/}/${git_file}"
      for scan_dir in "${scan_dirs[@]}"; do
        case "${abs_file}" in
          "${scan_dir}")
            printf "%s\n" "${abs_file}"
            break
            ;;
          "${scan_dir%/}"/*)
            printf "%s\n" "${abs_file}"
            break
            ;;
        esac
      done
    done < <(git -C "${PROJECT_ROOT}" ls-files --cached --others --exclude-standard -- '*.kt')
    return 0
  fi

  for scan_dir in "${scan_dirs[@]}"; do
    if [[ -f "${scan_dir}" && "${scan_dir}" == *.kt ]]; then
      printf "%s\n" "${scan_dir}"
    elif [[ -d "${scan_dir}" ]]; then
      find "${scan_dir}" -type f -name '*.kt' -print
    fi
  done
}

run_rg_scan() {
  local rule_name="$1"
  local pattern="$2"
  shift 2

  local scanner_stderr
  scanner_stderr="$(mktemp)"
  local rg_output=""
  local rg_status=0

  if ! resolve_arch_scanner; then
    echo "[architecture][FAIL] ${rule_name} (scan failed: no scanner available)"
    echo "[architecture][HINT] install 'rg' (preferred) or ensure 'grep' is available in PATH"
    EXIT_CODE=1
    RG_LAST_OUTPUT=""
    RG_LAST_STATUS=127
    rm -f "${scanner_stderr}"
    return 0
  fi

  if [[ "${ARCH_SCANNER}" == "rg" ]]; then
    if rg_output="$(rg -n "${pattern}" "$@" --glob '*.kt' 2>"${scanner_stderr}")"; then
      rg_status=0
    else
      rg_status=$?
    fi
  else
    local grep_pattern
    if ! grep_pattern="$(normalize_pattern_for_grep "${pattern}")"; then
      RG_LAST_OUTPUT=""
      RG_LAST_STATUS=2
      EXIT_CODE=1
      rm -f "${scanner_stderr}"
      return 0
    fi

    local scan_files=()
    while IFS= read -r scan_file; do
      [[ -z "${scan_file}" ]] && continue
      scan_files+=("${scan_file}")
    done < <(collect_scan_kotlin_files "$@" | sort -u)

    if [[ "${#scan_files[@]}" -eq 0 ]]; then
      rg_output=""
      rg_status=1
    elif rg_output="$(grep -n -E -- "${grep_pattern}" "${scan_files[@]}" 2>"${scanner_stderr}")"; then
      rg_status=0
    else
      rg_status=$?
    fi
  fi

  RG_LAST_OUTPUT="${rg_output}"
  RG_LAST_STATUS="${rg_status}"

  if [[ "${rg_status}" -gt 1 ]]; then
    echo "[architecture][FAIL] ${rule_name} (${ARCH_SCANNER} scan failed: exit ${rg_status})"
    if [[ "${ARCH_SCANNER}" == "rg" ]]; then
      echo "[architecture][HINT] ensure 'rg' is installed and scan paths are readable"
    else
      echo "[architecture][HINT] ensure scan paths are readable and grep regex is valid"
    fi
    if [[ -s "${scanner_stderr}" ]]; then
      sed 's/^/[architecture][DETAIL] /' "${scanner_stderr}"
    fi
    EXIT_CODE=1
  fi

  rm -f "${scanner_stderr}"
}

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

  run_rg_scan "${rule_name}" "${pattern}" "${scan_dirs[@]}"
  if [[ "${RG_LAST_STATUS}" -gt 1 ]]; then
    return 0
  fi

  if [[ "${RG_LAST_STATUS}" -eq 0 ]]; then
    printf "%s\n" "${RG_LAST_OUTPUT}"
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

  run_rg_scan "${rule_name}" "${pattern}" "${scan_dirs[@]}"
  if [[ "${RG_LAST_STATUS}" -gt 1 ]]; then
    return 0
  fi

  if [[ "${RG_LAST_STATUS}" -eq 1 ]]; then
    return 0
  fi
  local raw_matches="${RG_LAST_OUTPUT}"

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
    local allowlist_rel
    allowlist_rel="${allowlist_file#${PROJECT_ROOT%/}/}"
    if [[ "${allowlist_rel}" == "${allowlist_file}" ]]; then
      allowlist_rel="${allowlist_file}"
    fi
    echo "[architecture][HINT] this rule is frozen and allowlist-governed"
    echo "[architecture][HINT] see ${allowlist_rel}"
    echo "[architecture][HINT] preferred fix: move code to feature/* or inline into an allowlisted file"
    EXIT_CODE=1
  fi
}

run_filtered_rule() {
  local rule_name="$1"
  local pattern="$2"
  local exclude_regex="$3"
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

  run_rg_scan "${rule_name}" "${pattern}" "${scan_dirs[@]}"
  if [[ "${RG_LAST_STATUS}" -gt 1 ]]; then
    return 0
  fi

  if [[ "${RG_LAST_STATUS}" -eq 1 ]]; then
    return 0
  fi
  local raw_matches="${RG_LAST_OUTPUT}"

  local filtered_matches
  local filter_status=0
  if filtered_matches="$(printf "%s\n" "${raw_matches}" | grep -Ev "${exclude_regex}")"; then
    filter_status=0
  else
    filter_status=$?
  fi

  if [[ "${filter_status}" -gt 1 ]]; then
    echo "[architecture][FAIL] ${rule_name} (filter regex failed: exit ${filter_status})"
    echo "[architecture][HINT] verify exclude regex syntax: ${exclude_regex}"
    EXIT_CODE=1
    return 0
  fi

  if [[ -n "${filtered_matches}" ]]; then
    printf "%s\n" "${filtered_matches}"
    echo "[architecture][FAIL] ${rule_name}"
    EXIT_CODE=1
  fi
}

run_viewmodel_rule() {
  local rule_name="$1"
  local pattern="$2"
  shift 2

  local viewmodel_files=()
  local scan_dir
  while IFS= read -r file_path; do
    [[ -n "${file_path}" ]] && viewmodel_files+=("${file_path}")
  done < <(
    for scan_dir in "$@"; do
      [[ -d "${scan_dir}" ]] && find "${scan_dir}" -type f -name '*ViewModel.kt' -print
    done | sort -u
  )

  if [[ "${#viewmodel_files[@]}" -eq 0 ]]; then
    echo "[architecture] ${rule_name} skipped (no ViewModel files)"
    return 0
  fi

  run_rg_scan "${rule_name}" "${pattern}" "${viewmodel_files[@]}"
  if [[ "${RG_LAST_STATUS}" -eq 0 ]]; then
    printf "%s\n" "${RG_LAST_OUTPUT}"
    echo "[architecture][FAIL] ${rule_name}"
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
    local allowlist_rel
    allowlist_rel="${allowlist_file#${PROJECT_ROOT%/}/}"
    if [[ "${allowlist_rel}" == "${allowlist_file}" ]]; then
      allowlist_rel="${allowlist_file}"
    fi
    local import_allowlist_rel
    import_allowlist_rel="${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST#${PROJECT_ROOT%/}/}"
    if [[ "${import_allowlist_rel}" == "${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST}" ]]; then
      import_allowlist_rel="${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST}"
    fi
    echo "[architecture][HINT] app/features is frozen for new Kotlin files"
    echo "[architecture][HINT] see ${allowlist_rel}"
    echo "[architecture][HINT] see ${import_allowlist_rel}"
    echo "[architecture][HINT] preferred fix: move code to feature/* or inline into an allowlisted file"
    EXIT_CODE=1
  fi
}

count_allowlist_entries() {
  local allowlist_file="$1"
  awk 'NF && $1 !~ /^#/' "${allowlist_file}" | wc -l | tr -d ' '
}

check_allowlist_budget() {
  local rule_label="$1"
  local allowlist_file="$2"
  local budget_file="$3"

  if [[ ! -f "${allowlist_file}" ]]; then
    echo "[architecture][FAIL] ${rule_label} allowlist missing: ${allowlist_file}"
    EXIT_CODE=1
    return 0
  fi

  if [[ ! -f "${budget_file}" ]]; then
    echo "[architecture][FAIL] ${rule_label} budget file missing: ${budget_file}"
    EXIT_CODE=1
    return 0
  fi

  local budget
  budget="$(awk 'NF && $1 !~ /^#/ { print $1; exit }' "${budget_file}")"
  if [[ -z "${budget}" || ! "${budget}" =~ ^[0-9]+$ ]]; then
    echo "[architecture][FAIL] ${rule_label} invalid budget in ${budget_file}: '${budget}'"
    EXIT_CODE=1
    return 0
  fi

  local current_count
  current_count="$(count_allowlist_entries "${allowlist_file}")"
  if [[ "${current_count}" -gt "${budget}" ]]; then
    echo "[architecture][FAIL] ${rule_label} current=${current_count}, budget=${budget}"
    echo "[architecture] reduce ${allowlist_file} entries before merging."
    EXIT_CODE=1
    return 0
  fi

  echo "[architecture] ${rule_label} current=${current_count}, budget=${budget}"
}

echo "[architecture] rule-1: domain must not depend on android.*"
run_rule \
  "domain layer imports android.*" \
  '^\s*import\s+android\.' \
  "${APP_ROOT}/domain" \
  "${CORE_DOMAIN_ROOT}"

echo "[architecture] rule-1b: core/domain must not import api/data packages directly"
run_rule \
  "core/domain imports com.ytone.longcare.api|data directly" \
  '^\s*import\s+com\.ytone\.longcare\.(api|data)\.' \
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

echo "[architecture] rule-4d: app non-data/api/network layers must not import api request/response directly"
run_filtered_rule \
  "app non-data/api/network imports api request/response" \
  '^\s*import\s+com\.ytone\.longcare\.api\.(request|response)\.' \
  '/(api|data|network)/' \
  "${APP_ROOT}"

echo "[architecture] rule-4e: app/api/request must remain empty after request model migration"
if [[ -d "${APP_ROOT}/api/request" ]]; then
  if find "${APP_ROOT}/api/request" -type f -name '*.kt' -print | grep -q .; then
    find "${APP_ROOT}/api/request" -type f -name '*.kt' -print
    echo "[architecture][FAIL] app/api/request contains Kotlin files"
    EXIT_CODE=1
  fi
fi

echo "[architecture] rule-4f: app non-data/api/network must not reference api request/response FQCN"
run_filtered_rule \
  "app non-data/api/network references api request/response FQCN" \
  'com\.ytone\.longcare\.api\.(request|response)\.' \
  '/(api|data|network)/' \
  "${APP_ROOT}"

echo "[architecture] rule-4g: app/api/response must remain empty after response model migration"
if [[ -d "${APP_ROOT}/api/response" ]]; then
  if find "${APP_ROOT}/api/response" -type f -name '*.kt' -print | grep -q .; then
    find "${APP_ROOT}/api/response" -type f -name '*.kt' -print
    echo "[architecture][FAIL] app/api/response contains Kotlin files"
    EXIT_CODE=1
  fi
fi

echo "[architecture] rule-4h: app data/network layers must not reference api request/response directly"
run_rule \
  "app data/network layers reference api request/response directly" \
  'com\.ytone\.longcare\.api\.(request|response)\.' \
  "${APP_ROOT}/data" \
  "${APP_ROOT}/common/network" \
  "${APP_ROOT}/network"

echo "[architecture] rule-4i: app module must not reference api request/response directly"
run_rule \
  "app module references api request/response directly" \
  'com\.ytone\.longcare\.api\.(request|response)\.' \
  "${APP_ROOT}"

echo "[architecture] rule-4j: app/api must consume request/response via model aliases only"
run_rule \
  "app/api imports api request/response directly" \
  '^\s*import\s+com\.ytone\.longcare\.api\.(request|response)\.' \
  "${APP_ROOT}/api"

echo "[architecture] rule-4k: app test/debug sources must consume request/response via model aliases"
run_rule \
  "app test/debug sources reference api request/response directly" \
  'com\.ytone\.longcare\.api\.(request|response)\.' \
  "${APP_TEST_ROOT}" \
  "${APP_DEBUG_ROOT}"

echo "[architecture] rule-4l: repository must not reference api request/response FQCN"
run_rule \
  "repository references api request/response FQCN" \
  'com\.ytone\.longcare\.api\.(request|response)\.' \
  "${APP_ROOT}" \
  "${APP_TEST_ROOT}" \
  "${APP_DEBUG_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4m: core/model/api/request|response must remain empty"
if [[ -d "${CORE_MODEL_API_ROOT}" ]]; then
  if find "${CORE_MODEL_API_ROOT}" -type f -name '*.kt' -print | grep -q .; then
    find "${CORE_MODEL_API_ROOT}" -type f -name '*.kt' -print
    echo "[architecture][FAIL] core/model/api contains Kotlin files"
    EXIT_CODE=1
  fi
fi

echo "[architecture] rule-4n: source files must not declare package api.request|api.response"
run_rule \
  "source files declare package com.ytone.longcare.api.request|response" \
  '^\s*package\s+com\.ytone\.longcare\.api\.(request|response)\b' \
  "${APP_ROOT}" \
  "${APP_TEST_ROOT}" \
  "${APP_DEBUG_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4o: repository must not reference legacy models.protos package"
run_rule \
  "repository references legacy com.ytone.longcare.models.protos package" \
  'com\.ytone\.longcare\.models\.protos\.' \
  "${APP_ROOT}" \
  "${APP_TEST_ROOT}" \
  "${APP_DEBUG_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4p: source files must not declare package com.ytone.longcare.models.protos"
run_rule \
  "source files declare legacy package com.ytone.longcare.models.protos" \
  '^\s*package\s+com\.ytone\.longcare\.models\.protos\b' \
  "${APP_ROOT}" \
  "${APP_TEST_ROOT}" \
  "${APP_DEBUG_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4r: QLZ vendor APIs must stay inside the app-owned adapter"
run_filtered_rule \
  "source outside integration/qlz imports QLZ vendor APIs" \
  '^\s*import\s+(com\.evenmed|com\.comm|com\.falth|com\.qiaolz)\.' \
  '/integration/qlz/' \
  "${APP_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-4q: app/model package must remain empty after model extraction"
if [[ -d "${APP_ROOT}/model" ]]; then
  if find "${APP_ROOT}/model" -type f -name '*.kt' -print | grep -q .; then
    find "${APP_ROOT}/model" -type f -name '*.kt' -print
    echo "[architecture][FAIL] app/model contains Kotlin files"
    EXIT_CODE=1
  fi
fi

echo "[architecture] rule-4b: legacy app/features imports app internals are frozen by allowlist"
run_allowlisted_rule \
  "legacy app/features import app internals (data/di/db/api)" \
  '^\s*import\s+com\.ytone\.longcare\.(data|di|db|api)\.' \
  "${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST}" \
  "${LEGACY_APP_FEATURE_ROOT}"

echo "[architecture] rule-4c: legacy import allowlist must not exceed budget"
check_allowlist_budget \
  "legacy app/features import app internals allowlist budget" \
  "${LEGACY_APP_INTERNAL_IMPORT_ALLOWLIST}" \
  "${LEGACY_APP_INTERNAL_IMPORT_BUDGET_FILE}"

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

echo "[architecture] rule-7b: ViewModels must not depend on Activity, Context, or ApplicationContext"
run_viewmodel_rule \
  "ViewModels depend on Android lifecycle contexts" \
  '^\s*import\s+android\.(app\.Activity|content\.Context)|@ApplicationContext' \
  "${APP_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-7c: ViewModels must not invoke Toast or UI SDK implementations"
run_viewmodel_rule \
  "ViewModels depend on ToastHelper or FaceVerifier" \
  '^\s*import\s+com\.ytone\.longcare\.(common\.utils\.ToastHelper|common\.faceauth\.FaceVerifier)' \
  "${APP_ROOT}" \
  "${CORE_ROOT}" \
  "${FEATURE_ROOT}"

echo "[architecture] rule-7d: ViewModels must receive coroutine dispatchers through boundaries"
run_viewmodel_rule \
  "ViewModels hardcode Dispatchers" \
  'Dispatchers\.' \
  "${APP_ROOT}" \
  "${CORE_ROOT}" \
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
IDENTIFICATION_VM_ROOT="${APP_ROOT}/features/identification/vm"
if [[ ! -d "${IDENTIFICATION_VM_ROOT}" && -d "${FEATURE_ROOT}/identification/src/main/kotlin/com/ytone/longcare/features/identification/vm" ]]; then
  IDENTIFICATION_VM_ROOT="${FEATURE_ROOT}/identification/src/main/kotlin/com/ytone/longcare/features/identification/vm"
fi

check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationViewModel.kt" \
  290 \
  "IdentificationViewModel.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceVerifyCallbacks.kt" \
  120 \
  "IdentificationFaceVerifyCallbacks.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationElderPhotoFlow.kt" \
  120 \
  "IdentificationElderPhotoFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceSetupFlow.kt" \
  140 \
  "IdentificationFaceSetupFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceVerificationRequestFactory.kt" \
  120 \
  "IdentificationFaceVerificationRequestFactory.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceSetupPreparation.kt" \
  120 \
  "IdentificationFaceSetupPreparation.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationServicePersonDecisionFlow.kt" \
  80 \
  "IdentificationServicePersonDecisionFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceCaptureResultFlow.kt" \
  120 \
  "IdentificationFaceCaptureResultFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationFaceVerificationExecutionFlow.kt" \
  80 \
  "IdentificationFaceVerificationExecutionFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationServicePersonEntryFlow.kt" \
  80 \
  "IdentificationServicePersonEntryFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationElderEntryFlow.kt" \
  80 \
  "IdentificationElderEntryFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationStandardFaceVerificationFlow.kt" \
  80 \
  "IdentificationStandardFaceVerificationFlow.kt"
check_file_line_threshold \
  "${IDENTIFICATION_VM_ROOT}/IdentificationWatermarkFlow.kt" \
  80 \
  "IdentificationWatermarkFlow.kt"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[architecture] boundary verification failed."
  exit "${EXIT_CODE}"
fi

echo "[architecture] boundary verification passed."
