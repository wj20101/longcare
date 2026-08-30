#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_ci_workflow_quality.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-ci-workflow-quality.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

mkdir -p "${FIXTURE_ROOT}/valid" "${FIXTURE_ROOT}/valid/gradle/wrapper"
cp -R "${ROOT_DIR}/.github" "${FIXTURE_ROOT}/valid/.github"
cp -R "${ROOT_DIR}/scripts" "${FIXTURE_ROOT}/valid/scripts"
cp "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.properties" \
  "${FIXTURE_ROOT}/valid/gradle/wrapper/gradle-wrapper.properties"

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

echo "[ci-workflow-quality-test][PASS] valid workflow and unconditional-emulator negative fixture passed."
