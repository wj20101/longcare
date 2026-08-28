#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_production_release_config.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

SENTINEL_SECRET="fixture-secret-value-must-not-appear"
PASSED=0

run_case() {
  local name="$1"
  local expected_status="$2"
  shift 2

  local output_file="${TMP_DIR}/${name}.log"
  local actual_status=0
  bash "${GUARD}" "$@" >"${output_file}" 2>&1 || actual_status=$?

  if [[ "${actual_status}" -ne "${expected_status}" ]]; then
    echo "[release-config-fixture][FAIL] ${name}: expected ${expected_status}, got ${actual_status}" >&2
    sed 's/^/  /' "${output_file}" >&2
    exit 1
  fi
  if grep -Fq "${SENTINEL_SECRET}" "${output_file}"; then
    echo "[release-config-fixture][FAIL] ${name}: diagnostic leaked a configuration value" >&2
    exit 1
  fi

  PASSED=$((PASSED + 1))
  echo "[release-config-fixture][PASS] ${name}"
}

valid_production=(
  --production-requested true
  --acceptance-requested false
  --qlz-key-present true
  --known-test-qlz-key-requested false
  --qlz-test-mode false
  --approved-qlz-aar-present true
  --approved-qlz-aar-hash-matches true
  --known-unsafe-face-sdk-present false
)

valid_acceptance=(
  --production-requested false
  --acceptance-requested true
  --qlz-key-present true
  --known-test-qlz-key-requested true
  --qlz-test-mode true
  --approved-qlz-aar-present true
  --approved-qlz-aar-hash-matches true
  --known-unsafe-face-sdk-present true
)

run_case valid-production 0 "${valid_production[@]}"
run_case valid-acceptance 0 "${valid_acceptance[@]}"
run_case conflicting-modes 1 "${valid_production[@]}" --acceptance-requested true
run_case implicit-non-production 1 \
  --production-requested false --acceptance-requested false \
  --qlz-key-present true --known-test-qlz-key-requested false --qlz-test-mode false \
  --approved-qlz-aar-present true --approved-qlz-aar-hash-matches true \
  --known-unsafe-face-sdk-present false
run_case production-missing-key 1 "${valid_production[@]}" --qlz-key-present false
run_case production-known-test-key 1 "${valid_production[@]}" --known-test-qlz-key-requested true
run_case production-test-mode 1 "${valid_production[@]}" --qlz-test-mode true
run_case production-missing-aar 1 "${valid_production[@]}" --approved-qlz-aar-present false
run_case production-aar-hash-change 1 "${valid_production[@]}" --approved-qlz-aar-hash-matches false
run_case production-face-blocker 1 "${valid_production[@]}" --known-unsafe-face-sdk-present true
run_case acceptance-missing-key 1 "${valid_acceptance[@]}" --qlz-key-present false
run_case acceptance-production-mode 1 "${valid_acceptance[@]}" --qlz-test-mode false
run_case acceptance-missing-aar 1 "${valid_acceptance[@]}" --approved-qlz-aar-present false
run_case acceptance-aar-hash-change 1 "${valid_acceptance[@]}" --approved-qlz-aar-hash-matches false
run_case unknown-argument 2 "${valid_production[@]}" --future-flag true
run_case invalid-boolean 2 "${valid_production[@]}" --qlz-test-mode maybe
run_case missing-value 2 --production-requested
run_case missing-required-argument 2 \
  --production-requested true --acceptance-requested false \
  --qlz-key-present true --qlz-test-mode false \
  --approved-qlz-aar-present true --approved-qlz-aar-hash-matches true \
  --known-unsafe-face-sdk-present false

echo "[release-config-fixture][PASS] ${PASSED} cases"
