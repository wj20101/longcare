#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="${ROOT_DIR}/scripts/quality/run_physical_startup_benchmarks.py"
MANIFEST_TOOL="${ROOT_DIR}/scripts/quality/real_device_acceptance_manifest.py"
COMPARATOR="${ROOT_DIR}/scripts/quality/compare_startup_benchmark_rounds.py"
CONFIG="${ROOT_DIR}/scripts/quality/real_device_acceptance.json"
STARTUP_CONFIG="${ROOT_DIR}/scripts/quality/startup_profile_quality.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-physical-startup-runner.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT
PROJECT="${FIXTURE_ROOT}/project"
SERIAL="fixture-qualified-device"

python3 - "${PROJECT}" "${CONFIG}" "${STARTUP_CONFIG}" "${SERIAL}" <<'PY'
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
acceptance = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
startup = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
serial = sys.argv[4]
execution_id = "benchmark-fixture"
execution = root / "build/reports/real-device-acceptance" / execution_id
artifacts_root = root / "artifacts"
artifacts_root.mkdir(parents=True)
execution.mkdir(parents=True)
role_specs = {
    "acceptanceApk": ("acceptance.apk", "acceptanceRelease"),
    "acceptanceAab": ("acceptance.aab", "acceptanceRelease"),
    "mapping": ("mapping.txt", "acceptanceRelease"),
    "benchmarkTargetApk": ("benchmark-target.apk", "benchmarkRelease"),
    "benchmarkTestApk": ("benchmark-test.apk", "benchmarkRelease"),
    "baselineProfile": ("baseline-prof.txt", "sharedProfileInput"),
    "startupProfile": ("startup-prof.txt", "sharedProfileInput"),
}
artifacts = {}
for role, (name, variant) in role_specs.items():
    path = artifacts_root / name
    path.write_text(f"fixture {role}\n", encoding="utf-8")
    artifacts[role] = {
        "path": path.relative_to(root).as_posix(),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "sizeBytes": path.stat().st_size,
        "variant": variant,
        "packageName": "com.ytone.longcare.baselineprofile" if role == "benchmarkTestApk" else "com.ytone.longcare",
        "versionName": "1.2.3-fixture",
        "buildSha": "a" * 40,
    }
device_hash = hashlib.sha256(serial.encode()).hexdigest()
device = {
    "deviceIdHash": device_hash,
    "deviceType": "physical",
    "apiLevel": 36,
    "primaryAbi": "arm64-v8a",
    "cpuCores": 8,
    "fingerprint": "fixture",
    "model": "Fixture Phone",
    "battery": {"levelPercent": 90, "statusCode": 3, "powered": False},
    "thermalStatus": 0,
}
manifest = {
    "schemaVersion": 1,
    "executionId": execution_id,
    "createdAt": datetime.now(timezone.utc).isoformat(),
    "project": {
        "gitSha": "a" * 40,
        "workingTreeState": "dirty",
        "workingTreeDigest": "b" * 64,
        "packageName": "com.ytone.longcare",
        "benchmarkTestPackageName": "com.ytone.longcare.baselineprofile",
        "versionName": "1.2.3-fixture",
        "acceptanceVariant": "acceptanceRelease",
        "benchmarkVariant": "benchmarkRelease",
    },
    "device": device,
    "artifacts": artifacts,
    "artifactVerification": {
        "schemaVersion": 1,
        "status": "passed",
        "verifier": "release-profile-artifacts-v1",
        "acceptanceApkSha256": artifacts["acceptanceApk"]["sha256"],
        "acceptanceAabSha256": artifacts["acceptanceAab"]["sha256"],
    },
    "scenarios": [
        {
            "id": item["id"],
            "prerequisites": item["prerequisites"],
            "actions": item["actions"],
            "executionMethods": item["executionMethods"],
            "expectedTargetNode": item["targetNode"],
            "result": None,
        }
        for item in acceptance["releaseSmoke"]["scenarios"]
    ],
    "benchmark": {"rounds": [], "comparison": None},
    "verdicts": {
        "r8RuntimeAcceptance": {"status": "unverified", "reasons": ["Release smoke is incomplete"], "change": "prune-deterministic-project-r8-rules", "task": "5.1"},
        "startupProfileBenefit": {"status": "unverified", "reasons": ["Two rounds are incomplete"], "change": "separate-startup-and-baseline-profile-semantics", "task": "7.5"},
    },
    "productionReadiness": {"status": "independent-fail-closed-gates-required"},
}
(execution / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

for round_index in (1, 2):
    round_dir = execution / "benchmarks" / f"round-{round_index}"
    round_dir.mkdir(parents=True)
    preflight = {"schemaVersion": 1, "status": "qualified", **device, "reasons": []}
    for phase in ("before", "after"):
        (round_dir / f"preflight-{phase}.json").write_text(json.dumps(preflight, indent=2) + "\n", encoding="utf-8")
    (round_dir / "raw-androidx-benchmark.json").write_text(json.dumps({"fixtureRound": round_index}) + "\n", encoding="utf-8")
    results = []
    for scenario_index, scenario in enumerate(
        item for item in startup["scenarios"] if item["classification"] == "startup"
    ):
        for mode in startup["compilationModes"]:
            base_ttid = 100 + scenario_index * 10 + round_index
            base_ttfd = 160 + scenario_index * 10 + round_index
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
                    "timeToInitialDisplayMs": [(base_ttid + i / 10) * factor for i in range(10)],
                    "timeToFullDisplayMs": [(base_ttfd + i / 10) * factor for i in range(10)],
                },
            })
    normalized = {
        "schemaVersion": 1,
        "evidenceScope": "physical-raw-measurement",
        "performanceBenefit": "unverified",
        "deviceType": "physical",
        "results": results,
    }
    (round_dir / "normalized.json").write_text(json.dumps(normalized, indent=2) + "\n", encoding="utf-8")
