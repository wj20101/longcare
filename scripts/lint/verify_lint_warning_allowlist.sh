#!/usr/bin/env bash
set -euo pipefail

REPORT_PATH="${1:-app/build/reports/lint-results-debug.txt}"
WAIVER_PATH="${2:-scripts/lint/lint_warning_waivers.json}"
CURRENT_DATE="${LINT_WAIVER_DATE:-$(date -u +%F)}"

if [[ ! -f "${REPORT_PATH}" ]]; then
  echo "Lint report not found: ${REPORT_PATH}" >&2
  exit 1
fi

if [[ ! -f "${WAIVER_PATH}" ]]; then
  echo "Lint waiver file not found: ${WAIVER_PATH}" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required but not found in PATH." >&2
  exit 1
fi

if ! [[ "${CURRENT_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Invalid CURRENT_DATE value: ${CURRENT_DATE}" >&2
  exit 1
fi

TMP_WARNINGS="$(mktemp)"
trap 'rm -f "${TMP_WARNINGS}"' EXIT

grep ': Warning: ' "${REPORT_PATH}" > "${TMP_WARNINGS}" || true

WARNING_IDS="$(sed -nE 's/.*\[([A-Za-z0-9_]+)\]$/\1/p' "${TMP_WARNINGS}" | sort -u)"

if ! jq -e '.waivers | type == "array"' "${WAIVER_PATH}" >/dev/null; then
  echo "Invalid waiver format: missing waivers array (${WAIVER_PATH})" >&2
  exit 1
fi

WAIVER_IDS="$(jq -r '.waivers[].id' "${WAIVER_PATH}" | sort -u)"
INVALID_WAIVER_ENTRIES="$(jq -r '
  .waivers[]
  | select(
      (.id | type) != "string"
      or (.id | length == 0)
      or (.owner | type) != "string"
      or (.owner | length == 0)
      or (.reason | type) != "string"
      or (.reason | length == 0)
      or (.review_by | type) != "string"
      or (.review_by | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$") | not)
      or (.allowed_sources | type) != "array"
      or (.allowed_sources | length == 0)
    )
  | (.id // "<missing-id>")
' "${WAIVER_PATH}")"

if [[ -n "${INVALID_WAIVER_ENTRIES}" ]]; then
  echo "Found invalid waiver entries (missing required fields or invalid date/source config):" >&2
  printf '%s\n' "${INVALID_WAIVER_ENTRIES}" | sed 's/^/  - /' >&2
  exit 1
fi

UNKNOWN_IDS=""
SOURCE_VIOLATIONS=""

while IFS= read -r issue_id; do
  [[ -z "${issue_id}" ]] && continue
  if ! printf '%s\n' "${WAIVER_IDS}" | grep -qx "${issue_id}"; then
    UNKNOWN_IDS+="${issue_id}"$'\n'
  fi
done <<< "${WARNING_IDS}"

if [[ -n "${UNKNOWN_IDS}" ]]; then
  echo "Found lint warning IDs outside waiver allowlist:" >&2
  printf '%s' "${UNKNOWN_IDS}" | sed 's/^/  - /' >&2
  echo "Observed warning IDs in report:" >&2
  printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /' >&2
  exit 1
fi

EXPIRED_WAIVERS="$(jq -r --arg current_date "${CURRENT_DATE}" '
  .waivers[]
  | select((.review_by // "") < $current_date)
  | "\(.id) (review_by=\(.review_by))"
' "${WAIVER_PATH}")"

if [[ -n "${EXPIRED_WAIVERS}" ]]; then
  echo "Found expired lint waivers (review_by < ${CURRENT_DATE}):" >&2
  printf '%s\n' "${EXPIRED_WAIVERS}" | sed 's/^/  - /' >&2
  exit 1
fi

if [[ -z "${WARNING_IDS}" ]]; then
  if [[ -n "${WAIVER_IDS}" ]]; then
    echo "Lint report has no warnings, but waiver entries still exist. Remove stale waivers:" >&2
    printf '%s\n' "${WAIVER_IDS}" | sed 's/^/  - /' >&2
    exit 1
  fi
  echo "No lint warnings found in ${REPORT_PATH}."
  exit 0
fi

while IFS= read -r warning_line; do
  [[ -z "${warning_line}" ]] && continue
  issue_id="$(printf '%s\n' "${warning_line}" | sed -nE 's/.*\[([A-Za-z0-9_]+)\]$/\1/p')"
  [[ -z "${issue_id}" ]] && continue

  allowed_sources="$(jq -r --arg id "${issue_id}" '
    .waivers[]
    | select(.id == $id)
    | (.allowed_sources // [])[]
  ' "${WAIVER_PATH}")"

  if [[ -z "${allowed_sources}" ]]; then
    SOURCE_VIOLATIONS+="${issue_id}: no allowed_sources configured"$'\n'
    continue
  fi

  matched="false"
  while IFS= read -r source_pattern; do
    [[ -z "${source_pattern}" ]] && continue
    if [[ "${warning_line}" == *"${source_pattern}"* ]]; then
      matched="true"
      break
    fi
  done <<< "${allowed_sources}"

  if [[ "${matched}" != "true" ]]; then
    SOURCE_VIOLATIONS+="${issue_id}: ${warning_line}"$'\n'
  fi
done < "${TMP_WARNINGS}"

if [[ -n "${SOURCE_VIOLATIONS}" ]]; then
  echo "Found allowlisted lint IDs from unexpected sources:" >&2
  printf '%s' "${SOURCE_VIOLATIONS}" | sed 's/^/  - /' >&2
  exit 1
fi

UNUSED_WAIVERS=""
while IFS= read -r waiver_id; do
  [[ -z "${waiver_id}" ]] && continue
  if ! printf '%s\n' "${WARNING_IDS}" | grep -qx "${waiver_id}"; then
    UNUSED_WAIVERS+="${waiver_id}"$'\n'
  fi
done <<< "${WAIVER_IDS}"

if [[ -n "${UNUSED_WAIVERS}" ]]; then
  echo "Found stale waivers not present in current lint report. Remove them:" >&2
  printf '%s' "${UNUSED_WAIVERS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Lint warning waiver check passed."
echo "Observed warning IDs:"
printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /'
