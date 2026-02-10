#!/usr/bin/env bash
set -euo pipefail

REPORT_PATH="${1:-app/build/reports/lint-results-debug.txt}"

if [[ ! -f "${REPORT_PATH}" ]]; then
  echo "Lint report not found: ${REPORT_PATH}" >&2
  exit 1
fi

WARNING_IDS="$(grep ': Warning: ' "${REPORT_PATH}" | sed -nE 's/.*\[([A-Za-z0-9_]+)\]$/\1/p' | sort -u)"

if [[ -z "${WARNING_IDS}" ]]; then
  echo "No lint warnings found in ${REPORT_PATH}."
  exit 0
fi

ALLOWED_IDS=$'Aligned16KB\nGlobalOptionInConsumerRules\nTrustAllX509TrustManager'
UNKNOWN_IDS=""

while IFS= read -r issue_id; do
  [[ -z "${issue_id}" ]] && continue
  if ! printf '%s\n' "${ALLOWED_IDS}" | grep -qx "${issue_id}"; then
    UNKNOWN_IDS+="${issue_id}"$'\n'
  fi
done <<< "${WARNING_IDS}"

if [[ -n "${UNKNOWN_IDS}" ]]; then
  echo "Found lint warning IDs outside allowlist:" >&2
  printf '%s' "${UNKNOWN_IDS}" | sed 's/^/  - /' >&2
  echo "Observed warning IDs in report:" >&2
  printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Lint warning allowlist check passed."
echo "Observed warning IDs:"
printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /'
