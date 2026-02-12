#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-app/src/main/kotlin/com/ytone/longcare}"
EXIT_CODE=0

echo "[module-api] checking API visibility boundaries under: ${ROOT_DIR}"

echo "[module-api] rule-1: internal packages must not be imported directly"
if rg -n '^\s*import\s+.*\.internal\.' "${ROOT_DIR}" --glob '*.kt'; then
  echo "[module-api][FAIL] detected import of internal package symbols"
  EXIT_CODE=1
fi

echo "[module-api] rule-2: data implementation classes should only be wired in data/di layers"
if rg -n '^\s*import\s+com\.ytone\.longcare\.data\..*Impl' "${ROOT_DIR}" --glob '*.kt' \
  | rg -v '/(data|di)/'; then
  echo "[module-api][FAIL] non data/di layer imports implementation classes"
  EXIT_CODE=1
fi

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[module-api] visibility verification failed."
  exit "${EXIT_CODE}"
fi

echo "[module-api] visibility verification passed."
