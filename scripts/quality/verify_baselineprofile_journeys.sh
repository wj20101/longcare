#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_FILE=""

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/verify_baselineprofile_journeys.sh [options]

Options:
  --project-root <path>  Project root to inspect (default: repository root)
  --config <path>        Scenario policy JSON (default: <root>/scripts/quality/startup_profile_quality.json)
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      [[ $# -ge 2 && -n "${2:-}" && "${2:-}" != --* ]] || {
        echo "[baselineprofile][FAIL] --project-root requires a path" >&2
        exit 1
      }
      ROOT_DIR="$2"
      shift 2
      ;;
    --config)
      [[ $# -ge 2 && -n "${2:-}" && "${2:-}" != --* ]] || {
        echo "[baselineprofile][FAIL] --config requires a path" >&2
        exit 1
      }
      CONFIG_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[baselineprofile][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

ROOT_DIR="$(cd "${ROOT_DIR}" && pwd)"
CONFIG_FILE="${CONFIG_FILE:-${ROOT_DIR}/scripts/quality/startup_profile_quality.json}"
CONFIG_VERIFIER="${ROOT_DIR}/scripts/quality/verify_startup_profile_quality_config.py"
PROFILE_SOURCE="${ROOT_DIR}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile"
GENERATOR_FILE="${PROFILE_SOURCE}/BaselineProfileGenerator.kt"
BENCHMARK_FILE="${PROFILE_SOURCE}/StartupBenchmarks.kt"
SCENARIO_FILE="${PROFILE_SOURCE}/ProfileScenario.kt"
DRIVER_FILE="${PROFILE_SOURCE}/ProfileScenarioDriver.kt"
APP_BUILD_FILE="${ROOT_DIR}/app/build.gradle.kts"
FLAVOR_APPLIER="${ROOT_DIR}/app/src/main/kotlin/com/ytone/longcare/di/AppFlavorInterceptorApplier.kt"
OFFLINE_INTERCEPTOR="${ROOT_DIR}/app/src/main/kotlin/com/ytone/longcare/network/interceptor/PerformanceOfflineInterceptor.kt"
PERFORMANCE_MANIFEST="${ROOT_DIR}/app/src/profile/AndroidManifest.xml"
SETUP_ACTIVITY="${ROOT_DIR}/app/src/profile/kotlin/com/ytone/longcare/performance/ProfileScenarioSetupActivity.kt"
TEST_MANIFEST="${ROOT_DIR}/baselineprofile/src/main/AndroidManifest.xml"
SETUP_CONTRACT_TEST="${PROFILE_SOURCE}/ProfileScenarioSetupContractTest.kt"

FAILURES=()

fail() {
  echo "[baselineprofile][FAIL] $*" >&2
  FAILURES+=("$*")
}

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    fail "missing required file: ${file#${ROOT_DIR}/}"
    return 1
  fi
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  [[ -f "${file}" ]] || return 0
  grep -Eq -- "${pattern}" "${file}" || fail "${message}"
}

reject_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  [[ -f "${file}" ]] || return 0
  if grep -Eq -- "${pattern}" "${file}"; then
    fail "${message}"
  fi
}

for required_file in \
  "${CONFIG_VERIFIER}" \
  "${CONFIG_FILE}" \
  "${GENERATOR_FILE}" \
  "${BENCHMARK_FILE}" \
  "${SCENARIO_FILE}" \
  "${DRIVER_FILE}" \
  "${APP_BUILD_FILE}" \
  "${FLAVOR_APPLIER}" \
  "${OFFLINE_INTERCEPTOR}" \
  "${PERFORMANCE_MANIFEST}" \
  "${SETUP_ACTIVITY}" \
  "${TEST_MANIFEST}" \
  "${SETUP_CONTRACT_TEST}"; do
  require_file "${required_file}" || true
done

if [[ -f "${CONFIG_VERIFIER}" && -f "${CONFIG_FILE}" ]]; then
  if ! python3 "${CONFIG_VERIFIER}" "${CONFIG_FILE}"; then
    fail "machine-readable startup profile policy is invalid"
  fi
fi

for source_file in "${GENERATOR_FILE}" "${BENCHMARK_FILE}" "${SCENARIO_FILE}" "${DRIVER_FILE}"; do
  reject_pattern "${source_file}" 'TODO' "TODO markers are forbidden in Profile journeys"
