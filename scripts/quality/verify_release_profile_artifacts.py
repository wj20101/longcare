#!/usr/bin/env python3
"""Fail-closed validation for the explicitly supplied Release APK and AAB."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Any


APK_PROFILE_ENTRIES = (
    ("assets/dexopt/baseline.prof", b"pro\x00"),
    ("assets/dexopt/baseline.profm", b"prm\x00"),
)
AAB_PROFILE_ENTRIES = (
    ("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof", b"pro\x00"),
    ("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm", b"prm\x00"),
)
R8_ENTRY = "BUNDLE-METADATA/com.android.tools/r8.json"
LEAK_SENTINELS = (
    (b"ProfileScenarioSetupActivity", "performance-only setup"),
    (b"PROFILE_SCENARIO_SETUP", "performance-only setup"),
    (b"PROFILE_FIXTURE_TOKEN", "performance-only setup"),
    (b"profile_scenario_id", "performance-only setup"),
    (b"profile_setup_complete", "performance-only setup"),
    (b"profile_setup_failed", "performance-only setup"),
    (b"longcare-performance-offline", "performance-only setup"),
    (b"DebugPhotoCloudUploader", "Debug Mock"),
    (b"MissingMockRouteException", "Debug Mock"),
    (b"MockRouteRegistry", "Debug Mock"),
    (b"MockScenarioProvider", "Debug Mock"),
    (b"mock-only/not-for-production", "Debug Mock"),
    (b"assets/mock/", "Debug Mock"),
    (b"identification-test-owned-verified", "test-owned identity"),
)


def fail(message: str) -> None:
    print(f"[release-profile-artifact][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def open_archive(path: Path, suffix: str) -> zipfile.ZipFile:
    if path.suffix.lower() != suffix:
        fail(f"explicit artifact must end with {suffix}: {path}")
    if not path.is_file():
        fail(f"explicit artifact does not exist: {path}")
    try:
        return zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as error:
        fail(f"artifact is not a readable ZIP archive: {path}: {error}")


def read_required(archive: zipfile.ZipFile, entry: str, artifact: Path) -> bytes:
    try:
        return archive.read(entry)
    except KeyError:
        fail(f"{artifact} is missing required entry {entry}")


def verify_profile_payload(payload: bytes, magic: bytes, entry: str) -> None:
    if len(payload) <= 8:
        fail(f"{entry} is empty or truncated")
    if payload[:4] != magic:
        fail(f"{entry} has an invalid ART Profile magic header")
    if not re.fullmatch(rb"[0-9]{3}\x00", payload[4:8]):
        fail(f"{entry} has an invalid ART Profile version header")


def dex_sort_key(name: str) -> int:
    match = re.fullmatch(r"base/dex/classes([0-9]*)\.dex", name)
    if not match:
        return 1_000_000
    return int(match.group(1) or "1")


def parse_r8(archive: zipfile.ZipFile, artifact: Path) -> dict[str, Any]:
    payload = read_required(archive, R8_ENTRY, artifact)
    try:
        value = json.loads(payload)
    except json.JSONDecodeError as error:
        fail(f"{R8_ENTRY} is not valid JSON: {error}")
    if not isinstance(value, dict):
        fail(f"{R8_ENTRY} root must be an object")
    return value


def verify_r8_and_dex(archive: zipfile.ZipFile, artifact: Path) -> None:
    r8 = parse_r8(archive, artifact)
    startup = r8.get("startupOptimization")
    if not isinstance(startup, dict):
        fail("r8.json is missing startupOptimization")
    if startup.get("isDexLayoutOptimizationEnabled") is not True:
        fail("R8 Startup DEX layout optimization must be enabled")
    if startup.get("isProfileGuidedOptimizationEnabled") is not True:
        fail("R8 profile-guided optimization must be enabled")

    metadata = r8.get("dexFiles")
    if not isinstance(metadata, list) or not metadata:
        fail("r8.json dexFiles must be non-empty")
    dex_entries = sorted(
        (name for name in archive.namelist() if re.fullmatch(r"base/dex/classes[0-9]*\.dex", name)),
        key=dex_sort_key,
    )
    if len(metadata) != len(dex_entries):
        fail(
            "r8.json dexFiles count does not match actual AAB DEX files: "
            f"metadata={len(metadata)}, actual={len(dex_entries)}"
        )

    has_startup_dex = False
    for index, (entry, item) in enumerate(zip(dex_entries, metadata, strict=True)):
        if not isinstance(item, dict):
            fail(f"r8.json dexFiles[{index}] must be an object")
        payload = archive.read(entry)
        checksum = hashlib.sha256(payload).hexdigest()
        if item.get("checksum") != checksum:
            fail(f"r8.json checksum does not match actual DEX: {entry}")
        if item.get("sizeInBytes") != len(payload):
            fail(f"r8.json size does not match actual DEX: {entry}")
        if not isinstance(item.get("startup"), bool):
            fail(f"r8.json startup flag must be Boolean: {entry}")
        has_startup_dex = has_startup_dex or item["startup"]
    if not has_startup_dex:
        fail("r8.json must mark at least one DEX file as startup=true")


def verify_no_test_capability(archive: zipfile.ZipFile, artifact: Path) -> None:
    for info in archive.infolist():
        name_payload = info.filename.encode("utf-8", errors="ignore")
        payload = archive.read(info)
        for sentinel, capability in LEAK_SENTINELS:
            if sentinel in name_payload or sentinel in payload:
                fail(
                    f"{capability} capability leaked into {artifact.name}: "
                    f"entry={info.filename}, sentinel={sentinel.decode()}"
                )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--aab", required=True, type=Path)
    parser.add_argument("--result-json", type=Path)
    args = parser.parse_args()

    with open_archive(args.apk, ".apk") as apk:
        for entry, magic in APK_PROFILE_ENTRIES:
            verify_profile_payload(read_required(apk, entry, args.apk), magic, entry)
        verify_no_test_capability(apk, args.apk)

    with open_archive(args.aab, ".aab") as aab:
        for entry, magic in AAB_PROFILE_ENTRIES:
            verify_profile_payload(read_required(aab, entry, args.aab), magic, entry)
        verify_r8_and_dex(aab, args.aab)
        verify_no_test_capability(aab, args.aab)

    if args.result_json is not None:
        result = {
            "schemaVersion": 1,
            "status": "passed",
            "verifier": "release-profile-artifacts-v1",
            "artifacts": {
                "acceptanceApk": {
                    "sha256": sha256_file(args.apk),
                    "sizeBytes": args.apk.stat().st_size,
                },
                "acceptanceAab": {
                    "sha256": sha256_file(args.aab),
                    "sizeBytes": args.aab.stat().st_size,
                },
            },
            "checks": [
                "art-profile-payloads",
                "r8-startup-dex-metadata",
                "dex-checksums",
                "production-capability-isolation",
            ],
        }
        try:
            args.result_json.parent.mkdir(parents=True, exist_ok=True)
            args.result_json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        except OSError as error:
            fail(f"cannot write verification result {args.result_json}: {error}")

    print(
        "[release-profile-artifact][PASS] explicit APK/AAB contain parseable ART Profiles, "
        "verified Startup DEX metadata/checksums, and no performance-only, Debug Mock, "
        "or test-owned identity capability."
    )


if __name__ == "__main__":
    main()
