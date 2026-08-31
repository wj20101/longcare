#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_home_feature_boundary.sh"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[home-boundary-test][FAIL] $1" >&2
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
    "${root}/feature/home/src/main/kotlin/com/ytone/longcare/features/home/api"
  write_kotlin \
    "${root}" \
    "app/src/main/kotlin/com/ytone/longcare/navigation/HomeRoute.kt" \
    $'package fixture\nimport com.ytone.longcare.features.home.api.HomeFeatureScreen'
  write_kotlin \
    "${root}" \
    "feature/home/src/main/kotlin/com/ytone/longcare/features/home/api/HomeFeatureScreen.kt" \
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
  echo "[home-boundary-test][PASS] ${label}"
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
  grep -Fq -- "rule=${rule_id} file=${expected_file}" <<< "${output}" || {
    printf '%s\n' "${output}" >&2
    fail "${label}: expected rule and offending file"
  }
  grep -Fq -- "[home-boundary][ALLOW] api=" <<< "${output}" || fail "${label}: expected allowed API"
  grep -Fq -- "[home-boundary][HINT] fix=" <<< "${output}" || fail "${label}: expected remediation hint"
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[home-boundary-test][PASS] ${label}"
}

GOOD_ROOT="$(new_fixture good)"
expect_success "public API boundary" "${GOOD_ROOT}"

LEGACY_ROOT="$(new_fixture legacy-ui)"
LEGACY_FILE="app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt"
write_kotlin "${LEGACY_ROOT}" "${LEGACY_FILE}" 'package fixture'
expect_failure "app recreates Home UI" "${LEGACY_ROOT}" "legacy-app-home-implementation" "${LEGACY_FILE}"

INTERNAL_ROOT="$(new_fixture app-internal)"
INTERNAL_FILE="app/src/main/kotlin/com/ytone/longcare/navigation/HomeInternalRoute.kt"
write_kotlin "${INTERNAL_ROOT}" "${INTERNAL_FILE}" $'package fixture\nimport com.ytone.longcare.features.home.vm.HomeSharedViewModel'
expect_failure "app imports internal ViewModel" "${INTERNAL_ROOT}" "app-imports-home-internal" "${INTERNAL_FILE}"

RESOURCE_ROOT="$(new_fixture feature-app-resource)"
RESOURCE_FILE="feature/home/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt"
write_kotlin "${RESOURCE_ROOT}" "${RESOURCE_FILE}" $'package fixture\nimport com.ytone.longcare.R'
expect_failure "feature imports app resources" "${RESOURCE_ROOT}" "feature-imports-app-shell" "${RESOURCE_FILE}"

DATA_ROOT="$(new_fixture feature-data)"
DATA_FILE="feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeViewModel.kt"
write_kotlin "${DATA_ROOT}" "${DATA_FILE}" $'package fixture\nimport com.ytone.longcare.common.utils.SystemConfigManager'
expect_failure "feature imports Data implementation" "${DATA_ROOT}" "feature-imports-data-implementation" "${DATA_FILE}"

PLATFORM_ROOT="$(new_fixture feature-platform)"
PLATFORM_FILE="feature/home/src/main/kotlin/com/ytone/longcare/features/home/ui/HomePlatformEntry.kt"
write_kotlin "${PLATFORM_ROOT}" "${PLATFORM_FILE}" $'package fixture\nimport android.content.Intent'
expect_failure "feature uses platform type" "${PLATFORM_ROOT}" "feature-uses-platform-type" "${PLATFORM_FILE}"

NAV_ROOT="$(new_fixture feature-navigation)"
NAV_FILE="feature/home/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeNavigation.kt"
write_kotlin "${NAV_ROOT}" "${NAV_FILE}" $'package fixture\nimport com.ytone.longcare.navigation.HomeRoute'
expect_failure "feature imports app navigation" "${NAV_ROOT}" "feature-imports-app-shell" "${NAV_FILE}"

VENDOR_ROOT="$(new_fixture feature-vendor)"
VENDOR_FILE="feature/home/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeVendorEntry.kt"
write_kotlin "${VENDOR_ROOT}" "${VENDOR_FILE}" $'package fixture\nimport com.tencent.vendor.FaceSdk'
expect_failure "feature imports vendor SDK" "${VENDOR_ROOT}" "feature-imports-vendor-sdk" "${VENDOR_FILE}"

echo "[home-boundary-test] all ${PASS_COUNT} fixtures passed."
