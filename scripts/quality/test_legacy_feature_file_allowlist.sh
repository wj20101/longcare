#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_legacy_feature_file_allowlist.sh"
LEGACY_FEATURE_DIR_REL="app/src/main/kotlin/com/ytone/longcare/features"
ALLOWLIST_REL="scripts/quality/legacy_feature_files_allowlist.txt"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[legacy-file-allowlist-test][FAIL] $1" >&2
  exit 1
}

fixture_root() {
  printf '%s/%s' "${TMP_ROOT}" "$1"
}

create_scan_dir() {
  local root="$1"
  mkdir -p "${root}/${LEGACY_FEATURE_DIR_REL}"
}

create_allowlist_dir() {
  local root="$1"
  mkdir -p "${root}/scripts/quality"
}

create_kotlin_file() {
  local root="$1"
  local relative_path="$2"
  mkdir -p "$(dirname "${root}/${relative_path}")"
  printf 'package fixture\n' > "${root}/${relative_path}"
}

write_allowlist() {
  local root="$1"
  shift
  create_allowlist_dir "${root}"
  printf '%s\n' "$@" > "${root}/${ALLOWLIST_REL}"
}

expect_success() {
  local label="$1"
  local root="$2"
  local expected_path="$3"
  local output=""
  local status=0

  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -ne 0 ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected success, got exit ${status}"
  fi
  if ! grep -Fq -- "actual=1 allowlist=1 sets match" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: success summary missing"
  fi
  if [[ ! -f "${root}/${expected_path}" ]]; then
    fail "${label}: fixture source unexpectedly missing after guard run: ${expected_path}"
  fi

  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[legacy-file-allowlist-test][PASS] ${label}"
}

expect_failure() {
  local label="$1"
  local root="$2"
  local expected_output="$3"
  local output=""
  local status=0

  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected non-zero exit"
  fi
  if ! grep -Fq -- "${expected_output}" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected output not found: ${expected_output}"
  fi

  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[legacy-file-allowlist-test][PASS] ${label}"
}

MATCHING_ROOT="$(fixture_root matching)"
MATCHING_PATH="${LEGACY_FEATURE_DIR_REL}/nested/Existing.kt"
create_scan_dir "${MATCHING_ROOT}"
create_kotlin_file "${MATCHING_ROOT}" "${MATCHING_PATH}"
write_allowlist "${MATCHING_ROOT}" "# exact snapshot" "" "./${MATCHING_PATH}" "./${MATCHING_PATH}"
expect_success "matching sets" "${MATCHING_ROOT}" "${MATCHING_PATH}"

UNEXPECTED_ROOT="$(fixture_root unexpected)"
UNEXPECTED_PATH="${LEGACY_FEATURE_DIR_REL}/newflow/Unexpected.kt"
create_scan_dir "${UNEXPECTED_ROOT}"
create_kotlin_file "${UNEXPECTED_ROOT}" "${UNEXPECTED_PATH}"
write_allowlist "${UNEXPECTED_ROOT}" "# intentionally empty"
expect_failure "unexpected file" "${UNEXPECTED_ROOT}" "offending_file=${UNEXPECTED_PATH}"

STALE_ROOT="$(fixture_root stale)"
STALE_PATH="${LEGACY_FEATURE_DIR_REL}/removed/Stale.kt"
create_scan_dir "${STALE_ROOT}"
write_allowlist "${STALE_ROOT}" "${STALE_PATH}"
expect_failure "stale allowlist entry" "${STALE_ROOT}" "stale_entry=${STALE_PATH}"

REINTRODUCED_ROOT="$(fixture_root reintroduced)"
REINTRODUCED_PATH="${LEGACY_FEATURE_DIR_REL}/facecapture/FaceCaptureScreen.kt"
create_scan_dir "${REINTRODUCED_ROOT}"
create_kotlin_file "${REINTRODUCED_ROOT}" "${REINTRODUCED_PATH}"
write_allowlist "${REINTRODUCED_ROOT}" "# removed historical path must stay blocked"
expect_failure "reintroduced historical path" "${REINTRODUCED_ROOT}" "offending_file=${REINTRODUCED_PATH}"

MISSING_SCAN_ROOT="$(fixture_root missing-scan-dir)"
write_allowlist "${MISSING_SCAN_ROOT}" "# scan directory deliberately absent"
expect_failure "missing scan directory" "${MISSING_SCAN_ROOT}" "scan_dir=${LEGACY_FEATURE_DIR_REL}"

MISSING_ALLOWLIST_ROOT="$(fixture_root missing-allowlist)"
MISSING_ALLOWLIST_PATH="${LEGACY_FEATURE_DIR_REL}/Existing.kt"
create_scan_dir "${MISSING_ALLOWLIST_ROOT}"
create_kotlin_file "${MISSING_ALLOWLIST_ROOT}" "${MISSING_ALLOWLIST_PATH}"
expect_failure "missing allowlist" "${MISSING_ALLOWLIST_ROOT}" "rule_file=${ALLOWLIST_REL}"

echo "[legacy-file-allowlist-test] all ${PASS_COUNT} fixtures passed."
