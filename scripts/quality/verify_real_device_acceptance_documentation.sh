#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REAL_DEVICE_ACCEPTANCE_DOC_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SYSTEM_DOC="${ROOT_DIR}/docs/architecture/system-overview.md"
CI_DOC="${ROOT_DIR}/docs/architecture/ci-quality-gates.md"
STACK_DOC="${ROOT_DIR}/docs/architecture/tech-stack.md"
ROADMAP_DOC="${ROOT_DIR}/docs/architecture/roadmap-and-open-gaps.md"

failures=0
require_text() {
  local file="$1"
  local text="$2"
  local label="$3"
  if [[ ! -f "${file}" ]] || ! grep -Fq -- "${text}" "${file}"; then
    echo "[real-device-docs][FAIL] ${label}" >&2
    failures=$((failures + 1))
  fi
}

for file in "${SYSTEM_DOC}" "${CI_DOC}" "${STACK_DOC}" "${ROADMAP_DOC}"; do
  require_text "${file}" "real_device_acceptance.json" "$(basename "${file}") names the machine-readable acceptance policy"
  require_text "${file}" "API 36" "$(basename "${file}") keeps API 36 physical acceptance boundary"
  require_text "${file}" "r8RuntimeAcceptance" "$(basename "${file}") documents the R8 verdict"
  require_text "${file}" "startupProfileBenefit" "$(basename "${file}") documents the Profile verdict"
done
require_text "${CI_DOC}" "run_real_device_acceptance.sh" "CI documentation names the explicit real-device entry"
require_text "${CI_DOC}" "API 28" "CI documentation rejects API 28 for full evidence"
require_text "${CI_DOC}" "模拟器" "CI documentation rejects emulator performance/business substitution"
require_text "${CI_DOC}" "没有 overall verdict" "CI documentation forbids a single overall verdict"
require_text "${ROADMAP_DOC}" '都必须继续保持 `unverified`' "roadmap keeps both external executions open"
require_text "${SYSTEM_DOC}" "production readiness" "system overview keeps production readiness independent"

if [[ "${failures}" -ne 0 ]]; then
  exit 1
fi
echo "[real-device-docs][PASS] four architecture documents agree on API 36 physical evidence, independent verdicts, external blockers, and non-acceptance API 28/emulator boundaries."
