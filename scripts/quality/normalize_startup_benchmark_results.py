#!/usr/bin/env python3
"""Normalize AndroidX Macrobenchmark JSON into the repository startup report schema."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


BENCHMARK_IDENTITIES = {
    "firstRunPrivacyNone": ("first_run_privacy", "none"),
    "firstRunPrivacyProfile": ("first_run_privacy", "baseline-profile-required"),
    "loggedOutNone": ("logged_out", "none"),
    "loggedOutProfile": ("logged_out", "baseline-profile-required"),
    "careHomeNone": ("care_home", "none"),
    "careHomeProfile": ("care_home", "baseline-profile-required"),
    "salesHomeNone": ("sales_home", "none"),
    "salesHomeProfile": ("sales_home", "baseline-profile-required"),
}


def fail(message: str) -> None:
    print(f"[startup-benchmark-normalizer][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {label} {path}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} root must be an object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} must be a non-empty string")
    return value.strip()


def read_context_api_level(report: dict[str, Any]) -> int:
    context = report.get("context")
    build = context.get("build") if isinstance(context, dict) else None
    version = build.get("version") if isinstance(build, dict) else None
    sdk = version.get("sdk") if isinstance(version, dict) else None
    if not isinstance(sdk, int) or isinstance(sdk, bool):
        fail("AndroidX report context.build.version.sdk must be an integer")
    return sdk


def read_context_device(report: dict[str, Any]) -> str:
    context = report.get("context")
    build = context.get("build") if isinstance(context, dict) else None
    if not isinstance(build, dict):
        fail("AndroidX report context.build must be an object")
    model = build.get("model")
    device = build.get("device")
    if isinstance(model, str) and model.strip():
        return model.strip()
    return require_string(device, "AndroidX report context.build.device")


def metric_runs(
    benchmark: dict[str, Any],
    metric_name: str,
    scenario: str,
    mode: str,
) -> list[int | float]:
    metrics = benchmark.get("metrics")
    metric = metrics.get(metric_name) if isinstance(metrics, dict) else None
    runs = metric.get("runs") if isinstance(metric, dict) else None
    if not isinstance(runs, list):
        fail(f"{scenario}/{mode} is missing metric {metric_name} in AndroidX report")
    return runs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_results", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("startup_profile_quality.json"),
    )
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--abi", required=True)
    parser.add_argument("--device")
    parser.add_argument("--api-level", type=int)
    parser.add_argument(
        "--device-type",
        choices=("emulator", "physical"),
        required=True,
    )
    args = parser.parse_args()

    build_sha = require_string(args.build_sha, "--build-sha")
    if not re.fullmatch(r"[0-9a-fA-F]{7,64}", build_sha):
        fail("--build-sha must be a Git SHA")
    abi = require_string(args.abi, "--abi")

    config = load_object(args.config, "quality config")
    raw_report = load_object(args.raw_results, "AndroidX benchmark report")
    policy = config.get("benchmarkPolicy")
    scenarios = config.get("scenarios")
    if not isinstance(policy, dict) or not isinstance(scenarios, list):
        fail("quality config is incomplete")
    expected_metrics = policy.get("requiredMetrics")
    expected_iterations = policy.get("iterationsPerMode")
    startup_mode = policy.get("startupMode")
    if not isinstance(expected_metrics, list) or not isinstance(expected_iterations, int):
        fail("quality config benchmark policy is incomplete")
    setup_states = {
        scenario["id"]: scenario["setupState"]
        for scenario in scenarios
        if isinstance(scenario, dict) and scenario.get("classification") == "startup"
    }

    device = require_string(args.device, "--device") if args.device else read_context_device(raw_report)
    api_level = args.api_level if args.api_level is not None else read_context_api_level(raw_report)
    if api_level < 24:
        fail("API level must be at least 24")

    raw_benchmarks = raw_report.get("benchmarks")
    if not isinstance(raw_benchmarks, list) or not raw_benchmarks:
        fail("AndroidX report benchmarks must be a non-empty array")

    normalized_results: list[dict[str, Any]] = []
    observed_names: set[str] = set()
    for index, raw_benchmark in enumerate(raw_benchmarks):
        if not isinstance(raw_benchmark, dict):
            fail(f"AndroidX report benchmarks[{index}] must be an object")
        name = require_string(raw_benchmark.get("name"), f"benchmarks[{index}].name")
        identity = BENCHMARK_IDENTITIES.get(name)
        if identity is None:
            fail(f"unexpected AndroidX startup benchmark: {name}")
        if name in observed_names:
            fail(f"duplicate AndroidX startup benchmark: {name}")
        observed_names.add(name)
        scenario, mode = identity
        if scenario not in setup_states:
            fail(f"benchmark {name} maps to undeclared startup scenario {scenario}")
        repeat_iterations = raw_benchmark.get("repeatIterations")
        if repeat_iterations != expected_iterations:
            fail(
                f"{scenario}/{mode} repeatIterations must be {expected_iterations}, "
                f"got {repeat_iterations}"
            )
        metrics = {
            metric: metric_runs(raw_benchmark, metric, scenario, mode)
            for metric in expected_metrics
        }
        normalized_results.append(
            {
                "scenario": scenario,
                "mode": mode,
                "profileStatus": (
                    "disabled" if mode == "none" else "required-applied"
                ),
                "setupState": setup_states[scenario],
                "startupMode": startup_mode,
                "iterations": repeat_iterations,
                "metadata": {
                    "device": device,
                    "apiLevel": api_level,
                    "abi": abi,
                    "buildSha": build_sha.lower(),
                },
                "metrics": metrics,
            }
        )

    missing_names = sorted(set(BENCHMARK_IDENTITIES) - observed_names)
    if missing_names:
        fail(f"AndroidX startup benchmark set is incomplete: missing={missing_names}")

    normalized_results.sort(key=lambda result: (result["scenario"], result["mode"]))
    normalized_report = {
        "schemaVersion": 1,
        "evidenceScope": (
            "journey-and-report-format-only"
            if args.device_type == "emulator"
            else "physical-raw-measurement"
        ),
        "performanceBenefit": "unverified",
        "deviceType": args.device_type,
        "results": normalized_results,
    }
    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(normalized_report, indent=2) + "\n",
            encoding="utf-8",
        )
    except OSError as error:
        fail(f"cannot write normalized report {args.output}: {error}")
    print(
        f"[startup-benchmark-normalizer][PASS] normalized {len(normalized_results)} "
        f"AndroidX benchmarks as {args.device_type} evidence."
    )


if __name__ == "__main__":
    main()
