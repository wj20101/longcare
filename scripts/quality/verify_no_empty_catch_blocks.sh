#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${1:-.}"

if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Source directory not found: ${SOURCE_DIR}" >&2
  exit 1
fi

TMP_RESULTS="$(mktemp)"
trap 'rm -f "${TMP_RESULTS}"' EXIT

if command -v rg >/dev/null 2>&1; then
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
  scanner_status=$?
  set -e

  if [[ ${scanner_status} -gt 1 ]]; then
    echo "Failed to scan Kotlin catch blocks under ${SOURCE_DIR}." >&2
    exit "${scanner_status}"
  fi
elif command -v perl >/dev/null 2>&1; then
  echo "[empty-catch][WARN] ripgrep not found; falling back to perl"
  find "${SOURCE_DIR}" \
    \( -type d \( -name build -o -name .gradle -o -name .git \) -prune \) -o \
    \( -type f -name '*.kt' -exec perl -0777 -ne '
      while (/catch\s*\([^)]*\)\s*\{\s*(?:\/\/[^\n]*\s*)?\}/g) {
        $line = 1 + (substr($_, 0, $-[0]) =~ tr/\n//);
        print "$ARGV:$line:empty catch block\n";
      }
    ' {} + \) > "${TMP_RESULTS}"
else
  echo "Empty catch block scanning requires ripgrep or perl." >&2
  exit 1
fi

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Found empty catch blocks:" >&2
  sed 's/^/  - /' "${TMP_RESULTS}" >&2
  exit 1
fi

echo "No empty catch blocks found in ${SOURCE_DIR}."
