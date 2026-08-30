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
  "run_login_feature_instrumentation=false" \
  "com.ytone.longcare.navigation.EntryNavigationInstrumentationTest"

assert_plan_contains \
  "login feature UI" \
  "feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" \
  "affected_modules=:feature:login" \
  ":feature:login:compileDebugKotlin" \
  ":feature:login:testDebugUnitTest" \
  ":feature:login:lintDebug" \
  ":feature:login:compileDebugAndroidTestKotlin" \
  "run_instrumentation=false" \
  "run_login_feature_instrumentation=true" \
  "com.ytone.longcare.features.login.ui.LoginScreenAgreementDialogTest"

assert_plan_contains \
  "quality script" \
  "scripts/quality/verify_login_feature_boundary.sh" \
  "affected_scope=full" \
  ":feature:login:compileDebugKotlin" \
  "run_login_feature_instrumentation=false"

echo "[affected-modules-test] all ${PASS_COUNT} changed-path fixtures passed."
