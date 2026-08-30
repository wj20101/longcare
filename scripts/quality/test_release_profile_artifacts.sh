#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_release_profile_artifacts.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-release-profile-artifact.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

python3 - "${FIXTURE_ROOT}" <<'PY'
import hashlib
import json
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
profile = b"pro\x00010\x00fixture-profile"
metadata_profile = b"prm\x00002\x00fixture-metadata"


def write_apk(path: Path, include_profiles: bool = True) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"production-manifest")
        archive.writestr("classes.dex", b"dex\nproduction")
        if include_profiles:
            archive.writestr("assets/dexopt/baseline.prof", profile)
            archive.writestr("assets/dexopt/baseline.profm", metadata_profile)


def write_aab(
    path: Path,
    *,
    include_profiles: bool = True,
    all_startup_false: bool = False,
    bad_checksum: bool = False,
    leaked: bool = False,
    offline_leaked: bool = False,
    debug_mock_leaked: bool = False,
    test_identity_leaked: bool = False,
) -> None:
    dex_payloads = [
        b"dex\n035\x00primary"
        + (b"ProfileScenarioSetupActivity" if leaked else b"")
        + (b"longcare-performance-offline" if offline_leaked else b"")
        + (b"DebugPhotoCloudUploader" if debug_mock_leaked else b"")
        + (b"identification-test-owned-verified" if test_identity_leaked else b""),
        b"dex\n035\x00secondary",
    ]
    dex_files = []
    for index, payload in enumerate(dex_payloads):
        checksum = hashlib.sha256(payload).hexdigest()
        if bad_checksum and index == 0:
            checksum = "0" * 64
        dex_files.append({
            "checksum": checksum,
            "sizeInBytes": len(payload),
            "startup": False if all_startup_false else index == 0,
        })
    r8 = {
        "startupOptimization": {
            "isDexLayoutOptimizationEnabled": True,
            "isProfileGuidedOptimizationEnabled": True,
        },
        "dexFiles": dex_files,
    }
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("base/manifest/AndroidManifest.xml", b"production-manifest")
        for index, payload in enumerate(dex_payloads, start=1):
            suffix = "" if index == 1 else str(index)
            archive.writestr(f"base/dex/classes{suffix}.dex", payload)
        archive.writestr("BUNDLE-METADATA/com.android.tools/r8.json", json.dumps(r8))
        if include_profiles:
            archive.writestr(
                "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
                profile,
            )
            archive.writestr(
                "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm",
                metadata_profile,
            )


write_apk(root / "valid.apk")
write_aab(root / "valid.aab")
write_apk(root / "missing.apk", include_profiles=False)
write_aab(root / "missing.aab", include_profiles=False)
write_apk(root / "all-false.apk")
write_aab(root / "all-false.aab", all_startup_false=True)
write_apk(root / "bad-checksum.apk")
write_aab(root / "bad-checksum.aab", bad_checksum=True)
write_apk(root / "leaked.apk")
write_aab(root / "leaked.aab", leaked=True)
write_apk(root / "offline-leaked.apk")
write_aab(root / "offline-leaked.aab", offline_leaked=True)
write_apk(root / "debug-mock-leaked.apk")
write_aab(root / "debug-mock-leaked.aab", debug_mock_leaked=True)
write_apk(root / "test-identity-leaked.apk")
write_aab(root / "test-identity-leaked.aab", test_identity_leaked=True)
PY

verify() {
  python3 "${VERIFIER}" --apk "$1" --aab "$2"
}

verify "${FIXTURE_ROOT}/valid.apk" "${FIXTURE_ROOT}/valid.aab"

expect_failure() {
  local name="$1"
  local expected="$2"
  local apk="${FIXTURE_ROOT}/${name}.apk"
  local aab="${FIXTURE_ROOT}/${name}.aab"
  local output="${FIXTURE_ROOT}/${name}.log"
  if verify "${apk}" "${aab}" >"${output}" 2>&1; then
    echo "[release-profile-artifact-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "${expected}" "${output}" || {
    echo "[release-profile-artifact-test][FAIL] ${name} did not report: ${expected}" >&2
    sed -n '1,160p' "${output}" >&2
    exit 1
  }
  echo "[release-profile-artifact-test][PASS] ${name} rejected"
}

# The valid archives remain beside the explicit invalid paths; the verifier must never discover
# and substitute those old workspace artifacts.
expect_failure "missing" "missing required entry"
expect_failure "all-false" "at least one DEX file as startup=true"
expect_failure "bad-checksum" "checksum does not match actual DEX"
expect_failure "leaked" "performance-only setup capability leaked"
expect_failure "offline-leaked" "performance-only setup capability leaked"
expect_failure "debug-mock-leaked" "Debug Mock capability leaked"
expect_failure "test-identity-leaked" "test-owned identity capability leaked"

echo "[release-profile-artifact-test][PASS] explicit valid artifacts accepted and old-workspace, missing-entry, all-false, checksum, setup leakage, offline leakage, Debug Mock leakage, and test-owned identity leakage regressions rejected."
