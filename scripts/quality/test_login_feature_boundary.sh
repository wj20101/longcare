#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_login_feature_boundary.sh"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[login-boundary-test][FAIL] $1" >&2
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
    "${root}/feature/login/src/main/kotlin/com/ytone/longcare/feature/login/api"
  write_kotlin \
    "${root}" \
    "app/src/main/kotlin/com/ytone/longcare/navigation/LoginRoute.kt" \
    $'package fixture\nimport com.ytone.longcare.feature.login.api.LoginFeatureScreen'
  write_kotlin \
    "${root}" \
    "feature/login/src/main/kotlin/com/ytone/longcare/feature/login/api/LoginFeatureScreen.kt" \
    $'package fixture\nimport androidx.compose.runtime.Composable'
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
  echo "[login-boundary-test][PASS] ${label}"
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
  if ! grep -Fq -- "[login-boundary][HINT] fix=" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected remediation hint"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[login-boundary-test][PASS] ${label}"
}

GOOD_ROOT="$(new_fixture good)"
expect_success "public API boundary" "${GOOD_ROOT}"

LEGACY_ROOT="$(new_fixture legacy-ui)"
LEGACY_FILE="app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt"
write_kotlin "${LEGACY_ROOT}" "${LEGACY_FILE}" 'package fixture'
expect_failure "app recreates login UI" "${LEGACY_ROOT}" "legacy-app-login-ui" "${LEGACY_FILE}"

RESOURCE_ROOT="$(new_fixture feature-app-resource)"
RESOURCE_FILE="feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt"
write_kotlin "${RESOURCE_ROOT}" "${RESOURCE_FILE}" $'package fixture\nimport com.ytone.longcare.R'
expect_failure "feature imports app resources" "${RESOURCE_ROOT}" "feature-imports-app-shell" "${RESOURCE_FILE}"

ACTIVITY_ROOT="$(new_fixture feature-activity)"
ACTIVITY_FILE="feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginValidationEntrySheet.kt"
write_kotlin "${ACTIVITY_ROOT}" "${ACTIVITY_FILE}" $'package fixture\nimport android.content.Intent'
expect_failure "feature starts Activity directly" "${ACTIVITY_ROOT}" "feature-starts-platform-component" "${ACTIVITY_FILE}"

INTERNAL_ROOT="$(new_fixture app-internal)"
INTERNAL_FILE="app/src/main/kotlin/com/ytone/longcare/navigation/LoginInternalRoute.kt"
write_kotlin "${INTERNAL_ROOT}" "${INTERNAL_FILE}" $'package fixture\nimport com.ytone.longcare.features.login.ui.LoginRouteScreen'
expect_failure "app bypasses login public API" "${INTERNAL_ROOT}" "app-imports-login-internal" "${INTERNAL_FILE}"

echo "[login-boundary-test] all ${PASS_COUNT} fixtures passed."
