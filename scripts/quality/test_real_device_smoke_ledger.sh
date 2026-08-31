#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="${ROOT_DIR}/scripts/quality/run_real_device_smoke.py"
MANIFEST_TOOL="${ROOT_DIR}/scripts/quality/real_device_acceptance_manifest.py"
SCANNER="${ROOT_DIR}/scripts/quality/scan_real_device_log.py"
CONFIG="${ROOT_DIR}/scripts/quality/real_device_acceptance.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-ledger.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

FAKE_ADB="${FIXTURE_ROOT}/fake-adb"
cp "${ROOT_DIR}/scripts/quality/fixtures/fake_real_device_adb.sh" "${FAKE_ADB}"
chmod +x "${FAKE_ADB}"
export FAKE_DEVICE_SERIAL="fixture-qualified-device"
export FAKE_ADB_LOG="${FIXTURE_ROOT}/fake-adb.log"

create_project() {
  local name="$1"
  local project="${FIXTURE_ROOT}/${name}"
  python3 - "${project}" "${CONFIG}" "${FAKE_DEVICE_SERIAL}" <<'PY'
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
config = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
serial = sys.argv[3]
execution_id = "fixture-execution"
execution = root / "build/reports/real-device-acceptance" / execution_id
artifacts_root = root / "artifacts"
artifacts_root.mkdir(parents=True)
execution.mkdir(parents=True)
roles = {
    "acceptanceApk": ("artifacts/acceptance.apk", "acceptanceRelease"),
    "acceptanceAab": ("artifacts/acceptance.aab", "acceptanceRelease"),
    "mapping": ("artifacts/mapping.txt", "acceptanceRelease"),
    "benchmarkTargetApk": ("artifacts/benchmark-target.apk", "benchmarkRelease"),
    "benchmarkTestApk": ("artifacts/benchmark-test.apk", "benchmarkRelease"),
    "baselineProfile": ("artifacts/baseline-prof.txt", "sharedProfileInput"),
    "startupProfile": ("artifacts/startup-prof.txt", "sharedProfileInput"),
}
artifacts = {}
for index, (role, (relative, variant)) in enumerate(roles.items()):
    path = root / relative
    path.write_bytes(f"fixture-{role}-{index}".encode())
    artifacts[role] = {
        "path": relative,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "sizeBytes": path.stat().st_size,
        "variant": variant,
        "packageName": "com.ytone.longcare.baselineprofile" if role == "benchmarkTestApk" else "com.ytone.longcare",
        "versionName": "1.2.3-fixture",
        "buildSha": "a" * 40,
    }
scenarios = [
    {
        "id": item["id"],
        "prerequisites": item["prerequisites"],
        "actions": item["actions"],
        "executionMethods": item["executionMethods"],
        "expectedTargetNode": item["targetNode"],
        "result": None,
    }
    for item in config["releaseSmoke"]["scenarios"]
]
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
    "device": {
        "deviceIdHash": hashlib.sha256(serial.encode()).hexdigest(),
        "deviceType": "physical",
        "apiLevel": 36,
        "primaryAbi": "arm64-v8a",
        "cpuCores": 8,
        "fingerprint": "fixture",
        "model": "Fixture Phone",
        "battery": {"levelPercent": 90, "statusCode": 3, "powered": False},
        "thermalStatus": 0,
    },
    "artifacts": artifacts,
    "artifactVerification": {
        "schemaVersion": 1,
        "status": "passed",
        "verifier": "release-profile-artifacts-v1",
        "acceptanceApkSha256": artifacts["acceptanceApk"]["sha256"],
        "acceptanceAabSha256": artifacts["acceptanceAab"]["sha256"],
    },
    "scenarios": scenarios,
    "benchmark": {"rounds": [], "comparison": None},
    "verdicts": {
        "r8RuntimeAcceptance": {"status": "unverified", "reasons": ["incomplete"], "change": "prune-deterministic-project-r8-rules", "task": "5.1"},
        "startupProfileBenefit": {"status": "unverified", "reasons": ["incomplete"], "change": "separate-startup-and-baseline-profile-semantics", "task": "7.5"},
    },
    "productionReadiness": {"status": "independent-fail-closed-gates-required"},
}
(execution / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PY
}

manifest_path() {
  echo "$1/build/reports/real-device-acceptance/fixture-execution/manifest.json"
}

