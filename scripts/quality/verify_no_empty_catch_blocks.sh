#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${1:-app/src/main/kotlin}"

if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "Source directory not found: ${SOURCE_DIR}" >&2
  exit 1
fi

TMP_RESULTS="$(mktemp)"
trap 'rm -f "${TMP_RESULTS}"' EXIT

while IFS= read -r file; do
  grep -nE 'catch[[:space:]]*\([^)]*\)[[:space:]]*\{[[:space:]]*\}' "${file}" >> "${TMP_RESULTS}" || true
done < <(find "${SOURCE_DIR}" -type f -name "*.kt" | sort)

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Found empty catch blocks:" >&2
  sed 's/^/  - /' "${TMP_RESULTS}" >&2
  exit 1
fi

echo "No empty catch blocks found in ${SOURCE_DIR}."
