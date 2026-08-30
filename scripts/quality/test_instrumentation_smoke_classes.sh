#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_instrumentation_smoke_classes.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-smoke-classes.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local fqcn="$2"
  mkdir -p "${root}/scripts/quality" "${root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke"
  cat > "${root}/scripts/quality/source.sh" <<EOF
SMOKE_CLASSES="${fqcn}"
EOF
  cat > "${root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke/RealSmokeTest.kt" <<'EOF'
package com.ytone.longcare.smoke
class RealSmokeTest
EOF
}

valid_root="${FIXTURE_ROOT}/valid"
write_fixture "${valid_root}" "com.ytone.longcare.smoke.RealSmokeTest"
bash "${VERIFY_SCRIPT}" --project-root "${valid_root}" --source scripts/quality/source.sh >/dev/null

missing_root="${FIXTURE_ROOT}/missing"
write_fixture "${missing_root}" "com.ytone.longcare.smoke.MissingSmokeTest"
if bash "${VERIFY_SCRIPT}" --project-root "${missing_root}" --source scripts/quality/source.sh >"${missing_root}.log" 2>&1; then
  echo "[instrumentation-smoke-classes-test][FAIL] missing class fixture unexpectedly passed." >&2
  exit 1
fi
grep -Fq "com.ytone.longcare.smoke.MissingSmokeTest" "${missing_root}.log"
grep -Fq "scripts/quality/source.sh" "${missing_root}.log"

owned_root="${FIXTURE_ROOT}/owned"
mkdir -p \
  "${owned_root}/scripts/quality" \
  "${owned_root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke" \
  "${owned_root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/features/login/ui"
cat > "${owned_root}/scripts/quality/matrix.properties" <<'EOF'
app_classes=com.ytone.longcare.smoke.RealAppSmokeTest
login_classes=com.ytone.longcare.features.login.ui.RealLoginSmokeTest
EOF
cat > "${owned_root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke/RealAppSmokeTest.kt" <<'EOF'
package com.ytone.longcare.smoke
class RealAppSmokeTest
EOF
cat > "${owned_root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/RealLoginSmokeTest.kt" <<'EOF'
package com.ytone.longcare.features.login.ui
class RealLoginSmokeTest
EOF
bash "${VERIFY_SCRIPT}" \
  --project-root "${owned_root}" \
  --owned-field scripts/quality/matrix.properties app_classes app/src/androidTest \
  --owned-field scripts/quality/matrix.properties login_classes feature/login/src/androidTest >/dev/null

cross_owner_root="${FIXTURE_ROOT}/cross-owner"
mkdir -p \
  "${cross_owner_root}/scripts/quality" \
  "${cross_owner_root}/app/src/androidTest/kotlin" \
  "${cross_owner_root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/smoke"
cat > "${cross_owner_root}/scripts/quality/matrix.properties" <<'EOF'
app_classes=com.ytone.longcare.smoke.WrongOwnerSmokeTest
EOF
cat > "${cross_owner_root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/smoke/WrongOwnerSmokeTest.kt" <<'EOF'
package com.ytone.longcare.smoke
class WrongOwnerSmokeTest
EOF
if bash "${VERIFY_SCRIPT}" \
  --project-root "${cross_owner_root}" \
  --owned-field scripts/quality/matrix.properties app_classes app/src/androidTest \
  >"${cross_owner_root}.log" 2>&1; then
  echo "[instrumentation-smoke-classes-test][FAIL] cross-owner fixture unexpectedly passed." >&2
  exit 1
fi
grep -Fq "WrongOwnerSmokeTest" "${cross_owner_root}.log"
grep -Fq "app/src/androidTest" "${cross_owner_root}.log"

echo "[instrumentation-smoke-classes-test][PASS] all smoke class fixtures passed."
