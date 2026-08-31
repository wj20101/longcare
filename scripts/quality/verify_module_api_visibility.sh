#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-app/src/main/kotlin/com/ytone/longcare}"
PROJECT_ROOT="${2:-.}"
EXIT_CODE=0
SCANNER=""

if command -v rg >/dev/null 2>&1; then
  SCANNER="rg"
elif command -v grep >/dev/null 2>&1 && command -v find >/dev/null 2>&1; then
  SCANNER="grep"
  echo "[module-api][WARN] ripgrep not found; falling back to grep"
else
  echo "[module-api][FAIL] API visibility scanning requires ripgrep or grep/find." >&2
  exit 1
fi

SCAN_RESULTS="$(mktemp)"
FILTERED_RESULTS="$(mktemp)"
KOTLIN_FILES="$(mktemp)"
trap 'rm -f "${SCAN_RESULTS}" "${FILTERED_RESULTS}" "${KOTLIN_FILES}"' EXIT

scan_kotlin() {
  local rg_pattern="$1"
  local grep_pattern="$2"
  shift 2

  : > "${SCAN_RESULTS}"
  if [[ "${SCANNER}" == "rg" ]]; then
    set +e
    rg \
      --line-number \
      --glob '*.kt' \
      --glob '!**/build/**' \
      --glob '!**/.gradle/**' \
      --glob '!**/.git/**' \
      -- "${rg_pattern}" "$@" > "${SCAN_RESULTS}"
    local scanner_status=$?
    set -e
    if [[ ${scanner_status} -gt 1 ]]; then
      echo "[module-api][FAIL] Kotlin source scan failed (exit ${scanner_status})." >&2
      exit "${scanner_status}"
    fi
    return 0
  fi

  : > "${KOTLIN_FILES}"
  find "$@" \
    \( -type d \( -name build -o -name .gradle -o -name .git \) -prune \) -o \
    \( -type f -name '*.kt' -print0 \) > "${KOTLIN_FILES}"

  local kotlin_file
  local scanner_status
  while IFS= read -r -d '' kotlin_file; do
    set +e
    grep -EnH -- "${grep_pattern}" "${kotlin_file}" >> "${SCAN_RESULTS}"
    scanner_status=$?
    set -e
    if [[ ${scanner_status} -gt 1 ]]; then
      echo "[module-api][FAIL] Kotlin source scan failed for ${kotlin_file} (exit ${scanner_status})." >&2
      exit "${scanner_status}"
    fi
  done < "${KOTLIN_FILES}"
}

echo "[module-api] checking API visibility boundaries under: ${ROOT_DIR}"

echo "[module-api] rule-1: internal packages must not be imported directly"
scan_kotlin \
  '^\s*import\s+.*\.internal\.' \
  '^[[:space:]]*import[[:space:]]+.*\.internal\.' \
  "${ROOT_DIR}"
if [[ -s "${SCAN_RESULTS}" ]]; then
  cat "${SCAN_RESULTS}"
  echo "[module-api][FAIL] detected import of internal package symbols"
  EXIT_CODE=1
fi

echo "[module-api] rule-2: data implementation classes should only be wired in data/di layers"
scan_kotlin \
  '^\s*import\s+com\.ytone\.longcare\.data\..*Impl' \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.data\..*Impl' \
  "${ROOT_DIR}"
grep -Ev '/(data|di)/' "${SCAN_RESULTS}" > "${FILTERED_RESULTS}" || true
if [[ -s "${FILTERED_RESULTS}" ]]; then
  cat "${FILTERED_RESULTS}"
  echo "[module-api][FAIL] non data/di layer imports implementation classes"
  EXIT_CODE=1
fi

echo "[module-api] rule-3: repository contracts must be owned by core/domain only"
scan_kotlin \
  '^\s*(interface|sealed interface)\s+\w*Repository\b' \
  '^[[:space:]]*(interface|sealed[[:space:]]+interface)[[:space:]]+[[:alnum:]_]*Repository([^[:alnum:]_]|$)' \
  "${PROJECT_ROOT}/app" "${PROJECT_ROOT}/core" "${PROJECT_ROOT}/feature"
grep -Fv "${PROJECT_ROOT%/}/core/domain/src/main/kotlin/" "${SCAN_RESULTS}" > "${FILTERED_RESULTS}" || true
if [[ -s "${FILTERED_RESULTS}" ]]; then
  cat "${FILTERED_RESULTS}"
  echo "[module-api][FAIL] repository contract found outside core/domain"
  EXIT_CODE=1
fi

echo "[module-api] rule-4: app layer must not define domain contracts"
APP_DOMAIN_DIR="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/domain"
if [[ -d "${APP_DOMAIN_DIR}" ]]; then
  if find "${APP_DOMAIN_DIR}" -type f -name '*.kt' -print | grep -q .; then
    find "${APP_DOMAIN_DIR}" -type f -name '*.kt' -print
    echo "[module-api][FAIL] app/domain contains contract definitions"
    EXIT_CODE=1
  fi
fi

echo "[module-api] rule-5: Home implementation declarations must remain module-internal"
HOME_MAIN_DIR="${PROJECT_ROOT}/feature/home/src/main/kotlin"
if [[ -d "${HOME_MAIN_DIR}" ]]; then
  scan_kotlin \
    '^(data class|enum class|sealed (class|interface)|class|interface|object|fun|typealias)\s+' \
    '^(data class|enum class|sealed (class|interface)|class|interface|object|fun|typealias)[[:space:]]+' \
    "${HOME_MAIN_DIR}"
  grep -Ev '/features/home/(api|reporting)/' "${SCAN_RESULTS}" > "${FILTERED_RESULTS}" || true
  if [[ -s "${FILTERED_RESULTS}" ]]; then
    cat "${FILTERED_RESULTS}"
    echo "[module-api][FAIL] Home implementation exports declarations outside its reviewed API/reporting packages"
    EXIT_CODE=1
  fi
fi

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[module-api] visibility verification failed."
  exit "${EXIT_CODE}"
fi

echo "[module-api] visibility verification passed."
