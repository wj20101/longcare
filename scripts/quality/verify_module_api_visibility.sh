#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-app/src/main/kotlin/com/ytone/longcare}"
PROJECT_ROOT="${2:-.}"
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

echo "[module-api] rule-3: repository contracts must be owned by core/domain only"
if rg -n '^\s*(interface|sealed interface)\s+\w*Repository\b' \
  "${PROJECT_ROOT}/app" "${PROJECT_ROOT}/core" "${PROJECT_ROOT}/feature" --glob '*.kt' \
  | rg -v "^${PROJECT_ROOT}/core/domain/src/main/kotlin/"; then
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

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[module-api] visibility verification failed."
  exit "${EXIT_CODE}"
fi

echo "[module-api] visibility verification passed."
