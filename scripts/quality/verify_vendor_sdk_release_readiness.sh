#!/usr/bin/env bash
set -euo pipefail

LINT_REPORT="${1:-app/build/reports/lint-results-debug.txt}"

if [[ ! -f "${LINT_REPORT}" || ! -r "${LINT_REPORT}" || ! -s "${LINT_REPORT}" ]]; then
  echo "[vendor-sdk-release][FAIL] lint report missing, unreadable or empty: ${LINT_REPORT}" >&2
  exit 1
fi

TMP_VIOLATIONS="$(mktemp)"
trap 'rm -f "${TMP_VIOLATIONS}"' EXIT

grep '\[Aligned16KB\]' "${LINT_REPORT}" \
  | grep -E 'WbCloudFaceLiveSdk|libYTCommonLiveness|libkyctoolkit|libturingmfa' \
  >> "${TMP_VIOLATIONS}" || true

grep '\[GlobalOptionInConsumerRules\]' "${LINT_REPORT}" \
  | grep -E 'WbCloudFaceLiveSdk' \
  >> "${TMP_VIOLATIONS}" || true

grep '\[TrustAllX509TrustManager\]' "${LINT_REPORT}" \
  | grep -E 'qlzsdk' \
  >> "${TMP_VIOLATIONS}" || true

unapproved=0
while IFS= read -r finding; do
  # Only the explicitly accepted current binaries/findings are advisory.
  case "${finding}" in
    *qlzsdk-1.3.0.5-protobufLiteRelease-ui*\[TrustAllX509TrustManager\]*|\
    *WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc*\[Aligned16KB\]*|\
    *WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc*\[GlobalOptionInConsumerRules\]*)
      echo "[vendor-sdk-release][WARN] accepted risk, not fixed: ${finding}" >&2
      ;;
    *)
      echo "[vendor-sdk-release][FAIL] unapproved vendor finding: ${finding}" >&2
      unapproved=1
      ;;
  esac
done < <(sort -u "${TMP_VIOLATIONS}")

if [[ "${unapproved}" -ne 0 ]]; then exit 1; fi

echo "[vendor-sdk-release][PASS] no unapproved targeted vendor findings; other lint/signing checks remain required"
