#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPARATOR="${ROOT_DIR}/scripts/quality/compare_startup_benchmark_rounds.py"
STARTUP_CONFIG="${ROOT_DIR}/scripts/quality/startup_profile_quality.json"
ACCEPTANCE_CONFIG="${ROOT_DIR}/scripts/quality/real_device_acceptance.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-startup-round-comparator.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

python3 - "${STARTUP_CONFIG}" "${FIXTURE_ROOT}/round-1.json" "${FIXTURE_ROOT}/round-2.json" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
device_hash = hashlib.sha256(b"fixture-qualified-device").hexdigest()
for round_index, output in enumerate(map(Path, sys.argv[2:]), start=1):
    results = []
    for scenario_index, scenario in enumerate(
        item for item in config["scenarios"] if item["classification"] == "startup"
    ):
        for mode in config["compilationModes"]:
            none_ttid = 100.0 + scenario_index * 10 + round_index
            none_ttfd = 160.0 + scenario_index * 10 + round_index
            factor = 0.97 if mode == "baseline-profile-required" else 1.0
            results.append({
                "scenario": scenario["id"],
                "mode": mode,
                "profileStatus": "disabled" if mode == "none" else "required-applied",
                "setupState": scenario["setupState"],
                "startupMode": "cold",
                "iterations": 10,
                "metadata": {
                    "device": "Fixture Phone",
                    "deviceIdHash": device_hash,
                    "apiLevel": 36,
                    "abi": "arm64-v8a",
                    "cpuCores": 8,
                    "buildSha": "a" * 40,
                },
                "metrics": {
                    "timeToInitialDisplayMs": [(none_ttid + i / 10) * factor for i in range(10)],
                    "timeToFullDisplayMs": [(none_ttfd + i / 10) * factor for i in range(10)],
                },
            })
    report = {
        "schemaVersion": 1,
        "evidenceScope": "physical-raw-measurement",
        "performanceBenefit": "unverified",
        "deviceType": "physical",
        "results": results,
    }
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
PY

compare() {
  local round_one="$1"
  local round_two="$2"
  local output="$3"
  python3 "${COMPARATOR}" \
    --startup-config "${STARTUP_CONFIG}" \
    --acceptance-config "${ACCEPTANCE_CONFIG}" \
    --round-one "${round_one}" \
    --round-two "${round_two}" \
    --output "${output}"
}

compare "${FIXTURE_ROOT}/round-1.json" "${FIXTURE_ROOT}/round-2.json" "${FIXTURE_ROOT}/verified.json"
grep -Fq '"status": "verified"' "${FIXTURE_ROOT}/verified.json"
grep -Fq '"consistentlyImproved": true' "${FIXTURE_ROOT}/verified.json"

