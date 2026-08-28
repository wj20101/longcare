#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_vendor_sdk_release_readiness.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

run_case() {
  local name="$1"
  local expected_status="$2"
  local report_content="$3"
  local report_path="${TMP_DIR}/${name}.txt"
  local output_path="${TMP_DIR}/${name}.log"
  local actual_status=0

  printf '%s\n' "${report_content}" > "${report_path}"
  bash "${GUARD}" "${report_path}" > "${output_path}" 2>&1 || actual_status=$?
  if [[ "${actual_status}" -ne "${expected_status}" ]]; then
    echo "[vendor-sdk-release-fixture][FAIL] ${name}: expected ${expected_status}, got ${actual_status}" >&2
    sed 's/^/  /' "${output_path}" >&2
    exit 1
  fi
  echo "[vendor-sdk-release-fixture][PASS] ${name}"
}

approved_qlz='/cache/jetified-qlzsdk-1.3.0.2-protobufLiteRelease-ui/jars/classes.jar: Warning: vendor finding [TrustAllX509TrustManager]'
unknown_qlz='/cache/jetified-qlzsdk-1.3.0.3-protobufLiteRelease-ui/jars/classes.jar: Warning: vendor finding [TrustAllX509TrustManager]'
face_alignment='/cache/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar/lib/arm64-v8a/libkyctoolkit.so: Warning: vendor finding [Aligned16KB]'
face_rules='/cache/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar/proguard.txt: Warning: vendor finding [GlobalOptionInConsumerRules]'

run_case no-findings 0 'No lint warnings.'
run_case approved-qlz-is-information 0 "${approved_qlz}"
run_case unknown-qlz-blocks 1 "${unknown_qlz}"
run_case face-alignment-blocks 1 "${face_alignment}"
run_case face-consumer-rules-block 1 "${face_rules}"
run_case approved-qlz-does-not-hide-face 1 "${approved_qlz}"$'\n'"${face_alignment}"

echo "[vendor-sdk-release-fixture][PASS] all cases"
