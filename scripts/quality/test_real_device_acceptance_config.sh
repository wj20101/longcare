#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_real_device_acceptance_config.py"
SOURCE_CONFIG="${ROOT_DIR}/scripts/quality/real_device_acceptance.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-config.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

python3 "${VERIFIER}" "${SOURCE_CONFIG}" --project-root "${ROOT_DIR}"

make_fixture() {
  local mutation="$1"
  local destination="$2"
  python3 - "${SOURCE_CONFIG}" "${destination}" "${mutation}" <<'PY'
import json
import sys
from pathlib import Path

source, destination, mutation = map(Path, sys.argv[1:])
data = json.loads(source.read_text(encoding="utf-8"))

if mutation.name == "missing-device-field":
    del data["deviceEligibility"]["minimumBatteryPercent"]
elif mutation.name == "wrong-api":
    data["deviceEligibility"]["requiredApiLevel"] = 37
elif mutation.name == "wrong-abi":
    data["deviceEligibility"]["requiredPrimaryAbi"] = "x86_64"
elif mutation.name == "duplicate-scenario":
    data["releaseSmoke"]["scenarios"][-1] = dict(data["releaseSmoke"]["scenarios"][0])
elif mutation.name == "missing-scenario":
    data["releaseSmoke"]["scenarios"] = data["releaseSmoke"]["scenarios"][:-1]
elif mutation.name == "unknown-condition":
    data["releaseSmoke"]["scenarios"][0]["prerequisites"] = ["plain-text-account"]
elif mutation.name == "missing-forbidden":
    data["forbiddenLogSignatures"].remove("UnsatisfiedLinkError")
elif mutation.name == "single-verdict":
    data["verdicts"] = data["verdicts"][:1]
elif mutation.name == "missing-redaction":
    del data["reportPolicy"]["serialPolicy"]
elif mutation.name == "budget-drift":
    data["benchmarkEvidence"]["maxMedianRegressionPercent"]["timeToFullDisplayMs"] = 9.0
elif mutation.name == "mock-success":
    data["releaseSmoke"]["scenarios"][7]["mockSuccessAllowed"] = True
elif mutation.name == "missing-target":
    data["releaseSmoke"]["scenarios"][4]["targetNode"] = ""
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
  if python3 "${VERIFIER}" "${fixture}" --project-root "${ROOT_DIR}" >"${output}" 2>&1; then
    echo "[real-device-acceptance-config-test][FAIL] ${mutation} unexpectedly passed" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output}"; then
    echo "[real-device-acceptance-config-test][FAIL] ${mutation} did not report ${expected}" >&2
    sed 's/^/[fixture-output] /' "${output}" >&2
    exit 1
  fi
}

assert_rejected "missing-device-field" "deviceEligibility missing required fields"
assert_rejected "wrong-api" "requiredApiLevel must match current_target_api=36"
assert_rejected "wrong-abi" "requiredPrimaryAbi must match Startup"
assert_rejected "duplicate-scenario" "duplicate ids"
assert_rejected "missing-scenario" "complete ten-scenario catalog"
assert_rejected "unknown-condition" "unknown external conditions"
assert_rejected "missing-forbidden" "forbiddenLogSignatures missing required signatures"
assert_rejected "single-verdict" "exactly the two independent verdicts"
assert_rejected "missing-redaction" "serialPolicy must be sha256-only"
assert_rejected "budget-drift" "drifted from Startup quality config"
assert_rejected "mock-success" "mockSuccessAllowed must be false"
assert_rejected "missing-target" "targetNode must be a non-empty string"

echo "[real-device-acceptance-config-test][PASS] valid contract accepted and twelve malformed contracts rejected."
