#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-app/src/main/kotlin/com/ytone/longcare}"
EXIT_CODE=0

echo "[architecture] checking layer boundaries under: ${ROOT_DIR}"

echo "[architecture] rule-1: domain must not depend on android.*"
if rg -n '^\s*import\s+android\.' "${ROOT_DIR}/domain" --glob '*.kt'; then
  echo "[architecture][FAIL] domain layer imports android.*"
  EXIT_CODE=1
fi

echo "[architecture] rule-2: feature/shared/ui layers must not import data implementation classes"
if rg -n '^\s*import\s+com\.ytone\.longcare\.data\..*Impl' \
  "${ROOT_DIR}/features" "${ROOT_DIR}/shared" "${ROOT_DIR}/ui" --glob '*.kt'; then
  echo "[architecture][FAIL] presentation layer imports data implementation"
  EXIT_CODE=1
fi

echo "[architecture] rule-3: feature/shared layers must not reference *RepositoryImpl symbols"
if rg -n '\b[A-Za-z0-9_]+RepositoryImpl\b' \
  "${ROOT_DIR}/features" "${ROOT_DIR}/shared" --glob '*.kt'; then
  echo "[architecture][FAIL] presentation layer references repository implementation symbols"
  EXIT_CODE=1
fi

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[architecture] boundary verification failed."
  exit "${EXIT_CODE}"
fi

echo "[architecture] boundary verification passed."
