#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST_TOOL="${ROOT_DIR}/scripts/quality/real_device_acceptance_manifest.py"
ARTIFACT_VERIFIER="${ROOT_DIR}/scripts/quality/verify_release_profile_artifacts.py"
CONFIG="${ROOT_DIR}/scripts/quality/real_device_acceptance.json"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-manifest.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

create_project() {
  local name="$1"
  local mutation="${2:-valid}"
  local project="${FIXTURE_ROOT}/${name}"
  mkdir -p "${project}/artifacts" "${project}/profiles" "${project}/evidence"
  python3 - "${project}" "${mutation}" <<'PY'
import hashlib
import json
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
mutation = sys.argv[2]
artifacts = root / "artifacts"
profile = b"pro\x00010\x00fixture-profile"
metadata_profile = b"prm\x00002\x00fixture-metadata"

with zipfile.ZipFile(artifacts / "acceptance.apk", "w") as archive:
    archive.writestr("AndroidManifest.xml", b"acceptance")
    archive.writestr("classes.dex", b"dex production")
    archive.writestr("assets/dexopt/baseline.prof", profile)
    archive.writestr("assets/dexopt/baseline.profm", metadata_profile)

dex_payload = b"dex\n035\x00primary"
r8 = {
    "startupOptimization": {
        "isDexLayoutOptimizationEnabled": True,
        "isProfileGuidedOptimizationEnabled": True,
    },
    "dexFiles": [{
        "checksum": hashlib.sha256(dex_payload).hexdigest(),
        "sizeInBytes": len(dex_payload),
        "startup": True,
    }],
}
with zipfile.ZipFile(artifacts / "acceptance.aab", "w") as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"acceptance")
    archive.writestr("base/dex/classes.dex", dex_payload)
    archive.writestr("BUNDLE-METADATA/com.android.tools/r8.json", json.dumps(r8))
    archive.writestr("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof", profile)
    archive.writestr("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm", metadata_profile)

(artifacts / "mapping.txt").write_text("com.example.Source -> a:\n", encoding="utf-8")
(artifacts / "benchmark-target.apk").write_bytes(b"benchmark target apk")
(artifacts / "benchmark-test.apk").write_bytes(b"benchmark test apk")
(artifacts / "old-acceptance.apk").write_bytes(b"old acceptance apk")
(root / "profiles/baseline-prof.txt").write_text("HSPLcom/ytone/longcare/MainActivity;\n", encoding="utf-8")
(root / "profiles/startup-prof.txt").write_text("SLcom/ytone/longcare/MainActivity;\n", encoding="utf-8")
(root / "profiles/old-startup-prof.txt").write_text("old profile\n", encoding="utf-8")

serial = "fixture-qualified-device"
device = {
    "schemaVersion": 1,
    "status": "qualified",
    "deviceIdHash": hashlib.sha256(serial.encode()).hexdigest(),
    "deviceType": "physical",
    "apiLevel": 36,
    "primaryAbi": "arm64-v8a",
    "cpuCores": 8,
    "fingerprint": "google/fixture/fixture:16/ABC/123:user/release-keys",
    "model": "Fixture Phone",
    "battery": {"levelPercent": 90, "statusCode": 3, "powered": False},
    "thermalStatus": 0,
    "reasons": [],
}
(root / "evidence/device.json").write_text(json.dumps(device, indent=2) + "\n", encoding="utf-8")

