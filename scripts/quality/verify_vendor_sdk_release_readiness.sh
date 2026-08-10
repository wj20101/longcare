#!/usr/bin/env bash
set -euo pipefail

LINT_REPORT="${1:-app/build/reports/lint-results-debug.txt}"

if [[ ! -f "${LINT_REPORT}" ]]; then
  echo "[vendor-sdk-release][FAIL] lint report not found: ${LINT_REPORT}" >&2
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

if [[ -s "${TMP_VIOLATIONS}" ]]; then
  echo "[vendor-sdk-release][FAIL] production-blocking vendor SDK findings remain:" >&2
  sort -u "${TMP_VIOLATIONS}" | sed 's/^/  - /' >&2
  echo "Replace the QLZ and/or Tencent face SDK binaries before production release." >&2
  exit 1
fi

echo "[vendor-sdk-release][PASS] no production-blocking vendor SDK findings detected"
