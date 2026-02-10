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
  has_exception_catch=false
  if rg -q "catch \\((e|_): Exception\\)" "${file}"; then
    has_exception_catch=true
  fi

  if rg -q "suspend fun" "${file}" \
    && ${has_exception_catch} \
    && ! rg -q "CancellationException" "${file}"; then
    printf '%s\n' "${file}: suspend fun + catch(Exception) without CancellationException guard" >> "${TMP_RESULTS}"
  fi

  if rg -q "viewModelScope\\.launch\\b" "${file}" \
    && ${has_exception_catch} \
    && ! rg -q "CancellationException" "${file}"; then
    printf '%s\n' "${file}: viewModelScope.launch + catch(Exception) without CancellationException guard" >> "${TMP_RESULTS}"
  fi

  if rg -q "\\bscope\\.launch\\b" "${file}" \
    && ${has_exception_catch} \
    && ! rg -q "CancellationException" "${file}"; then
    printf '%s\n' "${file}: scope.launch + catch(Exception) without CancellationException guard" >> "${TMP_RESULTS}"
  fi
done < <(find "${SOURCE_DIR}" -type f -name "*.kt" | sort)

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Found potential coroutine cancellation guard issues:" >&2
  sort -u "${TMP_RESULTS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Cancellation guard check passed for ${SOURCE_DIR}."
