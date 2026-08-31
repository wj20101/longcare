#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="${ROOT_DIR}/scripts/quality/affected-modules.sh"
PASS_COUNT=0

fail() {
  echo "[affected-modules-test][FAIL] $1" >&2
  exit 1
}

assert_plan_contains() {
  local label="$1"
  local changed_file="$2"
  shift 2
  local output
  output="$(
    AFFECTED_MODULES_CHANGED_FILES="${changed_file}" \
      bash "${SCRIPT}" --format text
  )"

  local expected
  for expected in "$@"; do
    if ! grep -Fq -- "${expected}" <<< "${output}"; then
      printf '%s\n' "${output}" >&2
      fail "${label}: expected '${expected}'"
    fi
  done
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[affected-modules-test][PASS] ${label}"
}

assert_plan_contains \
  "app login assembly" \
  "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt" \
  "affected_modules=:app" \
  "verify_tasks=:app:lintDebug :app:assembleDebug" \
  "run_instrumentation=true" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=false" \
  "com.ytone.longcare.navigation.EntryNavigationInstrumentationTest"

APP_NAV_PLAN="$(
  AFFECTED_MODULES_CHANGED_FILES="app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt" \
    bash "${SCRIPT}" --format text
)"
APP_SMOKE_LINE="$(grep '^smoke_test_classes=' <<< "${APP_NAV_PLAN}")"
if grep -Fq -- "com.ytone.longcare.features.home.ui.HomeExperienceContentTest" <<< "${APP_SMOKE_LINE}"; then
  printf '%s\n' "${APP_NAV_PLAN}" >&2
  fail "app login assembly: Home feature selector leaked into app test APK"
fi
PASS_COUNT=$((PASS_COUNT + 1))
echo "[affected-modules-test][PASS] app test APK excludes Home feature selector"

assert_plan_contains \
  "login feature UI" \
  "feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" \
  "affected_modules=:feature:login" \
  ":feature:login:compileDebugKotlin" \
  ":feature:login:testDebugUnitTest" \
  ":feature:login:lintDebug" \
  ":feature:login:compileDebugAndroidTestKotlin" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=true" \
  "com.ytone.longcare.features.login.ui.LoginScreenAgreementDialogTest"

assert_plan_contains \
  "dashboard production UI" \
  "feature/home/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt" \
  "affected_modules=:feature:home" \
  ":feature:home:compileDebugKotlin" \
  ":feature:home:testDebugUnitTest" \
  ":feature:home:lintDebug" \
  ":feature:home:compileDebugAndroidTestKotlin" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=true" \
  "run_login_feature_instrumentation=false" \
  "com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest" \
  "com.ytone.longcare.features.profile.ui.ProfileScreenComponentsAdaptationTest"

assert_plan_contains \
  "dashboard instrumentation test" \
  "feature/home/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt" \
  "affected_modules=:feature:home" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=true" \
  "run_login_feature_instrumentation=false"

assert_plan_contains \
  "dashboard JVM resolver" \
  "feature/home/src/test/kotlin/com/ytone/longcare/features/maindashboard/ui/InfoCardLayoutSpecResolverTest.kt" \
  "affected_modules=:feature:home" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=false"

assert_plan_contains \
  "company name domain contract" \
  "core/domain/src/main/kotlin/com/ytone/longcare/domain/system/CompanyNameProvider.kt" \
  "affected_modules=:core:domain,:core:data,:feature:home" \
  ":feature:home:compileDebugKotlin" \
  ":feature:home:testDebugUnitTest" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=false"

assert_plan_contains \
  "architecture documentation" \
  "docs/architecture/ci-quality-gates.md" \
  "affected_modules=:app" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=false"

assert_plan_contains \
  "quality script" \
  "scripts/quality/verify_login_feature_boundary.sh" \
  "affected_scope=full" \
  ":feature:home:compileDebugKotlin" \
  ":feature:login:compileDebugKotlin" \
  "run_instrumentation=false" \
  "run_home_feature_instrumentation=false" \
  "run_login_feature_instrumentation=false"

echo "[affected-modules-test] all ${PASS_COUNT} changed-path fixtures passed."
