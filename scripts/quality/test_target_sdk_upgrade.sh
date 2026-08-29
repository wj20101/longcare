#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_target_sdk_upgrade.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-target-sdk.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local min_sdk="$2"
  local target_sdk="$3"
  local compile_sdk="$4"
  local ci_api="$5"
  mkdir -p "${root}/.github/workflows"
  cat > "${root}/settings.gradle.kts" <<EOF
android {
    compileSdk { version = release(${compile_sdk}) }
    minSdk { version = release(${min_sdk}) }
    targetSdk { version = release(${target_sdk}) }
}
EOF
  cat > "${root}/.github/workflows/android-ci.yml" <<EOF
steps:
  - name: Emulator
    with:
      api-level: ${ci_api}
EOF
  mkdir -p "${root}/scripts/quality" "${root}/app/src/main"
  cat > "${root}/scripts/quality/target_sdk_readiness.properties" <<'EOF'
approved_target_sdk=36
candidate_target_sdk=37
candidate_platform_channel=beta
candidate_promotion=blocked
platform_behavior_status=unverified
vendor_compatibility_status=unverified
adaptive_compatibility_status=blocked
test_matrix_status=unverified
candidate_change_id=
EOF
  cat > "${root}/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application /></manifest>
EOF
}

run_guard() {
  local root="$1"
  bash "${VERIFY_SCRIPT}" \
    "${root}/settings.gradle.kts" \
    "${root}/.github/workflows/android-ci.yml" \
    "${root}/scripts/quality/target_sdk_readiness.properties" \
    "${root}/app/src/main/AndroidManifest.xml"
}

valid="${FIXTURE_ROOT}/valid"
write_fixture "${valid}" 24 36 37 36
run_guard "${valid}" >/dev/null
[[ ! -e "${valid}/constants.gradle.kts" ]]

invalid_order="${FIXTURE_ROOT}/invalid-order"
write_fixture "${invalid_order}" 37 36 37 37
if run_guard "${invalid_order}" >"${invalid_order}.log" 2>&1; then
  echo "[target-sdk-upgrade-test][FAIL] invalid SDK order unexpectedly passed." >&2
  exit 1
fi
grep -Fq "minSdk(37) > targetSdk(36)" "${invalid_order}.log"

ci_low="${FIXTURE_ROOT}/ci-low"
write_fixture "${ci_low}" 24 36 37 35
if run_guard "${ci_low}" >"${ci_low}.log" 2>&1; then
  echo "[target-sdk-upgrade-test][FAIL] low CI API unexpectedly passed." >&2
  exit 1
fi
grep -Fq "max emulator API in CI is 35" "${ci_low}.log"

candidate_blocked="${FIXTURE_ROOT}/candidate-blocked"
write_fixture "${candidate_blocked}" 24 37 37 37
if run_guard "${candidate_blocked}" >"${candidate_blocked}.log" 2>&1; then
  echo "[target-sdk-upgrade-test][FAIL] blocked target 37 promotion unexpectedly passed." >&2
  exit 1
fi
for expected in candidate_platform_channel candidate_promotion platform_behavior_status vendor_compatibility_status adaptive_compatibility_status test_matrix_status candidate_change_id; do
  grep -Fq "${expected}" "${candidate_blocked}.log"
done

gmd_valid="${FIXTURE_ROOT}/gmd-valid"
write_fixture "${gmd_valid}" 24 36 37 35
cat > "${gmd_valid}/.github/workflows/android-ci.yml" <<'EOF'
steps:
  - name: Managed-device smoke
    run: ./gradlew :app:pixel6Api36DebugAndroidTest
EOF
cat > "${gmd_valid}/scripts/quality/target_platform_test_matrix.properties" <<'EOF'
current_target_api=36
current_target_blocking=true
current_target_device=pixel6Api36
EOF
run_guard "${gmd_valid}" >/dev/null

gmd_missing="${FIXTURE_ROOT}/gmd-missing"
write_fixture "${gmd_missing}" 24 36 37 35
cat > "${gmd_missing}/scripts/quality/target_platform_test_matrix.properties" <<'EOF'
current_target_api=36
current_target_blocking=true
current_target_device=pixel6Api36
EOF
if run_guard "${gmd_missing}" >"${gmd_missing}.log" 2>&1; then
  echo "[target-sdk-upgrade-test][FAIL] missing blocking GMD task unexpectedly passed." >&2
  exit 1
fi
grep -Fq ":pixel6Api36DebugAndroidTest is not invoked" "${gmd_missing}.log"

echo "[target-sdk-upgrade-test][PASS] all settings-based target SDK fixtures passed."
