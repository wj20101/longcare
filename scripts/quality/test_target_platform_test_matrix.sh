#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_target_platform_test_matrix.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-platform-matrix.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local scenario="$2"
  mkdir -p "${root}/scripts/quality" "${root}/baselineprofile" "${root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke" "${root}/app/src/test" "${root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/features/login/ui"
  local current_api=36
  local checks='adaptive-window,message-queue-reflection-native,local-network,certificate-transparency-network,background-alarm-audio,vendor-sdk-startup'
  local contract='app/src/test/ContractTest.kt'
  [[ "${scenario}" == "api-mismatch" ]] && current_api=35
  [[ "${scenario}" == "missing-check" ]] && checks='adaptive-window,local-network'
  [[ "${scenario}" == "missing-contract" ]] && contract='app/src/test/MissingTest.kt'
  cat > "${root}/settings.gradle.kts" <<'EOF'
android {
  targetSdk { version = release(36) }
}
EOF
  cat > "${root}/scripts/quality/target_sdk_readiness.properties" <<'EOF'
approved_target_sdk=36
candidate_target_sdk=37
candidate_promotion=blocked
EOF
  cat > "${root}/baselineprofile/build.gradle.kts" <<'EOF'
create("pixel6Api33") { apiLevel = 33 }
EOF
  cat > "${root}/scripts/quality/target_platform_test_matrix.properties" <<EOF
baseline_profile_api=33
baseline_profile_device=pixel6Api33
current_target_api=${current_api}
current_target_blocking=true
current_target_device=pixel6Api36
current_target_smoke_classes=com.ytone.longcare.smoke.RealSmokeTest
current_target_login_feature_smoke_classes=com.ytone.longcare.features.login.ui.RealLoginSmokeTest
current_target_contract_tests=${contract}
release_device_evidence_required=nfc,location,camera,sales,qlz,tencent-face
candidate_target_api=37
candidate_target_blocking=false
candidate_target_device=pixelTabletApi37
candidate_smoke_classes=com.ytone.longcare.smoke.RealSmokeTest
candidate_target_login_feature_smoke_classes=com.ytone.longcare.features.login.ui.RealLoginSmokeTest
candidate_readiness_checks=${checks}
EOF
  cat > "${root}/feature/login/build.gradle.kts" <<'EOF'
managedDevices.localDevices {
  create("pixel6Api36") { apiLevel = 36 }
  create("pixelTabletApi37") { apiLevel = 37 }
}
EOF
  cat > "${root}/app/src/androidTest/kotlin/com/ytone/longcare/smoke/RealSmokeTest.kt" <<'EOF'
package com.ytone.longcare.smoke
class RealSmokeTest
EOF
  cat > "${root}/feature/login/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/RealLoginSmokeTest.kt" <<'EOF'
package com.ytone.longcare.features.login.ui
class RealLoginSmokeTest
EOF
  cat > "${root}/app/src/test/ContractTest.kt" <<'EOF'
class ContractTest
EOF
}

valid="${FIXTURE_ROOT}/valid"
write_fixture "${valid}" valid
bash "${VERIFY_SCRIPT}" --project-root "${valid}" >/dev/null

for case_data in "api-mismatch:current target matrix API" "missing-check:message-queue-reflection-native" "missing-contract:MissingTest.kt"; do
  scenario="${case_data%%:*}"
  expected="${case_data#*:}"
  root="${FIXTURE_ROOT}/${scenario}"
  write_fixture "${root}" "${scenario}"
  if bash "${VERIFY_SCRIPT}" --project-root "${root}" >"${root}.log" 2>&1; then
    echo "[target-platform-test-matrix-test][FAIL] ${scenario} unexpectedly passed." >&2
    exit 1
  fi
  grep -Fq "${expected}" "${root}.log"
done

echo "[target-platform-test-matrix-test][PASS] all platform matrix fixtures passed."
