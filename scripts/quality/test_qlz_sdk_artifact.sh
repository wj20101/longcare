#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_qlz_sdk_artifact.sh"
SOURCE_AAR="${ROOT_DIR}/app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar"
APPROVED_NAME="qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

run_case() {
  local name="$1"
  local expected_status="$2"
  shift 2

  local output_file="${TMP_DIR}/${name}.log"
  local actual_status=0
  bash "${GUARD}" "$@" >"${output_file}" 2>&1 || actual_status=$?
  if [[ "${actual_status}" -ne "${expected_status}" ]]; then
    echo "[qlz-artifact-fixture][FAIL] ${name}: expected ${expected_status}, got ${actual_status}" >&2
    sed 's/^/  /' "${output_file}" >&2
    exit 1
  fi
  echo "[qlz-artifact-fixture][PASS] ${name}"
}

valid_libs="${TMP_DIR}/valid-libs"
mkdir -p "${valid_libs}"
cp "${SOURCE_AAR}" "${valid_libs}/${APPROVED_NAME}"
run_case approved-aar 0 \
  --aar-path "${valid_libs}/${APPROVED_NAME}" \
  --local-libs-dir "${valid_libs}"

missing_libs="${TMP_DIR}/missing-libs"
mkdir -p "${missing_libs}"
run_case missing-aar 1 \
  --aar-path "${missing_libs}/${APPROVED_NAME}" \
  --local-libs-dir "${missing_libs}"

changed_libs="${TMP_DIR}/changed-libs"
mkdir -p "${changed_libs}"
cp "${SOURCE_AAR}" "${changed_libs}/${APPROVED_NAME}"
printf 'fixture-byte-change' >> "${changed_libs}/${APPROVED_NAME}"
run_case changed-digest 1 \
  --aar-path "${changed_libs}/${APPROVED_NAME}" \
  --local-libs-dir "${changed_libs}"

renamed_libs="${TMP_DIR}/renamed-libs"
mkdir -p "${renamed_libs}"
cp "${SOURCE_AAR}" "${renamed_libs}/qlzsdk-renamed.aar"
run_case renamed-aar 1 \
  --aar-path "${renamed_libs}/qlzsdk-renamed.aar" \
  --local-libs-dir "${renamed_libs}"

duplicate_libs="${TMP_DIR}/duplicate-libs"
mkdir -p "${duplicate_libs}"
cp "${SOURCE_AAR}" "${duplicate_libs}/${APPROVED_NAME}"
cp "${SOURCE_AAR}" "${duplicate_libs}/qlzsdk-second-copy.aar"
run_case duplicate-aar 1 \
  --aar-path "${duplicate_libs}/${APPROVED_NAME}" \
  --local-libs-dir "${duplicate_libs}"

run_case unknown-argument 2 --future-flag value

echo "[qlz-artifact-fixture][PASS] all cases"