git_sha = "a" * 40
roles = [
    ("acceptanceApk", "artifacts/acceptance.apk", "acceptanceRelease", "com.ytone.longcare"),
    ("acceptanceAab", "artifacts/acceptance.aab", "acceptanceRelease", "com.ytone.longcare"),
    ("mapping", "artifacts/mapping.txt", "acceptanceRelease", "com.ytone.longcare"),
    ("benchmarkTargetApk", "artifacts/benchmark-target.apk", "benchmarkRelease", "com.ytone.longcare"),
    ("benchmarkTestApk", "artifacts/benchmark-test.apk", "benchmarkRelease", "com.ytone.longcare.baselineprofile"),
    ("baselineProfile", "profiles/baseline-prof.txt", "sharedProfileInput", "com.ytone.longcare"),
    ("startupProfile", "profiles/startup-prof.txt", "sharedProfileInput", "com.ytone.longcare"),
]
descriptor = {
    "schemaVersion": 1,
    "gitSha": git_sha,
    "workingTreeState": "dirty",
    "workingTreeDigest": "b" * 64,
    "packageName": "com.ytone.longcare",
    "benchmarkTestPackageName": "com.ytone.longcare.baselineprofile",
    "versionName": "1.2.3-fixture",
    "artifacts": [
        {
            "role": role,
            "path": path,
            "variant": variant,
            "packageName": package_name,
            "versionName": "1.2.3-fixture",
            "buildSha": git_sha,
        }
        for role, path, variant, package_name in roles
    ],
}

if mutation == "duplicate-role":
    descriptor["artifacts"][-1] = dict(descriptor["artifacts"][0])
elif mutation == "wrong-package":
    descriptor["artifacts"][0]["packageName"] = "com.example.old"
elif mutation == "wrong-version":
    descriptor["artifacts"][1]["versionName"] = "0.0.1"
elif mutation == "wrong-variant":
    descriptor["artifacts"][3]["variant"] = "debug"
elif mutation == "cross-build":
    descriptor["artifacts"][4]["buildSha"] = "c" * 40
elif mutation != "valid":
    raise SystemExit(f"unknown mutation: {mutation}")

(root / "evidence/build-identity.json").write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")
PY
  python3 "${ARTIFACT_VERIFIER}" \
    --apk "${project}/artifacts/acceptance.apk" \
    --aab "${project}/artifacts/acceptance.aab" \
    --result-json "${project}/evidence/artifact-verification.json" >/dev/null
}

init_manifest() {
  local project="$1"
  local execution_id="$2"
  shift 2
  python3 "${MANIFEST_TOOL}" init \
    --project-root "${project}" \
    --config "${CONFIG}" \
    --execution-id "${execution_id}" \
    --device-report "${project}/evidence/device.json" \
    --build-identity "${project}/evidence/build-identity.json" \
    --artifact-verification "${project}/evidence/artifact-verification.json" \
    --acceptance-apk "artifacts/acceptance.apk" \
    --acceptance-aab "artifacts/acceptance.aab" \
    --mapping "artifacts/mapping.txt" \
    --benchmark-target-apk "artifacts/benchmark-target.apk" \
    --benchmark-test-apk "artifacts/benchmark-test.apk" \
    --baseline-profile "profiles/baseline-prof.txt" \
    --startup-profile "profiles/startup-prof.txt" \
    "$@"
}

create_project "valid"
VALID_ROOT="${FIXTURE_ROOT}/valid"
init_manifest "${VALID_ROOT}" "fixture-valid"
VALID_MANIFEST="${VALID_ROOT}/build/reports/real-device-acceptance/fixture-valid/manifest.json"
python3 "${MANIFEST_TOOL}" verify --project-root "${VALID_ROOT}" --manifest "${VALID_MANIFEST}"

python3 - "${VALID_MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
manifest = json.loads(path.read_text(encoding="utf-8"))
assert set(manifest["verdicts"]) == {"r8RuntimeAcceptance", "startupProfileBenefit"}
assert len(manifest["scenarios"]) == 10
assert all(not Path(item["path"]).is_absolute() for item in manifest["artifacts"].values())
assert "/tmp/" not in path.read_text(encoding="utf-8")
PY

