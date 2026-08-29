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

echo "[instrumentation-smoke-classes-test][PASS] all smoke class fixtures passed."