# Log scanning detects runtime blockers and strips secrets before evidence is written.
LOG_PROJECT="${FIXTURE_ROOT}/log-project"
mkdir -p "${LOG_PROJECT}/build/reports/real-device-acceptance/log-fixture"
RAW_LOG="${FIXTURE_ROOT}/raw.log"
python3 - "${RAW_LOG}" "${FAKE_DEVICE_SERIAL}" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
serial = sys.argv[2]
path.write_text(
    "FATAL EXCEPTION token=top-secret account=alice phone=13800138000 "
    "idcard=11010519491231002X https://example.test/a?code=secret " + serial + "\n",
    encoding="utf-8",
)
PY
SANITIZED="${LOG_PROJECT}/build/reports/real-device-acceptance/log-fixture/sanitized.log"
SCAN_RESULT="${LOG_PROJECT}/build/reports/real-device-acceptance/log-fixture/scan.json"
if python3 "${SCANNER}" --input "${RAW_LOG}" --sanitized-output "${SANITIZED}" \
  --result-output "${SCAN_RESULT}" --project-root "${LOG_PROJECT}" --serial "${FAKE_DEVICE_SERIAL}" >"${FIXTURE_ROOT}/scan-fail.log" 2>&1; then
  echo "[real-device-smoke-ledger-test][FAIL] forbidden log unexpectedly passed" >&2
  exit 1
fi
grep -Fq "FATAL EXCEPTION" "${FIXTURE_ROOT}/scan-fail.log"
if grep -Eq 'top-secret|alice|13800138000|11010519491231002X|code=secret|fixture-qualified-device' "${SANITIZED}" "${SCAN_RESULT}"; then
  echo "[real-device-smoke-ledger-test][FAIL] sanitized log leaked a sample secret" >&2
  exit 1
fi

# Dry-run cannot touch fake ADB, production settings, or an unbound APK.
create_project "main"
MAIN_ROOT="${FIXTURE_ROOT}/main"
MAIN_MANIFEST="$(manifest_path "${MAIN_ROOT}")"
rm -f "${FAKE_ADB_LOG}"
python3 "${RUNNER}" prepare --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" --dry-run >"${FIXTURE_ROOT}/dry-run.json"
test ! -e "${FAKE_ADB_LOG}"
grep -Fq '"productionRelease": false' "${FIXTURE_ROOT}/dry-run.json"
grep -Fq '"globalSecuritySettingMutations": []' "${FIXTURE_ROOT}/dry-run.json"
grep -Fq 'artifacts/acceptance.apk' "${FIXTURE_ROOT}/dry-run.json"

# Fake ADB proves the exact bound APK is installed and a clean target log can be recorded.
export FAKE_LOG_MODE="clean"
python3 "${RUNNER}" prepare --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}"
python3 "${RUNNER}" finish --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" --status passed \
  --completed-action 1 --completed-action 2 --completed-action 3 --target-node authenticated_home_root
grep -Eq 'install -r .*/artifacts/acceptance\.apk$' "${FAKE_ADB_LOG}"
if grep -Eq 'settings|release\.production=true|old-acceptance|pm grant' "${FAKE_ADB_LOG}"; then
  echo "[real-device-smoke-ledger-test][FAIL] fake ADB observed a forbidden mutation or artifact" >&2
  exit 1
fi

# Typed blockers are recorded without installing or launching anything.
before_lines="$(wc -l < "${FAKE_ADB_LOG}" | tr -d ' ')"
python3 "${RUNNER}" block --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario qlz_evaluation --blocker qlz-ble-device
after_lines="$(wc -l < "${FAKE_ADB_LOG}" | tr -d ' ')"
test "${before_lines}" = "${after_lines}"

# Partial, failed, or blocked ledgers cannot produce an R8 pass verdict.
if python3 "${MANIFEST_TOOL}" aggregate-r8 --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" >"${FIXTURE_ROOT}/partial-r8.log" 2>&1; then
  echo "[real-device-smoke-ledger-test][FAIL] partial R8 ledger unexpectedly passed" >&2
  exit 1
fi
grep -Fq "scenario qlz_evaluation: status=blocked" "${FIXTURE_ROOT}/partial-r8.log"
grep -Fq "missing result" "${FIXTURE_ROOT}/partial-r8.log"

# Unknown, duplicate, free-text, cross-execution, and incomplete success records fail closed.
if python3 "${RUNNER}" block --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario unknown --blocker care-account >"${FIXTURE_ROOT}/unknown.log" 2>&1; then
  exit 1
fi
grep -Fq "unknown Release smoke scenario" "${FIXTURE_ROOT}/unknown.log"
if python3 "${RUNNER}" block --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --scenario login --blocker care-account >"${FIXTURE_ROOT}/duplicate.log" 2>&1; then
  exit 1
fi
grep -Fq "already recorded" "${FIXTURE_ROOT}/duplicate.log"
if python3 "${MANIFEST_TOOL}" record-scenario --project-root "${MAIN_ROOT}" --manifest "${MAIN_MANIFEST}" \
  --session /dev/null --log-scan /dev/null --scenario location --status overall-passed >"${FIXTURE_ROOT}/free-text.log" 2>&1; then
  exit 1
