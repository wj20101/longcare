#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_dependency_policy.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-dependency-policy.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local scenario="$2"
  mkdir -p "${root}/gradle" "${root}/scripts/quality" "${root}/app"

  local agp=9.3.2
  local extra_version=""
  local extra_allowlist=""
  local baseline_entry='androidxBaselineProfile|1.5.0-rc02|mobile-platform|AGP compatibility|profile generation|1.5.0'

  case "${scenario}" in
    success) ;;
    unapproved-beta) extra_version='experimental = "2.0.0-beta01"' ;;
    unapproved-compat) extra_version='kotlinxDatetime = "0.8.0-0.6.x-compat"' ;;
    missing-owner) extra_version='experimental = "2.0.0-beta01"'; extra_allowlist='experimental|2.0.0-beta01||temporary|tests|2.0.0' ;;
    generalized) extra_version='experimental = "2.0.0-beta01"'; extra_allowlist='experimental|2.0.*|mobile|temporary|tests|2.0.0' ;;
    stale) extra_allowlist='oldAlias|1.0.0-rc01|mobile|temporary|tests|1.0.0' ;;
    agp10) agp=10.0.0 ;;
    missing-baseline-waiver) baseline_entry="" ;;
    *) echo "unknown fixture scenario: ${scenario}" >&2; exit 2 ;;
  esac

  cat > "${root}/gradle/libs.versions.toml" <<EOF
[versions]
agp = "${agp}"
androidxBaselineProfile = "1.5.0-rc02"
androidxBenchmark = "1.5.0-rc02"
${extra_version}

[libraries]
EOF
  cat > "${root}/scripts/quality/dependency_preview_allowlist.txt" <<EOF
# alias|exact-version|owner|reason|validation-scope|exit-version
${baseline_entry}
androidxBenchmark|1.5.0-rc02|mobile-platform|aligned profile tools|macrobenchmark|1.5.0
${extra_allowlist}
EOF
  cat > "${root}/gradle.properties" <<'EOF'
android.enableJetifier=true
EOF
  cat > "${root}/app/build.gradle.kts" <<'EOF'
baselineProfile {
    warnings { maxAgpVersion = false }
}
EOF
}

run_case() {
  local scenario="$1"
  local expected_status="$2"
  local expected_text="${3:-}"
  local root="${FIXTURE_ROOT}/${scenario}"
  write_fixture "${root}" "${scenario}"
  status=0
  bash "${VERIFY_SCRIPT}" --project-root "${root}" >"${root}.log" 2>&1 || status=$?
  if [[ "${expected_status}" == "pass" && "${status}" -ne 0 ]]; then
    cat "${root}.log" >&2
    echo "[dependency-policy-test][FAIL] expected success: ${scenario}" >&2
    exit 1
  fi
  if [[ "${expected_status}" == "fail" && "${status}" -eq 0 ]]; then
    echo "[dependency-policy-test][FAIL] expected failure: ${scenario}" >&2
    exit 1
  fi
  if [[ -n "${expected_text}" ]] && ! grep -Fq "${expected_text}" "${root}.log"; then
    cat "${root}.log" >&2
    echo "[dependency-policy-test][FAIL] missing diagnostic '${expected_text}' for ${scenario}" >&2
    exit 1
  fi
}

run_case success pass
run_case unapproved-beta fail experimental
run_case unapproved-compat fail kotlinxDatetime
run_case missing-owner fail experimental
run_case generalized fail experimental
run_case stale fail oldAlias
run_case agp10 fail "AGP 10.0.0"
run_case missing-baseline-waiver fail "maxAgpVersion=false"

echo "[dependency-policy-test][PASS] all dependency policy fixtures passed."
