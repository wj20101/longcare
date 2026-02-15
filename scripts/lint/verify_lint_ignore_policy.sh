#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <lint.xml>"
  exit 1
fi

LINT_CONFIG_FILE="$1"

if [[ ! -f "${LINT_CONFIG_FILE}" ]]; then
  echo "[lint-ignore-policy][FAIL] lint config not found: ${LINT_CONFIG_FILE}"
  exit 1
fi

ALLOWED_IGNORED_ISSUES=(
  "Aligned16KB"
  "GlobalOptionInConsumerRules"
  "TrustAllX509TrustManager"
)

is_allowed_issue() {
  local candidate="$1"
  local allowed_issue=""
  for allowed_issue in "${ALLOWED_IGNORED_ISSUES[@]}"; do
    if [[ "${allowed_issue}" == "${candidate}" ]]; then
      return 0
    fi
  done
  return 1
}

IGNORED_ISSUES=()
while IFS= read -r issue_id; do
  if [[ -n "${issue_id}" ]]; then
    IGNORED_ISSUES+=("${issue_id}")
  fi
done < <(
  awk '
    /<issue[[:space:]][^>]*severity="ignore"/ {
      if (match($0, /id="[^"]+"/)) {
        id = substr($0, RSTART + 4, RLENGTH - 5)
        print id
      }
    }
  ' "${LINT_CONFIG_FILE}" | sort -u
)

if [[ "${#IGNORED_ISSUES[@]}" -eq 0 ]]; then
  echo "[lint-ignore-policy][PASS] no issue uses severity=\"ignore\" in ${LINT_CONFIG_FILE}"
  exit 0
fi

UNEXPECTED_ISSUES=()
for issue_id in "${IGNORED_ISSUES[@]}"; do
  if ! is_allowed_issue "${issue_id}"; then
    UNEXPECTED_ISSUES+=("${issue_id}")
  fi
done

if [[ "${#UNEXPECTED_ISSUES[@]}" -gt 0 ]]; then
  echo "[lint-ignore-policy][FAIL] unexpected ignored lint issues detected in ${LINT_CONFIG_FILE}:"
  printf '  - %s\n' "${UNEXPECTED_ISSUES[@]}"
  echo "[lint-ignore-policy] allowed severity=\"ignore\" issues are:"
  printf '  - %s\n' "${ALLOWED_IGNORED_ISSUES[@]}"
  exit 1
fi

echo "[lint-ignore-policy][PASS] lint ignore policy verified (${LINT_CONFIG_FILE})"