done

for source_file in "${GENERATOR_FILE}" "${BENCHMARK_FILE}" "${DRIVER_FILE}"; do
  reject_pattern "${source_file}" 'By\.pkg\(' "package-root-only readiness is forbidden: ${source_file#${ROOT_DIR}/}"
  reject_pattern "${source_file}" 'waitForIdle\(' "window-idle readiness is forbidden: ${source_file#${ROOT_DIR}/}"
  reject_pattern "${source_file}" 'Thread\.sleep\(|SystemClock\.sleep\(' "fixed sleeps are forbidden: ${source_file#${ROOT_DIR}/}"
  reject_pattern "${source_file}" '\.swipe\(' "blind coordinate swipes are forbidden: ${source_file#${ROOT_DIR}/}"
done
reject_pattern "${GENERATOR_FILE}" '\.pressBack\(' "unconditional back presses are forbidden in Profile generation"

if [[ -f "${GENERATOR_FILE}" ]]; then
  startup_count="$(grep -Ec 'includeInStartupProfile[[:space:]]*=[[:space:]]*true' "${GENERATOR_FILE}" || true)"
  baseline_only_count="$(grep -Ec 'includeInStartupProfile[[:space:]]*=[[:space:]]*false' "${GENERATOR_FILE}" || true)"
  [[ "${startup_count}" -eq 4 ]] || fail "generator must declare exactly four Startup collections (found ${startup_count})"
  [[ "${baseline_only_count}" -eq 2 ]] || fail "generator must declare exactly two Baseline-only collections (found ${baseline_only_count})"
  require_pattern "${GENERATOR_FILE}" 'scenarioDriver\.prepare\(scenario\)' "scenario preparation must be explicit"
  require_pattern "${GENERATOR_FILE}" 'baselineProfileRule\.collect' "generator must collect through BaselineProfileRule"
  if grep -q 'scenarioDriver\.prepare(scenario)' "${GENERATOR_FILE}" && grep -q 'baselineProfileRule\.collect' "${GENERATOR_FILE}"; then
    prepare_line="$(grep -n -m1 'scenarioDriver\.prepare(scenario)' "${GENERATOR_FILE}" | cut -d: -f1)"
    collect_line="$(grep -n -m1 'baselineProfileRule\.collect' "${GENERATOR_FILE}" | cut -d: -f1)"
    [[ "${prepare_line}" -lt "${collect_line}" ]] || fail "scenario preparation must occur before BaselineProfileRule.collect"
  fi
fi

if [[ -f "${SCENARIO_FILE}" ]]; then
  for scenario in FIRST_RUN_PRIVACY LOGGED_OUT CARE_HOME SALES_HOME CARE_SERVICE_RECORDS SALES_CUSTOMERS; do
    require_pattern "${SCENARIO_FILE}" "${scenario}" "ProfileScenario catalog is missing ${scenario}"
  done
  require_pattern "${SCENARIO_FILE}" 'By\.res\(' "Profile scenarios must resolve Compose tags through By.res(tag)"
  reject_pattern "${SCENARIO_FILE}" 'By\.res\([^,]+,' "Compose test tags must not use the package-qualified By.res overload"
fi

if [[ -f "${DRIVER_FILE}" ]]; then
  require_pattern "${DRIVER_FILE}" 'prepare\(scenario:[[:space:]]*ProfileScenario\)' "shared driver must expose deterministic scenario preparation"
  require_pattern "${DRIVER_FILE}" 'startAndAssert\(scenario:[[:space:]]*ProfileScenario\)' "shared driver must expose exact cold-start assertions"
  require_pattern "${DRIVER_FILE}" 'force-stop' "shared driver must force-stop the target after setup"
  require_pattern "${DRIVER_FILE}" 'missingTags' "scenario timeout diagnostics must identify missing tags"
fi