make_mutation() {
  local name="$1"
  local mutation="$2"
  cp "${FIXTURE_ROOT}/round-1.json" "${FIXTURE_ROOT}/${name}-round-1.json"
  cp "${FIXTURE_ROOT}/round-2.json" "${FIXTURE_ROOT}/${name}-round-2.json"
  python3 - "${FIXTURE_ROOT}/${name}-round-1.json" "${FIXTURE_ROOT}/${name}-round-2.json" "${mutation}" <<'PY'
import json
import sys
from pathlib import Path

round_one_path, round_two_path = map(Path, sys.argv[1:3])
mutation = sys.argv[3]
round_one = json.loads(round_one_path.read_text(encoding="utf-8"))
round_two = json.loads(round_two_path.read_text(encoding="utf-8"))

if mutation == "emulator":
    round_two["deviceType"] = "emulator"
    round_two["evidenceScope"] = "journey-and-report-format-only"
elif mutation == "api28":
    for result in round_two["results"]:
        result["metadata"]["apiLevel"] = 28
elif mutation == "missing-ttfd":
    del round_two["results"][0]["metrics"]["timeToFullDisplayMs"]
elif mutation == "missing-mode":
    round_two["results"].pop()
elif mutation == "missing-sample":
    round_two["results"][0]["metrics"]["timeToInitialDisplayMs"].pop()
elif mutation == "invalid-sample":
    round_two["results"][0]["metrics"]["timeToInitialDisplayMs"][0] = 0
elif mutation == "profile-downgrade":
    profile = next(item for item in round_two["results"] if item["mode"] == "baseline-profile-required")
    profile["profileStatus"] = "disabled"
elif mutation == "cross-device":
    for result in round_two["results"]:
        result["metadata"]["deviceIdHash"] = "0" * 64
elif mutation == "cross-build":
    for result in round_two["results"]:
        result["metadata"]["buildSha"] = "b" * 40
elif mutation == "regression":
    profile = next(item for item in round_two["results"] if item["mode"] == "baseline-profile-required")
    none = next(item for item in round_two["results"] if item["scenario"] == profile["scenario"] and item["mode"] == "none")
    profile["metrics"]["timeToInitialDisplayMs"] = [value * 1.10 for value in none["metrics"]["timeToInitialDisplayMs"]]
elif mutation in {"inconsistent", "zero-improvement"}:
    for report_index, report in enumerate((round_one, round_two)):
        pairs = {}
        for item in report["results"]:
            pairs[(item["scenario"], item["mode"])] = item
        for scenario_index, scenario in enumerate(sorted({item["scenario"] for item in report["results"]})):
            none = pairs[(scenario, "none")]
            profile = pairs[(scenario, "baseline-profile-required")]
            for metric_index, metric in enumerate(("timeToInitialDisplayMs", "timeToFullDisplayMs")):
                improve = mutation == "inconsistent" and (scenario_index + metric_index + report_index) % 2 == 0
                factor = 0.98 if improve else 1.0
                profile["metrics"][metric] = [value * factor for value in none["metrics"][metric]]
else:
    raise SystemExit(f"unknown mutation: {mutation}")

round_one_path.write_text(json.dumps(round_one, indent=2) + "\n", encoding="utf-8")
round_two_path.write_text(json.dumps(round_two, indent=2) + "\n", encoding="utf-8")
PY
}

expect_failure() {
  local name="$1"
  local mutation="$2"
  local expected="$3"
  make_mutation "${name}" "${mutation}"
  local output="${FIXTURE_ROOT}/${name}-comparison.json"
  local log="${FIXTURE_ROOT}/${name}.log"
  if compare "${FIXTURE_ROOT}/${name}-round-1.json" "${FIXTURE_ROOT}/${name}-round-2.json" "${output}" >"${log}" 2>&1; then
    echo "[startup-round-comparator-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "${expected}" "${log}" "${output}" || {
    echo "[startup-round-comparator-test][FAIL] ${name} did not report ${expected}" >&2
    sed 's/^/[fixture-output] /' "${log}" >&2
    exit 1
  }
  grep -Fq '"status": "unverified"' "${output}"
}

expect_failure "emulator" "emulator" "deviceType must be physical"
expect_failure "api28" "api28" "TTFD is not acceptable on API 29 or earlier"
expect_failure "missing-ttfd" "missing-ttfd" "missing metric timeToFullDisplayMs"
expect_failure "missing-mode" "missing-mode" "scenario/mode pairs are incomplete"
expect_failure "missing-sample" "missing-sample" "must contain 10 samples"
expect_failure "invalid-sample" "invalid-sample" "samples must be finite positive numbers"
expect_failure "profile-downgrade" "profile-downgrade" "profileStatus must be required-applied"
expect_failure "cross-device" "cross-device" "identity mismatch: ['deviceIdHash']"
expect_failure "cross-build" "cross-build" "identity mismatch: ['buildSha']"
expect_failure "regression" "regression" "median regression"
expect_failure "inconsistent" "inconsistent" "improvement is not consistent across both rounds"
expect_failure "zero-improvement" "zero-improvement" "improvement is not consistent across both rounds"

if python3 "${COMPARATOR}" --startup-config "${STARTUP_CONFIG}" --acceptance-config "${ACCEPTANCE_CONFIG}" \
  --round-one "${FIXTURE_ROOT}/round-1.json" --output "${FIXTURE_ROOT}/missing-round.json" >"${FIXTURE_ROOT}/missing-round.log" 2>&1; then
  echo "[startup-round-comparator-test][FAIL] missing round unexpectedly passed" >&2
  exit 1
fi
grep -Fq "round 2: normalized report is missing" "${FIXTURE_ROOT}/missing-round.json"

echo "[startup-round-comparator-test][PASS] verified pair accepted and twelve physical-evidence regressions plus missing round rejected."
