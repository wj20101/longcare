#!/usr/bin/env bash
set -euo pipefail

REPORT_PATH="${1:-app/build/reports/lint-results-debug.txt}"

if [[ ! -f "${REPORT_PATH}" ]]; then
  echo "Lint report not found: ${REPORT_PATH}" >&2
  exit 1
fi

TMP_WARNINGS="$(mktemp)"
trap 'rm -f "${TMP_WARNINGS}"' EXIT

grep ': Warning: ' "${REPORT_PATH}" > "${TMP_WARNINGS}" || true

WARNING_IDS="$(sed -nE 's/.*\[([A-Za-z0-9_]+)\]$/\1/p' "${TMP_WARNINGS}" | sort -u)"

if [[ -z "${WARNING_IDS}" ]]; then
  echo "No lint warnings found in ${REPORT_PATH}."
  exit 0
fi

ALLOWED_IDS=$'Aligned16KB\nGlobalOptionInConsumerRules\nTrustAllX509TrustManager'
UNKNOWN_IDS=""
SOURCE_VIOLATIONS=""

is_allowed_source() {
  local issue_id="$1"
  local warning_line="$2"

  case "${issue_id}" in
    Aligned16KB)
      if [[ "${warning_line}" == *"crashreport-"* ]] ||
        [[ "${warning_line}" == *"WbCloudFaceLiveSdk-face-v"* ]] ||
        [[ "${warning_line}" == *"wbcloudface-live-"* ]] ||
        [[ "${warning_line}" == *"com.tencent.cloud.huiyansdkface:wbcloudface-live:"* ]] ||
        [[ "${warning_line}" == *"/gradle/libs.versions.toml:"* ]]; then
        return 0
      fi
      return 1
      ;;
    GlobalOptionInConsumerRules)
      [[ "${warning_line}" == *"WbCloudFaceLiveSdk-face-v"* ]] ||
        [[ "${warning_line}" == *"wbcloudface-live-"* ]] ||
        [[ "${warning_line}" == *"com.tencent.cloud.huiyansdkface:wbcloudface-live:"* ]]
      return $?
      ;;
    TrustAllX509TrustManager)
      [[ "${warning_line}" == *"qcloud-foundation-"* ]]
      return $?
      ;;
    *)
      return 1
      ;;
  esac
}

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

while IFS= read -r warning_line; do
  [[ -z "${warning_line}" ]] && continue
  issue_id="$(printf '%s\n' "${warning_line}" | sed -nE 's/.*\[([A-Za-z0-9_]+)\]$/\1/p')"
  [[ -z "${issue_id}" ]] && continue

  if ! is_allowed_source "${issue_id}" "${warning_line}"; then
    SOURCE_VIOLATIONS+="${issue_id}: ${warning_line}"$'\n'
  fi
done < "${TMP_WARNINGS}"

if [[ -n "${SOURCE_VIOLATIONS}" ]]; then
  echo "Found allowlisted lint IDs from unexpected sources:" >&2
  printf '%s' "${SOURCE_VIOLATIONS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Lint warning allowlist check passed."
echo "Observed warning IDs:"
printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /'
