#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_startup_profile_quality_config.py"
SOURCE_CONFIG="${ROOT_DIR}/scripts/quality/startup_profile_quality.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-startup-profile-config.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

python3 "${VERIFIER}" "${SOURCE_CONFIG}"

make_fixture() {
  local mutation="$1"
  local destination="$2"
  python3 - "${SOURCE_CONFIG}" "${destination}" "${mutation}" <<'PY'
import json
import sys
from pathlib import Path

source, destination, mutation = map(Path, sys.argv[1:])
data = json.loads(source.read_text(encoding="utf-8"))

if mutation.name == "missing-scenario":
    data["scenarios"] = data["scenarios"][:-1]
elif mutation.name == "duplicate-scenario":
    data["scenarios"][-1] = dict(data["scenarios"][0])
elif mutation.name == "unknown-classification":
    data["scenarios"][0]["classification"] = "everything"
elif mutation.name == "incomplete-budget":
    del data["benefitAcceptance"]["maxMedianRegressionPercent"]["timeToFullDisplayMs"]
else:
    raise SystemExit(f"unknown mutation: {mutation.name}")

destination.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
}

assert_rejected() {
  local mutation="$1"
  local expected="$2"
  local fixture="${FIXTURE_ROOT}/${mutation}.json"
  local output="${FIXTURE_ROOT}/${mutation}.out"
  make_fixture "${mutation}" "${fixture}"
  if python3 "${VERIFIER}" "${fixture}" >"${output}" 2>&1; then
    echo "[startup-profile-config-test][FAIL] ${mutation} fixture unexpectedly passed" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output}"; then
    echo "[startup-profile-config-test][FAIL] ${mutation} did not report ${expected}" >&2
    cat "${output}" >&2
    exit 1
  fi
}

assert_rejected "missing-scenario" "complete six-scenario catalog"
assert_rejected "duplicate-scenario" "duplicate ids"
assert_rejected "unknown-classification" "classification must be startup"
assert_rejected "incomplete-budget" "complete TTID/TTFD budgets"

echo "[startup-profile-config-test][PASS] valid policy accepted and four malformed policies rejected."
