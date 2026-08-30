#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_text_profiles.py"
CONFIG="${ROOT_DIR}/scripts/quality/startup_profile_quality.json"
GENERATOR="${ROOT_DIR}/baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-text-profile.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_profile() {
  local path="$1"
  shift
  python3 - "${path}" "$@" <<'PY'
import sys
from pathlib import Path

Path(sys.argv[1]).write_text("\n".join(sys.argv[2:]) + "\n", encoding="utf-8")
PY
}

verify() {
  local baseline="$1"
  local startup="$2"
  python3 "${VERIFIER}" \
    --baseline "${baseline}" \
    --startup "${startup}" \
    --generator "${GENERATOR}" \
    --config "${CONFIG}"
}

VALID_BASELINE="${FIXTURE_ROOT}/valid-baseline.txt"
VALID_STARTUP="${FIXTURE_ROOT}/valid-startup.txt"
write_profile "${VALID_BASELINE}" '# ignored comment' 'HSPLcom/ytone/Startup;->run()V' 'Lcom/ytone/BaselineOnly;'
write_profile "${VALID_STARTUP}" '' '# another comment' 'SPLcom/ytone/Startup;->run()V'
VALID_REPORT="${FIXTURE_ROOT}/valid-report.json"
python3 "${VERIFIER}" \
  --baseline "${VALID_BASELINE}" \
  --startup "${VALID_STARTUP}" \
  --generator "${GENERATOR}" \
  --config "${CONFIG}" \
  --report "${VALID_REPORT}"
python3 - "${VALID_REPORT}" <<'PY'
import json
import sys
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert report["schemaVersion"] == 1
assert report["status"] == "verified"
assert report["startupIsBaselineSubset"] is True
assert report["baselineIsStrictSuperset"] is True
assert report["baselineOnlyRuleCount"] == 1
assert report["generatorScenarioCount"] == 6
assert report["baseline"]["normalizedRuleCount"] == 2
assert report["startup"]["normalizedRuleCount"] == 1
assert len(report["baseline"]["sha256"]) == 64
assert len(report["startup"]["sha256"]) == 64
PY

expect_failure() {
  local name="$1"
  local expected="$2"
  local baseline="$3"
  local startup="$4"
  local output="${FIXTURE_ROOT}/${name}.log"
  if verify "${baseline}" "${startup}" >"${output}" 2>&1; then
    echo "[text-profile-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "${expected}" "${output}" || {
    echo "[text-profile-test][FAIL] ${name} did not report: ${expected}" >&2
    sed -n '1,120p' "${output}" >&2
    exit 1
  }
  echo "[text-profile-test][PASS] ${name} rejected"
}

SAME_BASELINE="${FIXTURE_ROOT}/same-baseline.txt"
SAME_STARTUP="${FIXTURE_ROOT}/same-startup.txt"
write_profile "${SAME_BASELINE}" 'Lcom/ytone/Same;'
write_profile "${SAME_STARTUP}" 'Lcom/ytone/Same;'
expect_failure "identical-files" "strict superset" "${SAME_BASELINE}" "${SAME_STARTUP}"

REVERSE_BASELINE="${FIXTURE_ROOT}/reverse-baseline.txt"
REVERSE_STARTUP="${FIXTURE_ROOT}/reverse-startup.txt"
write_profile "${REVERSE_BASELINE}" 'Lcom/ytone/Base;'
write_profile "${REVERSE_STARTUP}" 'Lcom/ytone/Base;' 'Lcom/ytone/StartupOnly;'
expect_failure "reverse-subset" "must be a subset" "${REVERSE_BASELINE}" "${REVERSE_STARTUP}"

EMPTY_BASELINE="${FIXTURE_ROOT}/empty-baseline.txt"
EMPTY_STARTUP="${FIXTURE_ROOT}/empty-startup.txt"
write_profile "${EMPTY_BASELINE}" '# comments are not rules'
write_profile "${EMPTY_STARTUP}" 'Lcom/ytone/Startup;'
expect_failure "empty-profile" "must contain at least one normalized rule" "${EMPTY_BASELINE}" "${EMPTY_STARTUP}"

echo "[text-profile-test][PASS] strict-superset fixture accepted and identical, reversed, and empty fixtures rejected."