PY

MANIFEST="${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/manifest.json"
python3 "${RUNNER}" run-round --project-root "${PROJECT}" --manifest "${MANIFEST}" --round 1 \
  --raw-report baselineprofile/build/explicit-round-1.json --serial "${SERIAL}" \
  --adb /bin/false --gradle /bin/false --dry-run >"${FIXTURE_ROOT}/dry-run.json"
python3 - "${FIXTURE_ROOT}/dry-run.json" <<'PY'
import json
import sys
from pathlib import Path
plan = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert plan["task"] == ":baselineprofile:connectedBenchmarkReleaseAndroidTest"
assert plan["testClass"] == "com.ytone.longcare.baselineprofile.StartupBenchmarks"
assert plan["iterationsPerMode"] == 10
assert plan["startupMode"] == "COLD"
assert plan["suppressErrors"] is False
assert plan["productionRelease"] is False
assert not any("suppressErrors" in item for item in plan["command"])
assert "-Prelease.production=false" in plan["command"]
PY

attach_round() {
  local round="$1"
  local round_dir="${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/round-${round}"
  python3 "${MANIFEST_TOOL}" attach-benchmark-round --project-root "${PROJECT}" --manifest "${MANIFEST}" \
    --round "${round}" \
    --preflight-before "${round_dir}/preflight-before.json" \
    --preflight-after "${round_dir}/preflight-after.json" \
    --raw-report "${round_dir}/raw-androidx-benchmark.json" \
    --normalized-report "${round_dir}/normalized.json"
}

attach_round 1
if attach_round 1 >"${FIXTURE_ROOT}/duplicate-round.log" 2>&1; then
  echo "[physical-startup-runner-test][FAIL] duplicate round unexpectedly attached" >&2
  exit 1
fi
grep -Fq "already exists" "${FIXTURE_ROOT}/duplicate-round.log"

# A report outside the execution directory cannot be attached as round 2.
cp "${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/round-2/raw-androidx-benchmark.json" "${PROJECT}/artifacts/escaped-raw.json"
ROUND_TWO_DIR="${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/round-2"
if python3 "${MANIFEST_TOOL}" attach-benchmark-round --project-root "${PROJECT}" --manifest "${MANIFEST}" \
  --round 2 --preflight-before "${ROUND_TWO_DIR}/preflight-before.json" \
  --preflight-after "${ROUND_TWO_DIR}/preflight-after.json" \
  --raw-report "${PROJECT}/artifacts/escaped-raw.json" \
  --normalized-report "${ROUND_TWO_DIR}/normalized.json" >"${FIXTURE_ROOT}/escaped.log" 2>&1; then
  echo "[physical-startup-runner-test][FAIL] escaped report unexpectedly attached" >&2
  exit 1
fi
grep -Fq "must remain inside the current execution report directory" "${FIXTURE_ROOT}/escaped.log"

attach_round 2
COMPARISON="${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/comparison.json"
python3 "${COMPARATOR}" \
  --round-one "${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/round-1/normalized.json" \
  --round-two "${PROJECT}/build/reports/real-device-acceptance/benchmark-fixture/benchmarks/round-2/normalized.json" \
  --output "${COMPARISON}"
python3 "${MANIFEST_TOOL}" attach-comparison --project-root "${PROJECT}" --manifest "${MANIFEST}" --comparison "${COMPARISON}"
python3 "${MANIFEST_TOOL}" verify --project-root "${PROJECT}" --manifest "${MANIFEST}"
python3 - "${MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path
manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["verdicts"]["startupProfileBenefit"]["status"] == "verified"
assert manifest["verdicts"]["r8RuntimeAcceptance"]["status"] == "unverified"
assert "overallVerdict" not in manifest
assert len(manifest["benchmark"]["rounds"]) == 2
assert all(len(round_entry["results"]) == 8 for round_entry in manifest["benchmark"]["rounds"])
PY

# Hash mutation invalidates an already attached report.
python3 - "${ROUND_TWO_DIR}/raw-androidx-benchmark.json" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
path.write_text(path.read_text(encoding="utf-8") + "changed\n", encoding="utf-8")
PY
if python3 "${MANIFEST_TOOL}" verify --project-root "${PROJECT}" --manifest "${MANIFEST}" >"${FIXTURE_ROOT}/changed-report.log" 2>&1; then
  echo "[physical-startup-runner-test][FAIL] changed attached report unexpectedly verified" >&2
  exit 1
fi
grep -Fq "benchmark round 2 rawReport hash changed" "${FIXTURE_ROOT}/changed-report.log"

echo "[physical-startup-runner-test][PASS] fixed dry-run, two-round attachment, medians, independent verdict, path, overwrite, and hash guards passed."
