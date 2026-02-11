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
  awk -v file="${file}" '
function reset_state() {
  in_fun = 0
  has_exception_catch = 0
  has_cancellation_guard = 0
  brace_depth = 0
  fun_line = 0
  fun_signature = ""
}
function maybe_report() {
  if (in_fun && has_exception_catch && !has_cancellation_guard) {
    printf "%s:%d: suspend fun + catch(Exception) without CancellationException guard (%s)\n", file, fun_line, fun_signature
  }
}
BEGIN {
  reset_state()
}
{
  if (!in_fun && $0 ~ /suspend[[:space:]]+fun[[:space:]]+/) {
    in_fun = 1
    has_exception_catch = 0
    has_cancellation_guard = 0
    brace_depth = 0
    fun_line = NR
    fun_signature = $0
  }

  if (in_fun) {
    line = $0
    open_count = gsub(/\{/, "{", line)
    close_count = gsub(/\}/, "}", line)
    brace_depth += open_count - close_count

    if ($0 ~ /catch[[:space:]]*\(([e_]):[[:space:]]*Exception\)/) {
      has_exception_catch = 1
    }
    if ($0 ~ /CancellationException/) {
      has_cancellation_guard = 1
    }

    if (brace_depth <= 0 && $0 ~ /\}/) {
      maybe_report()
      reset_state()
    }
  }
}
END {
  maybe_report()
}
' "${file}" >> "${TMP_RESULTS}"

  has_exception_catch=false
  if grep -Eq "catch \\((e|_): Exception\\)" "${file}"; then
    has_exception_catch=true
  fi

  if grep -Eq "viewModelScope\\.launch\\b" "${file}" \
    && ${has_exception_catch} \
    && ! grep -Eq "CancellationException" "${file}"; then
    printf '%s\n' "${file}: viewModelScope.launch + catch(Exception) without CancellationException guard" >> "${TMP_RESULTS}"
  fi

  if grep -Eq "\\bscope\\.launch\\b" "${file}" \
    && ${has_exception_catch} \
    && ! grep -Eq "CancellationException" "${file}"; then
    printf '%s\n' "${file}: scope.launch + catch(Exception) without CancellationException guard" >> "${TMP_RESULTS}"
  fi
done < <(find "${SOURCE_DIR}" -type f -name "*.kt" | sort)

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Found potential coroutine cancellation guard issues:" >&2
  sort -u "${TMP_RESULTS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Cancellation guard check passed for ${SOURCE_DIR}."
