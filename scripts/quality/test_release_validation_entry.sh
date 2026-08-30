#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_release_validation_entry.sh"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[release-validation-entry-test][FAIL] $1" >&2
  exit 1
}

write_file() {
  local root="$1"
  local relative_path="$2"
  local content="$3"
  mkdir -p "$(dirname "${root}/${relative_path}")"
  printf '%s\n' "${content}" > "${root}/${relative_path}"
}

new_fixture() {
  local name="$1"
  local root="${TMP_ROOT}/${name}"
  local sheet_content
  local actions_content

  sheet_content=$'validationEntryActions.onOpenCameraValidation()\nvalidationEntryActions.onOpenBackupFaceVerification()\nvalidationEntryActions.onOpenManualFaceCapture()\nvalidationEntryActions.onOpenFaceVerificationValidation()\nvalidationEntryActions.onOpenNfcValidation()'
  actions_content=$'onOpenCameraValidation = { navigateToCamera(\n}\nonOpenBackupFaceVerification = { navigateToFaceVerificationWithAutoSign()\n}\nonOpenManualFaceCapture = { navigateToManualFaceCapture()\n}\nonOpenFaceVerificationValidation = { Intent(context, FaceVerificationValidationActivity::class.java)\n}\nonOpenNfcValidation = { Intent(context, NfcValidationActivity::class.java)\n}'

  write_file "${root}" "feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginValidationEntrySheet.kt" "${sheet_content}"
  write_file "${root}" "feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" 'onMainLogoLongPress = { showValidationEntrySheet = true }'
  write_file "${root}" "feature/login/src/main/kotlin/com/ytone/longcare/feature/login/api/LoginFeatureActions.kt" 'data class LoginFeatureActions(val value: Unit)'
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/navigation/LoginValidationEntryNavigationActions.kt" "${actions_content}"
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/presentation/validation/FaceVerificationValidationActivity.kt" 'class FaceVerificationValidationActivity'
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/presentation/validation/NfcValidationActivity.kt" 'class NfcValidationActivity'
  write_file "${root}" "app/src/main/kotlin/com/ytone/longcare/presentation/validation/nfc/NfcValidationScreen.kt" 'fun NfcValidationScreen() = Unit'
  write_file "${root}" "app/src/main/AndroidManifest.xml" $'android:name=".presentation.validation.FaceVerificationValidationActivity"\nandroid:name=".presentation.validation.NfcValidationActivity"'
  printf '%s' "${root}"
}

expect_success() {
  local label="$1"
  local root="$2"
  local output=""
  local status=0
  output="$(bash "${GUARD}" "${root}" 2>&1)" || status=$?
  if [[ "${status}" -ne 0 || "${output}" != *"[release-validation-entry][PASS]"* ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected success"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[release-validation-entry-test][PASS] ${label}"
}

expect_failure() {
  local label="$1"
  local root="$2"
  local expected="$3"
  local output=""
  local status=0
  output="$(bash "${GUARD}" "${root}" 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected non-zero exit"
  fi
  if ! grep -Fq -- "${expected}" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected diagnostic '${expected}'"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[release-validation-entry-test][PASS] ${label}"
}

GOOD_ROOT="$(new_fixture good)"
expect_success "shared main entry with five app actions" "${GOOD_ROOT}"

DEBUG_ROOT="$(new_fixture debug-only)"
write_file \
  "${DEBUG_ROOT}" \
  "feature/login/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" \
  $'onMainLogoLongPress = { showValidationEntrySheet = true }\nif (BuildConfig.DEBUG)'
expect_failure "debug-only gate is rejected" "${DEBUG_ROOT}" 'must not contain: if (BuildConfig.DEBUG)'

MISSING_ACTION_ROOT="$(new_fixture missing-action)"
write_file \
  "${MISSING_ACTION_ROOT}" \
  "app/src/main/kotlin/com/ytone/longcare/navigation/LoginValidationEntryNavigationActions.kt" \
  $'onOpenCameraValidation = { navigateToCamera(\n}\nonOpenBackupFaceVerification = { navigateToFaceVerificationWithAutoSign()\n}\nonOpenManualFaceCapture = {\n}\nonOpenFaceVerificationValidation = { Intent(context, FaceVerificationValidationActivity::class.java)\n}\nonOpenNfcValidation = { Intent(context, NfcValidationActivity::class.java)\n}'
expect_failure "missing app action implementation is rejected" "${MISSING_ACTION_ROOT}" 'must contain: navigateToManualFaceCapture()'

echo "[release-validation-entry-test] all ${PASS_COUNT} fixtures passed."
