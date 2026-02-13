#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
EXIT_CODE=0

APP_ROOT="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare"
CORE_ROOT="${PROJECT_ROOT}/core"
CORE_DOMAIN_ROOT="${PROJECT_ROOT}/core/domain/src/main/kotlin"
FEATURE_ROOT="${PROJECT_ROOT}/feature"

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

echo "[architecture] rule-5: core modules must not import feature packages"
run_rule \
  "core modules import feature packages" \
  '^\s*import\s+com\.ytone\.longcare\.feature\.' \
  "${CORE_ROOT}"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[architecture] boundary verification failed."
  exit "${EXIT_CODE}"
fi

echo "[architecture] boundary verification passed."
