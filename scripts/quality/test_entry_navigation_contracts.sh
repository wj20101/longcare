#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_entry_navigation_contracts.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-entry-navigation.XXXXXX")"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[entry-navigation-contracts-test][FAIL] $1" >&2
  exit 1
}

write_file() {
  local root="$1"
  local relative="$2"
  local content="$3"
  mkdir -p "$(dirname "${root}/${relative}")"
  printf '%s\n' "${content}" > "${root}/${relative}"
}

new_fixture() {
  local name="$1"
  local root="${TMP_ROOT}/${name}"
  write_file "${root}" "gradle/libs.versions.toml" \
    $'androidxNavigation = "2.10.0"\nandroidx-navigation-testing = { module = "androidx.navigation:navigation-testing", version.ref = "androidxNavigation" }'
  write_file "${root}" "app/build.gradle.kts" \
    $'dependencies {\n    androidTestImplementation(libs.androidx.navigation.testing)\n}'
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt" \
    $'package fixture\ninternal class EntryDestinationRenderers'
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt" \
    $'package fixture\ninternal fun AppNavHost() = Unit'

  while IFS='|' read -r relative class_name; do
    write_file "${root}" "${relative}" "package fixture; class ${class_name}"
  done <<'TEST_CONTRACTS'
app/src/test/kotlin/com/ytone/longcare/navigation/AppEntryStateTest.kt|AppEntryStateTest
app/src/test/kotlin/com/ytone/longcare/navigation/AuthenticationNavigationCoordinatorTest.kt|AuthenticationNavigationCoordinatorTest
app/src/test/kotlin/com/ytone/longcare/navigation/StartOrderNavigationContractTest.kt|StartOrderNavigationContractTest
app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeExperienceTest.kt|HomeExperienceTest
app/src/test/kotlin/com/ytone/longcare/features/sales/SalesNavigationSnapshotTest.kt|SalesNavigationSnapshotTest
app/src/test/kotlin/com/ytone/longcare/features/sales/SalesBackReducerTest.kt|SalesBackReducerTest
app/src/androidTest/kotlin/com/ytone/longcare/navigation/EntryNavigationInstrumentationTest.kt|EntryNavigationInstrumentationTest
app/src/androidTest/kotlin/com/ytone/longcare/navigation/HomeGraphOwnerInstrumentationTest.kt|HomeGraphOwnerInstrumentationTest
app/src/androidTest/kotlin/com/ytone/longcare/features/home/ui/HomeExperienceContentTest.kt|HomeExperienceContentTest
app/src/androidTest/kotlin/com/ytone/longcare/features/sales/SalesNavigationStateRestorationTest.kt|SalesNavigationStateRestorationTest
TEST_CONTRACTS
  printf '%s' "${root}"
}

expect_success() {
  local label="$1"
  local root="$2"
  local output
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || {
    printf '%s\n' "${output}" >&2
    fail "${label}: expected success"
  }
  [[ "${output}" == *"[entry-navigation-contracts][PASS]"* ]] || fail "${label}: missing pass marker"
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[entry-navigation-contracts-test][PASS] ${label}"
}

expect_failure() {
  local label="$1"
  local root="$2"
  local rule_id="$3"
  local output=""
  local status=0
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  [[ "${status}" -ne 0 ]] || fail "${label}: expected failure"
  grep -Fq -- "rule=${rule_id}" <<< "${output}" || {
    printf '%s\n' "${output}" >&2
    fail "${label}: missing ${rule_id}"
  }
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[entry-navigation-contracts-test][PASS] ${label}"
}

GOOD_ROOT="$(new_fixture good)"
expect_success "valid focused contract" "${GOOD_ROOT}"

DEPENDENCY_ROOT="$(new_fixture dependency-leak)"
printf '%s\n' 'implementation(libs.androidx.navigation.testing)' >> "${DEPENDENCY_ROOT}/app/build.gradle.kts"
expect_failure "production dependency leak" "${DEPENDENCY_ROOT}" "navigation-testing-test-only"

PUBLIC_RENDERER_ROOT="$(new_fixture public-renderer)"
write_file \
  "${PUBLIC_RENDERER_ROOT}" \
  "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt" \
  $'package fixture\nclass EntryDestinationRenderers'
expect_failure "public renderer seam" "${PUBLIC_RENDERER_ROOT}" "entry-renderer-internal"

MISSING_TEST_ROOT="$(new_fixture missing-test)"
rm -f -- "${MISSING_TEST_ROOT}/app/src/androidTest/kotlin/com/ytone/longcare/navigation/EntryNavigationInstrumentationTest.kt"
expect_failure "missing instrumentation contract" "${MISSING_TEST_ROOT}" "entry-navigation-test-contract"

echo "[entry-navigation-contracts-test] all ${PASS_COUNT} fixtures passed."
