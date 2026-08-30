#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_ci_workflow_quality.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-ci-workflow-quality.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

create_fixture() {
  local name="$1"
  local root="${FIXTURE_ROOT}/${name}"
  mkdir -p "${root}" "${root}/gradle/wrapper"
  cp -R "${ROOT_DIR}/.github" "${root}/.github"
  cp -R "${ROOT_DIR}/scripts" "${root}/scripts"
  cp "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.properties" \
    "${root}/gradle/wrapper/gradle-wrapper.properties"
}

create_fixture "valid"

CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/valid" \
  bash "${VERIFY_SCRIPT}" >/dev/null

workflow="${FIXTURE_ROOT}/valid/.github/workflows/android-ci.yml"
awk '
  /^    if: needs\.detect-affected\.outputs\.run_instrumentation == '\''true'\'' \|\| needs\.detect-affected\.outputs\.run_login_feature_instrumentation == '\''true'\''$/ {
    print "    if: always()"
    next
  }
  { print }
' "${workflow}" > "${workflow}.tmp"
mv "${workflow}.tmp" "${workflow}"

if CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/valid" \
  bash "${VERIFY_SCRIPT}" >"${FIXTURE_ROOT}/unconditional.log" 2>&1; then
  echo "[ci-workflow-quality-test][FAIL] unconditional current-target emulator fixture unexpectedly passed." >&2
  exit 1
fi

if ! grep -Fq "current-target emulator job remains affected-gated" "${FIXTURE_ROOT}/unconditional.log"; then
  echo "[ci-workflow-quality-test][FAIL] unconditional fixture did not report the affected-gating violation." >&2
  sed 's/^/[fixture-output] /' "${FIXTURE_ROOT}/unconditional.log" >&2
  exit 1
fi

create_fixture "warning-only-profile"
python3 - "${FIXTURE_ROOT}/warning-only-profile/.github/workflows/android-release.yml" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
start = next(i for i, line in enumerate(lines) if line == "      - name: Run release-required text Profile verification")
end = next(i for i in range(start + 1, len(lines)) if lines[i].startswith("      - name: "))
replacement = [
    "      - name: Run release-required baseline profile source check",
    "        run: |",
    "          echo \"::warning::No committed baseline profile files found in app/src. Release will continue.\"",
]
path.write_text("\n".join(lines[:start] + replacement + lines[end:]) + "\n", encoding="utf-8")
PY

if CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/warning-only-profile" \
  bash "${VERIFY_SCRIPT}" >"${FIXTURE_ROOT}/warning-only-profile.log" 2>&1; then
  echo "[ci-workflow-quality-test][FAIL] warning-only Profile source fixture unexpectedly passed." >&2
  exit 1
fi

if ! grep -Fq "android-release requires strict committed text Profiles" "${FIXTURE_ROOT}/warning-only-profile.log"; then
  echo "[ci-workflow-quality-test][FAIL] warning-only fixture did not report missing strict text verification." >&2
  sed 's/^/[fixture-output] /' "${FIXTURE_ROOT}/warning-only-profile.log" >&2
  exit 1
fi

create_fixture "missing-artifact-profile"
python3 - "${FIXTURE_ROOT}/missing-artifact-profile/.github/workflows/android-release.yml" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
start = next(i for i, line in enumerate(lines) if line == "      - name: Verify explicit release Profile artifacts")
end = next(i for i in range(start + 1, len(lines)) if lines[i].startswith("      - name: "))
path.write_text("\n".join(lines[:start] + lines[end:]) + "\n", encoding="utf-8")
PY

if CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/missing-artifact-profile" \
  bash "${VERIFY_SCRIPT}" >"${FIXTURE_ROOT}/missing-artifact-profile.log" 2>&1; then
  echo "[ci-workflow-quality-test][FAIL] missing explicit artifact verifier fixture unexpectedly passed." >&2
  exit 1
fi

if ! grep -Fq "android-release verifies the newly built minified artifacts" "${FIXTURE_ROOT}/missing-artifact-profile.log"; then
  echo "[ci-workflow-quality-test][FAIL] missing artifact fixture did not report the missing explicit verifier." >&2
  sed 's/^/[fixture-output] /' "${FIXTURE_ROOT}/missing-artifact-profile.log" >&2
  exit 1
fi

create_fixture "missing-project-r8"
python3 - "${FIXTURE_ROOT}/missing-project-r8/.github/workflows/android-ci.yml" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
lines = [
    line
    for line in lines
    if "python3 scripts/quality/verify_project_r8_rules.py --project-root ." not in line
]
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

if CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/missing-project-r8" \
  bash "${VERIFY_SCRIPT}" >"${FIXTURE_ROOT}/missing-project-r8.log" 2>&1; then
  echo "[ci-workflow-quality-test][FAIL] missing project R8 verifier fixture unexpectedly passed." >&2
  exit 1
fi

if ! grep -Fq "android-ci ci-required step runs project R8 verifier" "${FIXTURE_ROOT}/missing-project-r8.log"; then
  echo "[ci-workflow-quality-test][FAIL] missing project R8 fixture did not report the missing verifier." >&2
  sed 's/^/[fixture-output] /' "${FIXTURE_ROOT}/missing-project-r8.log" >&2
  exit 1
fi

create_fixture "missing-affected-module-self-test"
governance_script="${FIXTURE_ROOT}/missing-affected-module-self-test/scripts/quality/test_android_build_governance.sh"
awk '!/test_affected_modules\.sh/' "${governance_script}" > "${governance_script}.tmp"
mv "${governance_script}.tmp" "${governance_script}"

if CI_WORKFLOW_QUALITY_ROOT_DIR="${FIXTURE_ROOT}/missing-affected-module-self-test" \
  bash "${VERIFY_SCRIPT}" >"${FIXTURE_ROOT}/missing-affected-module-self-test.log" 2>&1; then
  echo "[ci-workflow-quality-test][FAIL] missing affected-module self-test fixture unexpectedly passed." >&2
  exit 1
fi

if ! grep -Fq "android build governance runs affected-module fixtures once" "${FIXTURE_ROOT}/missing-affected-module-self-test.log"; then
  echo "[ci-workflow-quality-test][FAIL] missing affected-module fixture did not report the single-entry violation." >&2
  sed 's/^/[fixture-output] /' "${FIXTURE_ROOT}/missing-affected-module-self-test.log" >&2
  exit 1
fi

echo "[ci-workflow-quality-test][PASS] valid workflow plus emulator, Profile, artifact, project R8, and affected-module negative fixtures passed."