fi
grep -Fq "invalid choice" "${FIXTURE_ROOT}/free-text.log"

create_project "cross-execution"
CROSS_ROOT="${FIXTURE_ROOT}/cross-execution"
CROSS_MANIFEST="$(manifest_path "${CROSS_ROOT}")"
python3 - "${CROSS_ROOT}" "${CROSS_MANIFEST}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path
root, manifest_path = map(Path, sys.argv[1:])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
execution = manifest_path.parent
log = execution / "logs/location.log"
log.parent.mkdir(parents=True)
log.write_text("clean\n", encoding="utf-8")
scan = {
    "schemaVersion": 1,
    "status": "passed",
    "forbiddenMatches": [],
    "sanitizedLog": {
        "path": log.relative_to(root).as_posix(),
        "sha256": hashlib.sha256(log.read_bytes()).hexdigest(),
        "sizeBytes": log.stat().st_size,
    },
}
(execution / "logs/location.scan.json").write_text(json.dumps(scan) + "\n", encoding="utf-8")
session = {
    "executionId": "wrong-execution",
    "scenarioId": "location",
    "deviceIdHash": manifest["device"]["deviceIdHash"],
    "buildSha": manifest["project"]["gitSha"],
    "acceptanceApkSha256": manifest["artifacts"]["acceptanceApk"]["sha256"],
    "startedAt": "2026-08-31T00:00:00+00:00",
    "endedAt": "2026-08-31T00:01:00+00:00",
    "processId": 42,
    "processAliveAtCapture": True,
}
(execution / "sessions").mkdir()
(execution / "sessions/location.json").write_text(json.dumps(session) + "\n", encoding="utf-8")
PY
if python3 "${MANIFEST_TOOL}" record-scenario --project-root "${CROSS_ROOT}" --manifest "${CROSS_MANIFEST}" \
  --session "${CROSS_ROOT}/build/reports/real-device-acceptance/fixture-execution/sessions/location.json" \
  --log-scan "${CROSS_ROOT}/build/reports/real-device-acceptance/fixture-execution/logs/location.scan.json" \
  --scenario location --status passed --completed-action 1 --completed-action 2 --completed-action 3 \
  --target-node location_update_observed >"${FIXTURE_ROOT}/cross.log" 2>&1; then
  exit 1
fi
grep -Fq "session executionId does not match" "${FIXTURE_ROOT}/cross.log"

create_project "missing-actions"
MISSING_ROOT="${FIXTURE_ROOT}/missing-actions"
MISSING_MANIFEST="$(manifest_path "${MISSING_ROOT}")"
python3 "${RUNNER}" prepare --project-root "${MISSING_ROOT}" --manifest "${MISSING_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" >/dev/null
if python3 "${RUNNER}" finish --project-root "${MISSING_ROOT}" --manifest "${MISSING_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" --status passed \
  --target-node authenticated_home_root >"${FIXTURE_ROOT}/missing-actions.log" 2>&1; then
  exit 1
fi
grep -Fq "every required action" "${FIXTURE_ROOT}/missing-actions.log"

create_project "missing-target"
TARGET_ROOT="${FIXTURE_ROOT}/missing-target"
TARGET_MANIFEST="$(manifest_path "${TARGET_ROOT}")"
python3 "${RUNNER}" prepare --project-root "${TARGET_ROOT}" --manifest "${TARGET_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" >/dev/null
if python3 "${RUNNER}" finish --project-root "${TARGET_ROOT}" --manifest "${TARGET_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" --status passed \
  --completed-action 1 --completed-action 2 --completed-action 3 >"${FIXTURE_ROOT}/missing-target.log" 2>&1; then
  exit 1
fi
grep -Fq "target node must be authenticated_home_root" "${FIXTURE_ROOT}/missing-target.log"

create_project "forbidden-log"
FORBIDDEN_ROOT="${FIXTURE_ROOT}/forbidden-log"
FORBIDDEN_MANIFEST="$(manifest_path "${FORBIDDEN_ROOT}")"
export FAKE_LOG_MODE="forbidden"
python3 "${RUNNER}" prepare --project-root "${FORBIDDEN_ROOT}" --manifest "${FORBIDDEN_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" >/dev/null
if python3 "${RUNNER}" finish --project-root "${FORBIDDEN_ROOT}" --manifest "${FORBIDDEN_MANIFEST}" \
  --scenario login --serial "${FAKE_DEVICE_SERIAL}" --adb "${FAKE_ADB}" --status passed \
  --completed-action 1 --completed-action 2 --completed-action 3 --target-node authenticated_home_root >"${FIXTURE_ROOT}/forbidden-record.log" 2>&1; then
  exit 1
