#!/usr/bin/env bash
set -euo pipefail

LINT_REPORT="${1:-app/build/reports/lint-results-debug.txt}"

if [[ ! -f "${LINT_REPORT}" ]]; then
  echo "[vendor-sdk-release][FAIL] lint report not found: ${LINT_REPORT}" >&2
  exit 1
fi

TMP_VIOLATIONS="$(mktemp)"
TMP_QLZ_FINDINGS="$(mktemp)"
trap 'rm -f "${TMP_VIOLATIONS}" "${TMP_QLZ_FINDINGS}"' EXIT

grep '\[Aligned16KB\]' "${LINT_REPORT}" \
  | grep -E 'WbCloudFaceLiveSdk|libYTCommonLiveness|libkyctoolkit|libturingmfa' \
  >> "${TMP_VIOLATIONS}" || true

grep '\[GlobalOptionInConsumerRules\]' "${LINT_REPORT}" \
  | grep -E 'WbCloudFaceLiveSdk' \
  >> "${TMP_VIOLATIONS}" || true

grep '\[TrustAllX509TrustManager\]' "${LINT_REPORT}" \
  | grep -E 'qlzsdk' \
  >> "${TMP_QLZ_FINDINGS}" || true

if [[ -s "${TMP_QLZ_FINDINGS}" ]]; then
  registered_qlz_count="$({ grep -E '(jetified-)?qlzsdk-1\.3\.0\.2-protobufLiteRelease-ui(\.aar)?' "${TMP_QLZ_FINDINGS}" || true; } | wc -l | tr -d ' ')"
  echo "[vendor-sdk-release][INFO] approved QLZ 1.3.0.2 vendor-internal TLS risk is accepted and non-blocking (${registered_qlz_count} registered finding(s))"

  grep -Ev '(jetified-)?qlzsdk-1\.3\.0\.2-protobufLiteRelease-ui(\.aar)?' "${TMP_QLZ_FINDINGS}" \
    >> "${TMP_VIOLATIONS}" || true
fi

if [[ -s "${TMP_VIOLATIONS}" ]]; then
  echo "[vendor-sdk-release][FAIL] production-blocking vendor SDK findings remain:" >&2
  sort -u "${TMP_VIOLATIONS}" | sed 's/^/  - /' >&2
  echo "Resolve production-blocking vendor findings without treating the approved QLZ finding as project-remediated." >&2
  exit 1
fi

echo "[vendor-sdk-release][PASS] no production-blocking vendor SDK findings detected"
