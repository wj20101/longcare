#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
FAILURES=()

require_file() {
  local relative_path="$1"
  if [[ ! -f "${ROOT_DIR}/${relative_path}" ]]; then
    FAILURES+=("missing shared Release source: ${relative_path}")
  fi
}

reject_file() {
  local relative_path="$1"
  if [[ -f "${ROOT_DIR}/${relative_path}" ]]; then
    FAILURES+=("build-type implementation must be removed: ${relative_path}")
  fi
}

require_text() {
  local relative_path="$1"
  local expected="$2"
  if ! grep -Fq "${expected}" "${ROOT_DIR}/${relative_path}"; then
    FAILURES+=("${relative_path} must contain: ${expected}")
  fi
}

reject_text() {
  local relative_path="$1"
  local forbidden="$2"
  if grep -Fq "${forbidden}" "${ROOT_DIR}/${relative_path}"; then
    FAILURES+=("${relative_path} must not contain: ${forbidden}")
  fi
}

require_file "app/src/main/kotlin/com/ytone/longcare/presentation/validation/LoginValidationEntrySheet.kt"
require_file "app/src/main/kotlin/com/ytone/longcare/navigation/LoginValidationEntryNavigationActions.kt"
require_file "app/src/main/kotlin/com/ytone/longcare/presentation/validation/FaceVerificationValidationActivity.kt"
require_file "app/src/main/kotlin/com/ytone/longcare/presentation/validation/NfcValidationActivity.kt"
require_file "app/src/main/kotlin/com/ytone/longcare/presentation/validation/nfc/NfcValidationScreen.kt"

reject_file "app/src/debug/kotlin/com/ytone/longcare/features/login/ui/LoginTestEntrySheet.kt"
reject_file "app/src/release/kotlin/com/ytone/longcare/features/login/ui/LoginTestEntrySheet.kt"
reject_file "app/src/debug/kotlin/com/ytone/longcare/navigation/LoginTestEntryNavigationActions.kt"
reject_file "app/src/release/kotlin/com/ytone/longcare/navigation/LoginTestEntryNavigationActions.kt"

require_text \
  "app/src/main/AndroidManifest.xml" \
  'android:name=".presentation.validation.FaceVerificationValidationActivity"'
require_text \
  "app/src/main/AndroidManifest.xml" \
  'android:name=".presentation.validation.NfcValidationActivity"'
require_text \
  "app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" \
  'onMainLogoLongPress = { showValidationEntrySheet = true }'
reject_text \
  "app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt" \
  'if (BuildConfig.DEBUG)'

if [[ ${#FAILURES[@]} -gt 0 ]]; then
  echo "[release-validation-entry][FAIL] Release validation entry contract is not satisfied:" >&2
  printf '  - %s\n' "${FAILURES[@]}" >&2
  exit 1
fi

echo "[release-validation-entry][PASS] hidden validation entry is shared by Debug and Release"