fi
grep -Fq "overturned by forbidden log signatures" "${FIXTURE_ROOT}/forbidden-record.log"
if rg -q 'top-secret|13800138000|fixture-qualified-device|access_token=leak' \
  "${FORBIDDEN_ROOT}/build/reports/real-device-acceptance/fixture-execution/logs"; then
  echo "[real-device-smoke-ledger-test][FAIL] forbidden scenario report leaked raw secrets" >&2
  exit 1
fi

# A complete same-device/same-build ledger passes; identity mutation invalidates it precisely.
create_project "aggregate-pass"
PASS_ROOT="${FIXTURE_ROOT}/aggregate-pass"
PASS_MANIFEST="$(manifest_path "${PASS_ROOT}")"
python3 - "${PASS_ROOT}" "${PASS_MANIFEST}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path
root, manifest_path = map(Path, sys.argv[1:])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
execution = manifest_path.parent
log = execution / "logs/shared.log"
log.parent.mkdir(parents=True)
log.write_text("clean target log\n", encoding="utf-8")
digest = hashlib.sha256(log.read_bytes()).hexdigest()
for scenario in manifest["scenarios"]:
    scenario["result"] = {
        "status": "passed",
        "recordedAt": "2026-08-31T00:01:00+00:00",
        "completedActionIndexes": list(range(1, len(scenario["actions"]) + 1)),
        "targetEvidence": {"node": scenario["expectedTargetNode"], "observedAt": "2026-08-31T00:01:00+00:00"},
        "blockers": [],
        "failureReason": None,
        "session": {
            "executionId": manifest["executionId"],
            "scenarioId": scenario["id"],
            "deviceIdHash": manifest["device"]["deviceIdHash"],
            "buildSha": manifest["project"]["gitSha"],
            "acceptanceApkSha256": manifest["artifacts"]["acceptanceApk"]["sha256"],
            "startedAt": "2026-08-31T00:00:00+00:00",
            "endedAt": "2026-08-31T00:01:00+00:00",
            "processId": 42,
            "processAliveAtCapture": True,
        },
        "log": {
            "scanPath": "build/reports/real-device-acceptance/fixture-execution/logs/shared.scan.json",
            "path": log.relative_to(root).as_posix(),
            "sha256": digest,
            "status": "passed",
            "forbiddenMatches": [],
        },
    }
manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PY
python3 "${MANIFEST_TOOL}" aggregate-r8 --project-root "${PASS_ROOT}" --manifest "${PASS_MANIFEST}"
python3 - "${PASS_MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path
manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["verdicts"]["r8RuntimeAcceptance"]["status"] == "passed"
assert manifest["verdicts"]["startupProfileBenefit"]["status"] == "unverified"
assert "overallVerdict" not in manifest
PY
python3 - "${PASS_MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["scenarios"][0]["result"]["status"] = "failed"
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
if python3 "${MANIFEST_TOOL}" aggregate-r8 --project-root "${PASS_ROOT}" --manifest "${PASS_MANIFEST}" >"${FIXTURE_ROOT}/failed-r8.log" 2>&1; then
  exit 1
fi
grep -Fq "status=failed" "${FIXTURE_ROOT}/failed-r8.log"
python3 - "${PASS_MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["scenarios"][0]["result"]["status"] = "passed"
data["scenarios"][0]["result"]["session"]["deviceIdHash"] = "0" * 64
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
if python3 "${MANIFEST_TOOL}" aggregate-r8 --project-root "${PASS_ROOT}" --manifest "${PASS_MANIFEST}" >"${FIXTURE_ROOT}/cross-device-r8.log" 2>&1; then
  exit 1
fi
grep -Fq "cross-execution identity mismatch ['deviceIdHash']" "${FIXTURE_ROOT}/cross-device-r8.log"
python3 - "${PASS_MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["scenarios"][0]["result"]["session"]["deviceIdHash"] = data["device"]["deviceIdHash"]
data["scenarios"][0]["result"]["session"]["buildSha"] = "0" * 40
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
if python3 "${MANIFEST_TOOL}" aggregate-r8 --project-root "${PASS_ROOT}" --manifest "${PASS_MANIFEST}" >"${FIXTURE_ROOT}/cross-build-r8.log" 2>&1; then
  exit 1
fi
grep -Fq "cross-execution identity mismatch ['buildSha']" "${FIXTURE_ROOT}/cross-build-r8.log"

echo "[real-device-smoke-ledger-test][PASS] dry-run/fake ADB, typed ledger, redaction, forbidden signatures, and same-device/build R8 aggregation fixtures passed."
