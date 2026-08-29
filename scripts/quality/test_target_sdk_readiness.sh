#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_target_sdk_readiness.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-target-readiness.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local scenario="$2"
  mkdir -p "${root}/scripts/quality" "${root}/app/src/main"
  local channel=beta
  local promotion=blocked
  local platform=unverified
  local vendor=unverified
  local adaptive=blocked
  local matrix=unverified
  local change_id=""
  local matrix_line='test_matrix_status=unverified'

  case "${scenario}" in
    valid) ;;
    missing-field) matrix_line="" ;;
    illegal-status) promotion=ready ;;
    forged-verified)
      channel=stable
      promotion=approved
      platform=verified
      vendor=verified
      adaptive=verified
      matrix=verified
      matrix_line='test_matrix_status=verified'
      change_id=target-api37
      mkdir -p "${root}/openspec/changes/${change_id}"
      ;;
    *) echo "unknown fixture scenario: ${scenario}" >&2; exit 2 ;;
  esac

  cat > "${root}/scripts/quality/target_sdk_readiness.properties" <<EOF
approved_target_sdk=36
candidate_target_sdk=37
candidate_platform_channel=${channel}
candidate_promotion=${promotion}
platform_behavior_status=${platform}
vendor_compatibility_status=${vendor}
adaptive_compatibility_status=${adaptive}
${matrix_line}
candidate_change_id=${change_id}
EOF
  cat > "${root}/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <activity android:name=".MainActivity" android:screenOrientation="portrait">
      <property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true" />
    </activity>
  </application>
</manifest>
EOF
}

valid="${FIXTURE_ROOT}/valid"
write_fixture "${valid}" valid
bash "${VERIFY_SCRIPT}" --project-root "${valid}" >/dev/null

for case_data in "missing-field:test_matrix_status" "illegal-status:illegal candidate_promotion" "forged-verified:adaptive_compatibility_status cannot be verified"; do
  scenario="${case_data%%:*}"
  expected="${case_data#*:}"
  root="${FIXTURE_ROOT}/${scenario}"
  write_fixture "${root}" "${scenario}"
  if bash "${VERIFY_SCRIPT}" --project-root "${root}" >"${root}.log" 2>&1; then
    echo "[target-sdk-readiness-test][FAIL] ${scenario} unexpectedly passed." >&2
    exit 1
  fi
  grep -Fq "${expected}" "${root}.log"
done

echo "[target-sdk-readiness-test][PASS] all readiness fixtures passed."