if [[ -f "${BENCHMARK_FILE}" ]]; then
  benchmark_delegate_count="$(grep -Ec 'benchmark\(ProfileScenario\.' "${BENCHMARK_FILE}" || true)"
  [[ "${benchmark_delegate_count}" -eq 8 ]] || fail "benchmark must expose exactly eight symmetric scenario/mode delegates (found ${benchmark_delegate_count})"
  require_pattern "${BENCHMARK_FILE}" 'private fun benchmark\([[:space:]]*scenario:[[:space:]]*ProfileScenario,[[:space:]]*compilationMode:[[:space:]]*CompilationMode' "None/Profile tests must share benchmark(scenario, compilationMode)"
  require_pattern "${BENCHMARK_FILE}" 'CompilationMode\.Partial\(BaselineProfileMode\.Require\)' "Profile benchmark must fail closed with BaselineProfileMode.Require"
  require_pattern "${BENCHMARK_FILE}" 'StartupMode\.COLD' "all startup benchmarks must use cold startup"
  require_pattern "${BENCHMARK_FILE}" 'iterations[[:space:]]*=[[:space:]]*10' "all startup benchmarks must use ten iterations"
  require_pattern "${BENCHMARK_FILE}" 'StartupTimingMetric\(' "startup benchmarks must capture TTID and TTFD"
  reject_pattern "${BENCHMARK_FILE}" 'benchmarkWithout|benchmarkWithProfile|benchmarkNone' "mode-specific benchmark helper divergence is forbidden"
fi

if [[ -f "${APP_BUILD_FILE}" ]]; then
  require_pattern "${APP_BUILD_FILE}" 'src/profile/kotlin' "app must bind the shared performance-only Kotlin source root"
  require_pattern "${APP_BUILD_FILE}" 'src/profile/AndroidManifest\.xml' "app must bind the shared performance-only Manifest"
  require_pattern "${APP_BUILD_FILE}" 'nonMinifiedRelease' "performance source root must be bound to nonMinifiedRelease"
  require_pattern "${APP_BUILD_FILE}" 'benchmarkRelease' "performance source root must be bound to benchmarkRelease"
  require_pattern "${APP_BUILD_FILE}" 'localPerformance' "performance target variants must use the isolated local signing config shared with the test APK"
  require_pattern "${APP_BUILD_FILE}" 'buildConfigField\("boolean",[[:space:]]*"PROFILE_OFFLINE_MODE",[[:space:]]*"false"\)' "production variants must default performance offline mode to false"
  require_pattern "${APP_BUILD_FILE}" '"PROFILE_OFFLINE_MODE"' "performance variants must override the deterministic offline boundary"
fi

if [[ -f "${FLAVOR_APPLIER}" ]]; then
  require_pattern "${FLAVOR_APPLIER}" 'BuildConfig\.PROFILE_OFFLINE_MODE' "network wiring must gate the offline boundary on a variant constant"
  require_pattern "${FLAVOR_APPLIER}" 'PerformanceOfflineInterceptor\(' "performance variants must install the deterministic offline interceptor"
fi

if [[ -f "${OFFLINE_INTERCEPTOR}" ]]; then
  require_pattern "${OFFLINE_INTERCEPTOR}" 'throw IOException\(OFFLINE_REASON\)' "performance network isolation must fail immediately without production traffic"
  require_pattern "${OFFLINE_INTERCEPTOR}" 'longcare-performance-offline' "performance offline boundary must expose an artifact leakage sentinel"
fi

if [[ -f "${PERFORMANCE_MANIFEST}" ]]; then
  require_pattern "${PERFORMANCE_MANIFEST}" 'protectionLevel="signature"' "Profile setup permission must be signature protected"
  require_pattern "${PERFORMANCE_MANIFEST}" 'ProfileScenarioSetupActivity' "performance Manifest must declare ProfileScenarioSetupActivity"
  require_pattern "${PERFORMANCE_MANIFEST}" 'android:permission="com\.ytone\.longcare\.permission\.PROFILE_SCENARIO_SETUP"' "Profile setup Activity must require the dedicated permission"
fi

if [[ -f "${TEST_MANIFEST}" ]]; then
  require_pattern "${TEST_MANIFEST}" 'uses-permission android:name="com\.ytone\.longcare\.permission\.PROFILE_SCENARIO_SETUP"' "baselineprofile test APK must request the dedicated signature permission"
fi

