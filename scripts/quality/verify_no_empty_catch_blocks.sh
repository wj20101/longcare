#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${1:-.}"

if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Source directory not found: ${SOURCE_DIR}" >&2
  exit 1
fi

if ! command -v rg >/dev/null 2>&1; then
  echo "rg is required for multiline Kotlin catch-block scanning." >&2
  exit 1
fi

TMP_RESULTS="$(mktemp)"
trap 'rm -f "${TMP_RESULTS}"' EXIT

set +e
rg \
  --line-number \
  --pcre2 \
  --multiline \
  'catch\s*\([^)]*\)\s*\{\s*(?://[^\n]*\s*)?\}' \
  --glob '*.kt' \
  --glob '!**/build/**' \
  --glob '!**/.gradle/**' \
  --glob '!**/.git/**' \
  "${SOURCE_DIR}" > "${TMP_RESULTS}"
rg_status=$?
set -e

if [[ ${rg_status} -gt 1 ]]; then
  echo "Failed to scan Kotlin catch blocks under ${SOURCE_DIR}." >&2
  exit "${rg_status}"
fi

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Found empty catch blocks:" >&2
  sed 's/^/  - /' "${TMP_RESULTS}" >&2
  exit 1
fi

echo "No empty catch blocks found in ${SOURCE_DIR}."
