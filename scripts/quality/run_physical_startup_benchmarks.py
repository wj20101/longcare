#!/usr/bin/env python3
"""Run and bind two explicit physical Startup Macrobenchmark rounds."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any, NoReturn

from real_device_acceptance_manifest import load_controlled_manifest, sha256_file


BENCHMARK_TASK = ":baselineprofile:connectedBenchmarkReleaseAndroidTest"
BENCHMARK_CLASS = "com.ytone.longcare.baselineprofile.StartupBenchmarks"


def fail(message: str) -> NoReturn:
    print(f"[physical-startup-runner][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def run_command(
    command: list[str],
    label: str,
    *,
    env: dict[str, str] | None = None,
    allow_failure: bool = False,
) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=60 * 90,
            env=env,
        )
    except (OSError, subprocess.TimeoutExpired):
        fail(f"{label} could not complete")
    if result.returncode != 0 and not allow_failure:
        fail(f"{label} failed with exit code {result.returncode}")
    return result


def verify_manifest(project_root: Path, manifest: Path) -> None:
    tool = Path(__file__).with_name("real_device_acceptance_manifest.py")
    run_command(
        [
            sys.executable,
            str(tool),
            "verify",
            "--project-root",
            str(project_root),
            "--manifest",
            str(manifest),
        ],
        "execution manifest verification",
    )


def load_execution(
    args: argparse.Namespace,
    *,
    require_serial: bool = True,
) -> tuple[Path, Path, dict[str, Any], str | None]:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest(project_root, manifest_path)
    manifest = load_controlled_manifest(manifest_path, project_root)
    serial = args.serial or os.environ.get("ANDROID_SERIAL")
    if require_serial and not serial:
        fail("ANDROID_SERIAL is required; no benchmark device may be selected implicitly")
    device = manifest.get("device")
    if not isinstance(device, dict):
        fail("manifest benchmark device identity is incomplete")
    if serial and sha256_text(serial) != device.get("deviceIdHash"):
        fail("explicit benchmark device does not match the manifest-bound anonymous device")
    if device.get("deviceType") != "physical" or device.get("apiLevel") != 36:
        fail("benchmark execution requires the manifest-bound API 36 physical device")
    return project_root, manifest_path, manifest, serial


def controlled_source(project_root: Path, raw: str) -> Path:
    source = (project_root / raw).resolve()
    try:
        source.relative_to(project_root)
    except ValueError:
        fail("--raw-report must be an explicit project-relative Gradle output path")
    return source


def benchmark_command(gradle: str) -> list[str]:
    return [
        gradle,
        "--no-daemon",
        BENCHMARK_TASK,
        "-Prelease.production=false",
        "-Prelease.acceptance=false",
        f"-Pandroid.testInstrumentationRunnerArguments.class={BENCHMARK_CLASS}",
    ]


def run_round(args: argparse.Namespace) -> None:
    project_root, manifest_path, manifest, serial = load_execution(args)
    assert serial is not None
    if args.round not in {1, 2}:
        fail("--round must be 1 or 2")
    benchmark = manifest.get("benchmark")
    if not isinstance(benchmark, dict) or not isinstance(benchmark.get("rounds"), list):
        fail("manifest benchmark ledger is incomplete")
    if any(isinstance(item, dict) and item.get("round") == args.round for item in benchmark["rounds"]):
        fail(f"benchmark round {args.round} already exists")
    command = benchmark_command(args.gradle)
    plan = {
        "schemaVersion": 1,
        "round": args.round,
        "device": "<manifest-bound-explicit-serial>",
        "deviceIdHash": manifest["device"]["deviceIdHash"],
        "task": BENCHMARK_TASK,
        "testClass": BENCHMARK_CLASS,
        "startupMode": "COLD",
        "compilationModes": ["None", "Partial(BaselineProfileMode.Require)"],
        "startupScenarios": ["first_run_privacy", "logged_out", "care_home", "sales_home"],
        "iterationsPerMode": 10,
        "suppressErrors": False,
        "productionRelease": False,
        "rawReportSource": args.raw_report,
        "checks": [
            "manifest artifact hashes before and after",
            "device/battery/thermal before and after",
            "one explicit AndroidX raw report changed by this invocation",
            "normalizer and structural verifier",
        ],
        "command": [
            item if "ANDROID_SERIAL" not in item else "<redacted>"
            for item in command
        ],
    }
    if args.dry_run:
        print(json.dumps(plan, indent=2))
        print("[physical-startup-runner][PASS] dry-run fixed one device/build/helper/mode/iteration contract.", file=sys.stderr)
        return

    raw_source = controlled_source(project_root, args.raw_report)
    before_hash = sha256_file(raw_source) if raw_source.is_file() else None
    round_dir = manifest_path.parent / "benchmarks" / f"round-{args.round}"
    if round_dir.exists():
        fail(f"benchmark round {args.round} report directory already exists")
    round_dir.mkdir(parents=True)
    before_preflight = round_dir / "preflight-before.json"
    after_preflight = round_dir / "preflight-after.json"
    preflight = Path(__file__).with_name("preflight_real_device.py")
    run_command(
        [
            sys.executable,
            str(preflight),
            "--adb",
            args.adb,
            "--serial",
            serial,
            "--output",
            str(before_preflight),
        ],
        f"round {args.round} device preflight before measurement",
    )
    env = dict(os.environ)
    env["ANDROID_SERIAL"] = serial
    run_command(command, f"round {args.round} connected Macrobenchmark", env=env)
    if not raw_source.is_file():
        fail("explicit AndroidX raw report was not produced by the connected benchmark task")
    current_hash = sha256_file(raw_source)
    if before_hash is not None and current_hash == before_hash:
        fail("explicit AndroidX raw report did not change during this benchmark invocation")
    run_command(
        [
            sys.executable,
            str(preflight),
            "--adb",
            args.adb,
            "--serial",
            serial,
            "--output",
            str(after_preflight),
        ],
        f"round {args.round} device preflight after measurement",
    )
    raw_copy = round_dir / "raw-androidx-benchmark.json"
    normalized = round_dir / "normalized.json"
    shutil.copyfile(raw_source, raw_copy)
    if sha256_file(raw_copy) != current_hash:
        fail("raw AndroidX report hash changed while copying into the execution ledger")
    device = manifest["device"]
    project = manifest["project"]
    normalizer = Path(__file__).with_name("normalize_startup_benchmark_results.py")
    run_command(
        [
            sys.executable,
            str(normalizer),
            "--build-sha",
            project["gitSha"],
            "--abi",
            device["primaryAbi"],
            "--device",
            device["model"],
            "--device-id-hash",
            device["deviceIdHash"],
            "--cpu-cores",
            str(device["cpuCores"]),
            "--api-level",
            str(device["apiLevel"]),
            "--device-type",
            "physical",
            str(raw_copy),
            str(normalized),
        ],
        f"round {args.round} AndroidX report normalization",
    )
    verifier = Path(__file__).with_name("verify_startup_benchmark_results.py")
    run_command(
        [sys.executable, str(verifier), str(normalized)],
        f"round {args.round} normalized report verification",
    )
    verify_manifest(project_root, manifest_path)
    manifest_tool = Path(__file__).with_name("real_device_acceptance_manifest.py")
    run_command(
        [
            sys.executable,
            str(manifest_tool),
            "attach-benchmark-round",
            "--project-root",
            str(project_root),
            "--manifest",
            str(manifest_path),
            "--round",
            str(args.round),
            "--preflight-before",
            str(before_preflight),
            "--preflight-after",
            str(after_preflight),
            "--raw-report",
            str(raw_copy),
            "--normalized-report",
            str(normalized),
        ],
        f"round {args.round} manifest attachment",
    )
    print(f"[physical-startup-runner][PASS] benchmark round {args.round} captured and bound to the execution manifest.")


def compare(args: argparse.Namespace) -> None:
    project_root, manifest_path, manifest, _ = load_execution(args, require_serial=False)
    benchmark = manifest.get("benchmark")
    rounds = benchmark.get("rounds") if isinstance(benchmark, dict) else None
    if not isinstance(rounds, list) or {item.get("round") for item in rounds if isinstance(item, dict)} != {1, 2}:
        fail("comparison requires exactly two attached benchmark rounds")
    by_index = {item["round"]: item for item in rounds}
    round_paths = {
        index: project_root / by_index[index]["normalizedReport"]["path"]
        for index in (1, 2)
    }
    comparison_path = manifest_path.parent / "benchmarks" / "comparison.json"
    if comparison_path.exists():
        fail("benchmark comparison already exists; overwrite is forbidden")
    comparator = Path(__file__).with_name("compare_startup_benchmark_rounds.py")
    result = run_command(
        [
            sys.executable,
            str(comparator),
            "--round-one",
            str(round_paths[1]),
            "--round-two",
            str(round_paths[2]),
            "--output",
            str(comparison_path),
        ],
        "two-round Startup comparison",
        allow_failure=True,
    )
    if not comparison_path.is_file():
        fail("two-round comparator did not produce a machine-readable result")
    manifest_tool = Path(__file__).with_name("real_device_acceptance_manifest.py")
    attach = run_command(
        [
            sys.executable,
            str(manifest_tool),
            "attach-comparison",
            "--project-root",
            str(project_root),
            "--manifest",
            str(manifest_path),
            "--comparison",
            str(comparison_path),
        ],
        "benchmark comparison attachment",
        allow_failure=True,
    )
    if result.returncode != 0 or attach.returncode != 0:
        fail("startupProfileBenefit remains unverified; inspect the manifest-bound comparison reasons")
    print("[physical-startup-runner][PASS] startupProfileBenefit=verified attached to the execution manifest.")


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    round_parser = subparsers.add_parser("run-round")
    round_parser.add_argument("--project-root", type=Path, default=Path.cwd())
    round_parser.add_argument("--manifest", required=True, type=Path)
    round_parser.add_argument("--round", required=True, type=int)
    round_parser.add_argument("--raw-report", required=True)
    round_parser.add_argument("--serial")
    round_parser.add_argument("--adb", default="adb")
    round_parser.add_argument("--gradle", default="./gradlew")
    round_parser.add_argument("--dry-run", action="store_true")
    round_parser.set_defaults(handler=run_round)

    compare_parser = subparsers.add_parser("compare")
    compare_parser.add_argument("--project-root", type=Path, default=Path.cwd())
    compare_parser.add_argument("--manifest", required=True, type=Path)
    compare_parser.add_argument("--serial")
    compare_parser.set_defaults(handler=compare)

    args = parser.parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
