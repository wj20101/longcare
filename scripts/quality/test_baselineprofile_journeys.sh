#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_baselineprofile_journeys.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-profile-journeys.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

make_fixture() {
  local fixture="$1"
  mkdir -p \
    "${fixture}/scripts/quality" \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile" \
    "${fixture}/baselineprofile/src/main" \
    "${fixture}/app/src/profile/kotlin/com/ytone/longcare/performance" \
    "${fixture}/app/src/main/kotlin/com/ytone/longcare/di" \
    "${fixture}/app/src/main/kotlin/com/ytone/longcare/network/interceptor" \
    "${fixture}/app/src/main/kotlin" \
    "${fixture}/feature/login/src/main/kotlin"

  cp "${ROOT_DIR}/scripts/quality/startup_profile_quality.json" \
    "${ROOT_DIR}/scripts/quality/verify_startup_profile_quality_config.py" \
    "${fixture}/scripts/quality/"
  cp "${ROOT_DIR}"/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/*.kt \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/"
  cp "${ROOT_DIR}/baselineprofile/src/main/AndroidManifest.xml" \
    "${fixture}/baselineprofile/src/main/AndroidManifest.xml"
  cp "${ROOT_DIR}/app/build.gradle.kts" "${fixture}/app/build.gradle.kts"
  cp "${ROOT_DIR}/app/src/main/kotlin/com/ytone/longcare/di/AppFlavorInterceptorApplier.kt" \
    "${fixture}/app/src/main/kotlin/com/ytone/longcare/di/AppFlavorInterceptorApplier.kt"
  cp "${ROOT_DIR}/app/src/main/kotlin/com/ytone/longcare/network/interceptor/PerformanceOfflineInterceptor.kt" \
    "${fixture}/app/src/main/kotlin/com/ytone/longcare/network/interceptor/PerformanceOfflineInterceptor.kt"
  cp "${ROOT_DIR}/app/src/profile/AndroidManifest.xml" \
    "${fixture}/app/src/profile/AndroidManifest.xml"
  cp "${ROOT_DIR}/app/src/profile/kotlin/com/ytone/longcare/performance/ProfileScenarioSetupActivity.kt" \
    "${fixture}/app/src/profile/kotlin/com/ytone/longcare/performance/ProfileScenarioSetupActivity.kt"

  python3 - "${fixture}/scripts/quality/startup_profile_quality.json" \
    "${fixture}/app/src/main/kotlin/ProfileUiContract.kt" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
tags = sorted({tag for scenario in config["scenarios"] for tag in scenario["requiredTags"]})
content = "\n".join([
    "// Synthetic production UI contract used only by the shell fixture.",
    "// testTagsAsResourceId = true",
    "// ReportDrawn()",
    *[f'// {tag}' for tag in tags],
    "",
])
Path(sys.argv[2]).write_text(content, encoding="utf-8")
PY
}

replace_once() {
  local file="$1"
  local old="$2"
  local new="$3"
  python3 - "${file}" "${old}" "${new}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
old = sys.argv[2]
new = sys.argv[3]
content = path.read_text(encoding="utf-8")
if old not in content:
    raise SystemExit(f"fixture mutation source not found in {path}: {old}")
path.write_text(content.replace(old, new, 1), encoding="utf-8")
PY
}

append_line() {
  local file="$1"
  local line="$2"
  python3 - "${file}" "${line}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
with path.open("a", encoding="utf-8") as stream:
    stream.write(sys.argv[2] + "\n")
PY
}

expect_failure() {
  local name="$1"
  local expected="$2"
  local fixture="${FIXTURE_ROOT}/${name}"
  shift 2
  make_fixture "${fixture}"
  "$@" "${fixture}"

  local output_file="${FIXTURE_ROOT}/${name}.log"
  if bash "${VERIFIER}" --project-root "${fixture}" >"${output_file}" 2>&1; then
    echo "[baselineprofile-test][FAIL] ${name} fixture unexpectedly passed" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output_file}"; then
    echo "[baselineprofile-test][FAIL] ${name} did not report: ${expected}" >&2
    sed -n '1,220p' "${output_file}" >&2
    exit 1
  fi
  echo "[baselineprofile-test][PASS] ${name} rejected"
}

mutate_missing_setup() {
  local fixture="$1"
  replace_once \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt" \
    '        scenarioDriver.prepare(scenario)' \
    '        // state preparation deliberately removed'
}

mutate_package_root() {
  local fixture="$1"
  append_line \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/ProfileScenarioDriver.kt" \
    '// forbidden readiness: By.pkg(targetAppId)'
}

mutate_blind_gesture() {
  local fixture="$1"
  append_line \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/ProfileScenarioDriver.kt" \
    '// forbidden gesture: device.swipe('
}

mutate_startup_pollution() {
  local fixture="$1"
  replace_once \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt" \
    'includeInStartupProfile = false' \
    'includeInStartupProfile = true'
}

mutate_benchmark_divergence() {
  local fixture="$1"
  replace_once \
    "${fixture}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/StartupBenchmarks.kt" \
    'benchmark(ProfileScenario.FIRST_RUN_PRIVACY, CompilationMode.None())' \
    'benchmarkWithoutProfile(ProfileScenario.FIRST_RUN_PRIVACY, CompilationMode.None())'
}

mutate_production_leak() {
  local fixture="$1"
  append_line "${fixture}/app/src/main/kotlin/ProfileUiContract.kt" \
    '// PROFILE_FIXTURE_TOKEN leaked into production'
}

mutate_production_offline_default() {
  local fixture="$1"
  replace_once \
    "${fixture}/app/build.gradle.kts" \
    'buildConfigField("boolean", "PROFILE_OFFLINE_MODE", "false")' \
    'buildConfigField("boolean", "PROFILE_OFFLINE_MODE", "true")'
}

VALID_FIXTURE="${FIXTURE_ROOT}/valid"
make_fixture "${VALID_FIXTURE}"
bash "${VERIFIER}" --project-root "${VALID_FIXTURE}"

expect_failure "missing-state-preparation" "scenario preparation must be explicit" mutate_missing_setup
expect_failure "package-root-only" "package-root-only readiness is forbidden" mutate_package_root
expect_failure "blind-gesture" "blind coordinate swipes are forbidden" mutate_blind_gesture
expect_failure "startup-classification-pollution" "exactly four Startup collections" mutate_startup_pollution
expect_failure "benchmark-mode-divergence" "mode-specific benchmark helper divergence is forbidden" mutate_benchmark_divergence
expect_failure "production-capability-leak" "fixture identity leaked into app/src/main" mutate_production_leak
expect_failure "production-offline-default" "production variants must default performance offline mode to false" mutate_production_offline_default

echo "[baselineprofile-test][PASS] valid fixture accepted and seven contract regressions rejected."