if [[ -f "${SETUP_CONTRACT_TEST}" ]]; then
  require_pattern "${SETUP_CONTRACT_TEST}" 'missingAndUnknownScenarioIdsFailClosedWithOneObservableNode' "setup contract must cover missing and unknown scenario ids"
  require_pattern "${SETUP_CONTRACT_TEST}" 'PROTECTION_SIGNATURE' "setup contract must verify signature permission protection"
  require_pattern "${SETUP_CONTRACT_TEST}" 'SIGNATURE_NO_MATCH' "setup contract must verify that a non-matching caller signature is rejected"
  require_pattern "${SETUP_CONTRACT_TEST}" 'PERMISSION_DENIED' "setup contract must verify that a non-matching caller lacks the setup permission"
fi

if [[ -f "${SETUP_ACTIVITY}" ]]; then
  require_pattern "${SETUP_ACTIVITY}" 'PrivacyConsentManager' "setup Activity must use PrivacyConsentManager"
  require_pattern "${SETUP_ACTIVITY}" 'UserSessionRepository' "setup Activity must use UserSessionRepository"
  require_pattern "${SETUP_ACTIVITY}" 'SessionLoginPayload' "setup Activity must use the production login payload"
  reject_pattern "${SETUP_ACTIVITY}" 'SharedPreferences|DataStore|RoomDatabase|openOrCreateDatabase|FileOutputStream' "setup Activity must not write storage primitives directly"
fi

for production_manifest in "${ROOT_DIR}/app/src/main/AndroidManifest.xml" "${ROOT_DIR}/app/src/release/AndroidManifest.xml"; do
  [[ -f "${production_manifest}" ]] || continue
  reject_pattern "${production_manifest}" 'PROFILE_SCENARIO_SETUP|ProfileScenarioSetupActivity' "Profile test capability leaked into ${production_manifest#${ROOT_DIR}/}"
done

if [[ -d "${ROOT_DIR}/app/src/main" ]] && rg -n --glob '*.{kt,xml,json}' 'PROFILE_FIXTURE_TOKEN|ProfileScenarioSetupActivity|PROFILE_SCENARIO_SETUP' "${ROOT_DIR}/app/src/main" >/dev/null; then
  fail "Profile setup capability or fixture identity leaked into app/src/main"
fi
if [[ -d "${ROOT_DIR}/app/src/release" ]] && rg -n --glob '*.{kt,xml,json}' 'PROFILE_FIXTURE_TOKEN|ProfileScenarioSetupActivity|PROFILE_SCENARIO_SETUP' "${ROOT_DIR}/app/src/release" >/dev/null; then
  fail "Profile setup capability or fixture identity leaked into app/src/release"
fi

APP_UI_ROOTS=(
  "${ROOT_DIR}/app/src/main/kotlin"
  "${ROOT_DIR}/feature/login/src/main/kotlin"
)
if [[ -f "${CONFIG_FILE}" ]]; then
  while IFS= read -r required_tag; do
    [[ -n "${required_tag}" ]] || continue
    found="false"
    for search_root in "${APP_UI_ROOTS[@]}"; do
      [[ -d "${search_root}" ]] || continue
      if rg -Fq -- "${required_tag}" "${search_root}"; then
        found="true"
        break
      fi
    done
    [[ "${found}" == "true" ]] || fail "required Compose test tag is missing from production UI: ${required_tag}"
  done < <(python3 - "${CONFIG_FILE}" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
for scenario in config.get("scenarios", []):
    for tag in scenario.get("requiredTags", []):
        print(tag)
PY
  )
fi

if ! rg -q 'testTagsAsResourceId[[:space:]]*=[[:space:]]*true' "${ROOT_DIR}/app/src/main/kotlin"; then
  fail "application root must expose Compose test tags to UiAutomator"
fi
if ! rg -q 'ReportDrawn|ReportDrawnWhen' "${ROOT_DIR}/app/src/main/kotlin" "${ROOT_DIR}/feature/login/src/main/kotlin"; then
  fail "root destinations must use the Activity Compose fully-drawn API"
fi

if [[ "${#FAILURES[@]}" -gt 0 ]]; then
  echo "[baselineprofile] ${#FAILURES[@]} contract violation(s) detected." >&2
  exit 1
fi

echo "[baselineprofile][PASS] six deterministic scenarios, exact page assertions, Profile layering, symmetric benchmarks, fully-drawn reporting, and performance-only isolation are enforced."
