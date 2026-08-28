#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/lint/verify_lint_warning_allowlist.sh"
WAIVERS="${ROOT_DIR}/scripts/lint/lint_warning_waivers.json"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

run_case() {
  local name="$1"
  local expected_status="$2"
  local warning_line="$3"
  local report_path="${TMP_DIR}/${name}.txt"
  local output_path="${TMP_DIR}/${name}.log"
  local actual_status=0

  printf '%s\n' "${warning_line}" > "${report_path}"
  LINT_WAIVER_DATE=2026-08-28 LINT_ENFORCE_UNUSED_WAIVERS=false \
    bash "${GUARD}" "${report_path}" "${WAIVERS}" > "${output_path}" 2>&1 || actual_status=$?
  if [[ "${actual_status}" -ne "${expected_status}" ]]; then
    echo "[lint-waiver-fixture][FAIL] ${name}: expected ${expected_status}, got ${actual_status}" >&2
    sed 's/^/  /' "${output_path}" >&2
    exit 1
  fi
  echo "[lint-waiver-fixture][PASS] ${name}"
}

run_case approved-qlz 0 \
  '/cache/jetified-qlzsdk-1.3.0.2-protobufLiteRelease-ui/jars/classes.jar: Warning: vendor finding [TrustAllX509TrustManager]'
run_case approved-aar-filename 0 \
  '/workspace/app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar: Warning: vendor finding [TrustAllX509TrustManager]'
run_case unknown-qlz-version 1 \
  '/cache/jetified-qlzsdk-1.3.0.3-protobufLiteRelease-ui/jars/classes.jar: Warning: vendor finding [TrustAllX509TrustManager]'
run_case app-source 1 \
  '/workspace/app/src/main/kotlin/com/ytone/longcare/network/Unsafe.kt: Warning: project finding [TrustAllX509TrustManager]'
run_case other-dependency 1 \
  '/cache/jetified-other-network-sdk-9.0.0/jars/classes.jar: Warning: dependency finding [TrustAllX509TrustManager]'
run_case approved-navigation-pin 0 \
  '/workspace/gradle/libs.versions.toml:19: Warning: A newer version of androidx.navigation:navigation-compose than 2.9.8 is available: 2.10.0 [GradleDependency]'
run_case approved-camera-pin 0 \
  '/workspace/gradle/libs.versions.toml:28: Warning: A newer version of androidx.camera:camera-core than 1.6.1 is available: 1.6.2 [GradleDependency]'
run_case unknown-dependency-pin 1 \
  '/workspace/gradle/libs.versions.toml:30: Warning: A newer version of example:unexpected than 1.0.0 is available: 2.0.0 [GradleDependency]'

echo "[lint-waiver-fixture][PASS] all cases"
