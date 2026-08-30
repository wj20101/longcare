#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_identification_feature_boundary.sh"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[identification-boundary-test][FAIL] $1" >&2
  exit 1
}

write_kotlin() {
  local root="$1"
  local relative_path="$2"
  local content="$3"
  mkdir -p "$(dirname "${root}/${relative_path}")"
  printf '%s\n' "${content}" > "${root}/${relative_path}"
}

new_fixture() {
  local name="$1"
  local root="${TMP_ROOT}/${name}"
  mkdir -p \
    "${root}/app/src/main/kotlin/com/ytone/longcare/navigation" \
    "${root}/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/api"
  write_kotlin \
    "${root}" \
    "app/src/main/kotlin/com/ytone/longcare/navigation/IdentificationRoute.kt" \
    $'package fixture\nimport com.ytone.longcare.features.identification.api.IdentificationFeatureScreen'
  write_kotlin \
    "${root}" \
    "feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/api/IdentificationFeatureScreen.kt" \
    $'package fixture\nimport com.ytone.longcare.model.OrderKey'
  printf '%s' "${root}"
}

expect_success() {
  local label="$1"
  local root="$2"
  local output=""
  local status=0
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -ne 0 || "${output}" != *"verification passed"* ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected success"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[identification-boundary-test][PASS] ${label}"
}

expect_failure() {
  local label="$1"
  local root="$2"
  local rule_id="$3"
  local expected_file="$4"
  local output=""
  local status=0
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected non-zero exit"
  fi
  if ! grep -Fq -- "rule=${rule_id} file=${expected_file}" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected rule and offending file"
  fi
  if ! grep -Fq -- "[identification-boundary][HINT] fix=" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected remediation hint"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[identification-boundary-test][PASS] ${label}"
}

GOOD_ROOT="$(new_fixture good)"
expect_success "public API boundary" "${GOOD_ROOT}"

LEGACY_ROOT="$(new_fixture legacy-ui)"
LEGACY_FILE="app/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreen.kt"
write_kotlin "${LEGACY_ROOT}" "${LEGACY_FILE}" 'package fixture'
expect_failure \
  "app recreates identification UI" \
  "${LEGACY_ROOT}" \
  "legacy-app-identification-ui" \
  "${LEGACY_FILE}"

PLATFORM_ROOT="$(new_fixture feature-platform)"
PLATFORM_FILE="feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreen.kt"
write_kotlin \
  "${PLATFORM_ROOT}" \
  "${PLATFORM_FILE}" \
  $'package fixture\nimport com.ytone.longcare.platform.face.FaceSdkUiController'
expect_failure \
  "feature imports app platform adapter" \
  "${PLATFORM_ROOT}" \
  "feature-imports-app-shell" \
  "${PLATFORM_FILE}"

INTERNAL_ROOT="$(new_fixture app-internal)"
INTERNAL_FILE="app/src/main/kotlin/com/ytone/longcare/navigation/IdentificationInternalRoute.kt"
write_kotlin \
  "${INTERNAL_ROOT}" \
  "${INTERNAL_FILE}" \
  $'package fixture\nimport com.ytone.longcare.features.identification.ui.IdentificationScreen'
expect_failure \
  "app bypasses identification public API" \
  "${INTERNAL_ROOT}" \
  "app-imports-identification-internal" \
  "${INTERNAL_FILE}"

echo "[identification-boundary-test] all ${PASS_COUNT} fixtures passed."
