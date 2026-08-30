#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_startup_benchmark_results.py"
NORMALIZER="${ROOT_DIR}/scripts/quality/normalize_startup_benchmark_results.py"
CONFIG="${ROOT_DIR}/scripts/quality/startup_profile_quality.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-startup-benchmark.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

VALID_REPORT="${FIXTURE_ROOT}/valid.json"
RAW_REPORT="${FIXTURE_ROOT}/androidx-benchmarkData.json"
NORMALIZED_REPORT="${FIXTURE_ROOT}/normalized.json"
python3 - "${CONFIG}" "${VALID_REPORT}" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
results = []
for scenario in config["scenarios"]:
    if scenario["classification"] != "startup":
        continue
    for mode in config["compilationModes"]:
        results.append({
            "scenario": scenario["id"],
            "mode": mode,
            "profileStatus": "disabled" if mode == "none" else "required-applied",
            "setupState": scenario["setupState"],
            "startupMode": config["benchmarkPolicy"]["startupMode"],
            "iterations": config["benchmarkPolicy"]["iterationsPerMode"],
            "metadata": {
                "device": "fixture-arm64-device",
                "apiLevel": 36,
                "abi": "arm64-v8a",
                "buildSha": "0123456789abcdef0123456789abcdef01234567",
            },
            "metrics": {
                "timeToInitialDisplayMs": [100 + value for value in range(10)],
                "timeToFullDisplayMs": [150 + value for value in range(10)],
            },
        })
Path(sys.argv[2]).write_text(
    json.dumps({"schemaVersion": 1, "results": results}, indent=2) + "\n",
    encoding="utf-8",
)
PY

python3 "${VERIFIER}" --config "${CONFIG}" "${VALID_REPORT}"

python3 - "${CONFIG}" "${RAW_REPORT}" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
method_names = {
    "first_run_privacy": ("firstRunPrivacyNone", "firstRunPrivacyProfile"),
    "logged_out": ("loggedOutNone", "loggedOutProfile"),
    "care_home": ("careHomeNone", "careHomeProfile"),
    "sales_home": ("salesHomeNone", "salesHomeProfile"),
}
benchmarks = []
for scenario in config["scenarios"]:
    if scenario["classification"] != "startup":
        continue
    for method_name in method_names[scenario["id"]]:
        benchmarks.append({
            "name": method_name,
            "repeatIterations": 10,
            "metrics": {
                "timeToInitialDisplayMs": {"runs": [100 + value for value in range(10)]},
                "timeToFullDisplayMs": {"runs": [150 + value for value in range(10)]},
            },
        })
report = {
    "context": {
        "build": {
            "device": "fixture-device",
            "model": "fixture-model",
            "version": {"sdk": 33},
        }
    },
    "benchmarks": benchmarks,
}
Path(sys.argv[2]).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
PY

python3 "${NORMALIZER}" \
  --config "${CONFIG}" \
  --build-sha "0123456789abcdef0123456789abcdef01234567" \
  --abi "arm64-v8a" \
  --device-type "emulator" \
  "${RAW_REPORT}" \
  "${NORMALIZED_REPORT}"
python3 "${VERIFIER}" --config "${CONFIG}" "${NORMALIZED_REPORT}"
grep -Fq '"evidenceScope": "journey-and-report-format-only"' "${NORMALIZED_REPORT}"
grep -Fq '"performanceBenefit": "unverified"' "${NORMALIZED_REPORT}"

expect_failure() {
  local name="$1"
  local expected="$2"
  local mutation="$3"
  local fixture="${FIXTURE_ROOT}/${name}.json"
  cp "${VALID_REPORT}" "${fixture}"
  python3 - "${fixture}" "${mutation}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
mutation = sys.argv[2]
report = json.loads(path.read_text(encoding="utf-8"))
if mutation == "missing-ttfd":
    del report["results"][0]["metrics"]["timeToFullDisplayMs"]
elif mutation == "missing-mode":
    report["results"] = report["results"][:-1]
elif mutation == "asymmetric-profile-status":
    profile = next(result for result in report["results"] if result["mode"] == "baseline-profile-required")
    profile["profileStatus"] = "disabled"
elif mutation == "cross-device":
    report["results"][-1]["metadata"]["device"] = "different-device"
else:
    raise SystemExit(f"unknown mutation: {mutation}")
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
PY

  local output="${FIXTURE_ROOT}/${name}.log"
  if python3 "${VERIFIER}" --config "${CONFIG}" "${fixture}" >"${output}" 2>&1; then
    echo "[startup-benchmark-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "${expected}" "${output}" || {
    echo "[startup-benchmark-test][FAIL] ${name} did not report: ${expected}" >&2
    sed -n '1,160p' "${output}" >&2
    exit 1
  }
  echo "[startup-benchmark-test][PASS] ${name} rejected"
}

expect_failure "missing-ttfd" "missing metric timeToFullDisplayMs" "missing-ttfd"
expect_failure "missing-mode" "scenario/mode pairs are incomplete" "missing-mode"
expect_failure "asymmetric-profile-status" "profileStatus must be required-applied" "asymmetric-profile-status"
expect_failure "cross-device" "cross-device or cross-build benchmark comparison is forbidden" "cross-device"

RAW_MISSING_TTFD="${FIXTURE_ROOT}/androidx-missing-ttfd.json"
cp "${RAW_REPORT}" "${RAW_MISSING_TTFD}"
python3 - "${RAW_MISSING_TTFD}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
report = json.loads(path.read_text(encoding="utf-8"))
benchmark = next(item for item in report["benchmarks"] if item["name"] == "salesHomeProfile")
del benchmark["metrics"]["timeToFullDisplayMs"]
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
PY
RAW_FAILURE_OUTPUT="${FIXTURE_ROOT}/androidx-missing-ttfd.log"
if python3 "${NORMALIZER}" \
  --config "${CONFIG}" \
  --build-sha "0123456789abcdef0123456789abcdef01234567" \
  --abi "arm64-v8a" \
  --device-type "emulator" \
  "${RAW_MISSING_TTFD}" \
  "${FIXTURE_ROOT}/should-not-exist.json" >"${RAW_FAILURE_OUTPUT}" 2>&1; then
  echo "[startup-benchmark-test][FAIL] raw missing TTFD unexpectedly passed" >&2
  exit 1
fi
grep -Fq -- \
  "sales_home/baseline-profile-required is missing metric timeToFullDisplayMs" \
  "${RAW_FAILURE_OUTPUT}" || {
  echo "[startup-benchmark-test][FAIL] raw missing TTFD did not identify sales_home/Profile" >&2
  sed -n '1,160p' "${RAW_FAILURE_OUTPUT}" >&2
  exit 1
}
echo "[startup-benchmark-test][PASS] raw missing TTFD rejected with scenario and mode"

echo "[startup-benchmark-test][PASS] normalized AndroidX report accepted and five malformed reports rejected."
