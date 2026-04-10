#!/usr/bin/env bash
set -euo pipefail

REPORT_PATH="${1:-app/build/reports/lint-results-debug.txt}"
WAIVER_PATH="${2:-scripts/lint/lint_warning_waivers.json}"
CURRENT_DATE="${LINT_WAIVER_DATE:-$(date -u +%F)}"
ENFORCE_UNUSED_WAIVERS_MODE="${LINT_ENFORCE_UNUSED_WAIVERS:-auto}"
REGISTRY_PATH="${LINT_GATE_REGISTRY_PATH:-scripts/quality/quality_gate_registry.json}"
REGISTRY_GATE_ID="lint_warning_allowlist"

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

resolve_enforce_unused_waivers() {
  local mode="$1"
  case "${mode}" in
    true|TRUE|1|yes|YES|on|ON)
      echo "true"
      ;;
    false|FALSE|0|no|NO|off|OFF)
      echo "false"
      ;;
    auto|AUTO)
      # In CI, stale waivers should be advisory to avoid flaky red pipelines
      # when dependency noise changes between PR validation and post-merge runs.
      # Use LINT_ENFORCE_UNUSED_WAIVERS=true when strict enforcement is needed.
      if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
        echo "false"
      else
        echo "true"
      fi
      ;;
    *)
      echo "Invalid LINT_ENFORCE_UNUSED_WAIVERS value: ${mode}" >&2
      exit 1
      ;;
  esac
}

registry_gate_field() {
  local field="$1"

  if [[ ! -f "${REGISTRY_PATH}" ]]; then
    return 1
  fi

  jq -r --arg id "${REGISTRY_GATE_ID}" --arg field "${field}" '
    .gates[]
    | select(.id == $id)
    | .[$field] // empty
  ' "${REGISTRY_PATH}" | head -n 1
}

lint_gate_metadata() {
  local registry_registered
  local layer
  local owner
  local source_of_truth
  local likely_fix

  registry_registered="false"
  layer="$(registry_gate_field "layer" || true)"
  owner="$(registry_gate_field "owner" || true)"
  source_of_truth="$(registry_gate_field "source_of_truth" || true)"
  likely_fix="$(registry_gate_field "likely_fix" || true)"

  if [[ -n "${layer}" || -n "${owner}" || -n "${source_of_truth}" || -n "${likely_fix}" ]]; then
    registry_registered="true"
  fi

  if [[ "${registry_registered}" != "true" ]]; then
    layer="ci-required"
  fi
  if [[ -z "${owner}" ]]; then
    owner="mobile-platform"
  fi
  if [[ -z "${source_of_truth}" ]]; then
    source_of_truth="scripts/lint/lint_warning_waivers.json"
  fi
  if [[ -z "${likely_fix}" ]]; then
    likely_fix="Fix the lint issue or add/update an approved waiver entry."
  fi

  printf '%s\t%s\t%s\t%s\t%s\n' "${registry_registered}" "${layer}" "${owner}" "${source_of_truth}" "${likely_fix}"
}

print_lint_gate_diagnostics() {
  local gate_metadata
  local gate_registry_registered
  local gate_layer
  local gate_owner
  local gate_source_of_truth
  local gate_likely_fix

  gate_metadata="$(lint_gate_metadata)"
  gate_registry_registered="$(printf '%s' "${gate_metadata}" | cut -f1)"
  gate_layer="$(printf '%s' "${gate_metadata}" | cut -f2)"
  gate_owner="$(printf '%s' "${gate_metadata}" | cut -f3)"
  gate_source_of_truth="$(printf '%s' "${gate_metadata}" | cut -f4)"
  gate_likely_fix="$(printf '%s' "${gate_metadata}" | cut -f5)"

  echo "Lint gate diagnostics:" >&2
  if [[ "${gate_registry_registered}" != "true" ]]; then
    echo "  - registry_entry: missing" >&2
  fi
  echo "  - owner: ${gate_owner}" >&2
  echo "  - layer: ${gate_layer}" >&2
  echo "  - source_of_truth: ${gate_source_of_truth}" >&2
  echo "  - likely_fix: ${gate_likely_fix}" >&2
}

warning_line_matches_source_pattern() {
  local warning_line="$1"
  local source_pattern="$2"

  if [[ "${source_pattern}" == regex:* ]]; then
    local regex_pattern="${source_pattern#regex:}"
    [[ "${warning_line}" =~ ${regex_pattern} ]]
    return
  fi

  [[ "${warning_line}" == *"${source_pattern}"* ]]
}

ENFORCE_UNUSED_WAIVERS="$(resolve_enforce_unused_waivers "${ENFORCE_UNUSED_WAIVERS_MODE}")"

TMP_WARNINGS="$(mktemp)"
trap 'rm -f "${TMP_WARNINGS}"' EXIT

grep ': Warning: ' "${REPORT_PATH}" > "${TMP_WARNINGS}" || true

WARNING_IDS="$(sed -nE 's/.*\[([A-Za-z0-9_]+)([[:space:]]+from[[:space:]]+[^]]+)?\]$/\1/p' "${TMP_WARNINGS}" | sort -u)"

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
  print_lint_gate_diagnostics
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
  print_lint_gate_diagnostics
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
  print_lint_gate_diagnostics
  exit 1
fi

if [[ -z "${WARNING_IDS}" ]]; then
  if [[ -n "${WAIVER_IDS}" ]]; then
    if [[ "${ENFORCE_UNUSED_WAIVERS}" == "true" ]]; then
      echo "Lint report has no warnings, but waiver entries still exist. Remove stale waivers:" >&2
      printf '%s\n' "${WAIVER_IDS}" | sed 's/^/  - /' >&2
      print_lint_gate_diagnostics
      exit 1
    fi

    echo "Lint report has no warnings; existing waiver entries are treated as non-blocking in current mode:" >&2
    printf '%s\n' "${WAIVER_IDS}" | sed 's/^/  - /' >&2
  fi
  echo "No lint warnings found in ${REPORT_PATH}."
  exit 0
fi

while IFS= read -r warning_line; do
  [[ -z "${warning_line}" ]] && continue
  issue_id="$(printf '%s\n' "${warning_line}" | sed -nE 's/.*\[([A-Za-z0-9_]+)([[:space:]]+from[[:space:]]+[^]]+)?\]$/\1/p')"
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
    if warning_line_matches_source_pattern "${warning_line}" "${source_pattern}"; then
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
  print_lint_gate_diagnostics
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
  if [[ "${ENFORCE_UNUSED_WAIVERS}" == "true" ]]; then
    echo "Found stale waivers not present in current lint report. Remove them:" >&2
    printf '%s' "${UNUSED_WAIVERS}" | sed 's/^/  - /' >&2
    print_lint_gate_diagnostics
    exit 1
  fi

  echo "Found stale waivers not present in current lint report (non-blocking in current mode):" >&2
  printf '%s' "${UNUSED_WAIVERS}" | sed 's/^/  - /' >&2
fi

echo "Lint warning waiver check passed."
echo "Observed warning IDs:"
printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /'