expect_init_failure() {
  local name="$1"
  local mutation="$2"
  local expected="$3"
  shift 3
  create_project "${name}" "${mutation}"
  local project="${FIXTURE_ROOT}/${name}"
  local output="${FIXTURE_ROOT}/${name}.log"
  if init_manifest "${project}" "fixture-${name}" "$@" >"${output}" 2>&1; then
    echo "[real-device-manifest-test][FAIL] ${name} unexpectedly initialized" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output}"; then
    echo "[real-device-manifest-test][FAIL] ${name} did not report ${expected}" >&2
    sed 's/^/[fixture-output] /' "${output}" >&2
    exit 1
  fi
}

expect_init_failure "duplicate-role" "duplicate-role" "duplicate artifact role"
expect_init_failure "wrong-package" "wrong-package" "packageName does not match"
expect_init_failure "wrong-version" "wrong-version" "versionName does not match"
expect_init_failure "wrong-variant" "wrong-variant" "variant must be benchmarkRelease"
expect_init_failure "cross-build" "cross-build" "different build SHA"
expect_init_failure "old-apk-path" "valid" "explicit acceptanceApk path does not match" \
  --acceptance-apk "artifacts/old-acceptance.apk"
expect_init_failure "old-profile-path" "valid" "explicit startupProfile path does not match" \
  --startup-profile "profiles/old-startup-prof.txt"

create_project "symlink-artifact"
ln -s "baseline-prof.txt" "${FIXTURE_ROOT}/symlink-artifact/profiles/baseline-link.txt"
python3 - "${FIXTURE_ROOT}/symlink-artifact/evidence/build-identity.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
next(item for item in data["artifacts"] if item["role"] == "baselineProfile")["path"] = "profiles/baseline-link.txt"
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
if init_manifest "${FIXTURE_ROOT}/symlink-artifact" "fixture-symlink-artifact" \
  --baseline-profile "profiles/baseline-link.txt" >"${FIXTURE_ROOT}/symlink-artifact.log" 2>&1; then
  echo "[real-device-manifest-test][FAIL] symlink artifact unexpectedly initialized" >&2
  exit 1
fi
grep -Fq "must not be a symlink" "${FIXTURE_ROOT}/symlink-artifact.log"

create_project "missing-file"
python3 - "${FIXTURE_ROOT}/missing-file/artifacts/benchmark-test.apk" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[1]).unlink()
PY
if init_manifest "${FIXTURE_ROOT}/missing-file" "fixture-missing-file" >"${FIXTURE_ROOT}/missing-file.log" 2>&1; then
  echo "[real-device-manifest-test][FAIL] missing file unexpectedly initialized" >&2
  exit 1
fi
grep -Fq "does not exist" "${FIXTURE_ROOT}/missing-file.log"

create_project "verification-mismatch"
python3 - "${FIXTURE_ROOT}/verification-mismatch/evidence/artifact-verification.json" <<'PY'
import json
import sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["artifacts"]["acceptanceApk"]["sha256"] = "0" * 64
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
if init_manifest "${FIXTURE_ROOT}/verification-mismatch" "fixture-verification-mismatch" >"${FIXTURE_ROOT}/verification-mismatch.log" 2>&1; then
  echo "[real-device-manifest-test][FAIL] mismatched verifier result unexpectedly initialized" >&2
  exit 1
fi
grep -Fq "artifact verification hash does not match explicit acceptanceApk" "${FIXTURE_ROOT}/verification-mismatch.log"

python3 - "${VALID_ROOT}/artifacts/benchmark-target.apk" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
path.write_bytes(path.read_bytes() + b"changed")
PY
if python3 "${MANIFEST_TOOL}" verify --project-root "${VALID_ROOT}" --manifest "${VALID_MANIFEST}" >"${FIXTURE_ROOT}/changed.log" 2>&1; then
  echo "[real-device-manifest-test][FAIL] changed artifact unexpectedly verified" >&2
  exit 1
fi
grep -Fq "benchmarkTargetApk hash changed after initialization" "${FIXTURE_ROOT}/changed.log"

echo "[real-device-manifest-test][PASS] explicit build identity initialized and stale, missing, symlink, duplicate, mismatched, cross-build, and changed artifact fixtures rejected."
