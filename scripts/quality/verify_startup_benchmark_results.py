#!/usr/bin/env python3
"""Validate normalized Startup Macrobenchmark JSON before it is compared or published."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path
from typing import Any


def fail(message: str) -> None:
    print(f"[startup-benchmark][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {label} {path}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} root must be an object")
    return value


def non_empty_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("results", type=Path)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("startup_profile_quality.json"),
    )
    args = parser.parse_args()

    config = load_object(args.config, "quality config")
    report = load_object(args.results, "benchmark report")
    if report.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")

    policy = config.get("benchmarkPolicy")
    scenarios = config.get("scenarios")
    modes = config.get("compilationModes")
    if not isinstance(policy, dict) or not isinstance(scenarios, list) or not isinstance(modes, list):
        fail("quality config is incomplete")

    expected_scenarios = {
        scenario["id"]: scenario["setupState"]
        for scenario in scenarios
        if isinstance(scenario, dict) and scenario.get("classification") == "startup"
    }
    expected_modes = set(modes)
    expected_pairs = {
        (scenario, mode)
        for scenario in expected_scenarios
        for mode in expected_modes
    }
    expected_metrics = policy.get("requiredMetrics")
    expected_iterations = policy.get("iterationsPerMode")
    expected_startup_mode = policy.get("startupMode")
    if not isinstance(expected_metrics, list) or not isinstance(expected_iterations, int):
        fail("quality config benchmark policy is incomplete")

    results = report.get("results")
    if not isinstance(results, list) or not results:
        fail("results must be a non-empty array")

    actual_pairs: set[tuple[str, str]] = set()
    comparison_identity: tuple[str, int, str, str] | None = None
    for index, result in enumerate(results):
        location = f"results[{index}]"
        if not isinstance(result, dict):
            fail(f"{location} must be an object")
        scenario = result.get("scenario")
        mode = result.get("mode")
        if not isinstance(scenario, str) or not isinstance(mode, str):
            fail(f"{location} must declare scenario and mode")
        pair = (scenario, mode)
        if pair in actual_pairs:
            fail(f"duplicate scenario/mode pair: {scenario}/{mode}")
        actual_pairs.add(pair)

        if scenario not in expected_scenarios or mode not in expected_modes:
            fail(f"unexpected scenario/mode pair: {scenario}/{mode}")
        if result.get("setupState") != expected_scenarios[scenario]:
            fail(f"{scenario}/{mode} setupState is not symmetric with policy")
        if result.get("startupMode") != expected_startup_mode:
            fail(f"{scenario}/{mode} startupMode must be {expected_startup_mode}")
        if result.get("iterations") != expected_iterations:
            fail(f"{scenario}/{mode} iterations must be {expected_iterations}")

        expected_profile_status = (
            "disabled" if mode == "none" else "required-applied"
        )
        if result.get("profileStatus") != expected_profile_status:
            fail(
                f"{scenario}/{mode} profileStatus must be {expected_profile_status}"
            )

        metadata = result.get("metadata")
        if not isinstance(metadata, dict):
            fail(f"{location}.metadata must be an object")
        device = metadata.get("device")
        api_level = metadata.get("apiLevel")
        abi = metadata.get("abi")
        build_sha = metadata.get("buildSha")
        if not non_empty_string(device):
            fail(f"{location}.metadata.device is required")
        if not isinstance(api_level, int) or isinstance(api_level, bool) or api_level < 24:
            fail(f"{location}.metadata.apiLevel must be an Android API integer")
        if not non_empty_string(abi):
            fail(f"{location}.metadata.abi is required")
        if not non_empty_string(build_sha) or not re.fullmatch(r"[0-9a-fA-F]{7,64}", build_sha):
            fail(f"{location}.metadata.buildSha must be a Git SHA")
        identity = (device.strip(), api_level, abi.strip(), build_sha.lower())
        if comparison_identity is None:
            comparison_identity = identity
        elif identity != comparison_identity:
            fail("cross-device or cross-build benchmark comparison is forbidden")

        metrics = result.get("metrics")
        if not isinstance(metrics, dict):
            fail(f"{location}.metrics must be an object")
        for metric in expected_metrics:
            samples = metrics.get(metric)
            if not isinstance(samples, list):
                fail(f"{scenario}/{mode} is missing metric {metric}")
            if len(samples) != expected_iterations:
                fail(
                    f"{scenario}/{mode} metric {metric} must contain "
                    f"{expected_iterations} iterations"
                )
            for sample in samples:
                if (
                    not isinstance(sample, (int, float))
                    or isinstance(sample, bool)
                    or not math.isfinite(sample)
                    or sample <= 0
                ):
                    fail(f"{scenario}/{mode} metric {metric} has an invalid sample")

    missing = sorted(expected_pairs - actual_pairs)
    extra = sorted(actual_pairs - expected_pairs)
    if missing or extra:
        fail(f"scenario/mode pairs are incomplete: missing={missing}, extra={extra}")

    print(
        "[startup-benchmark][PASS] four scenarios have symmetric None/Profile "
        "runs, ten TTID/TTFD samples, and one device/build identity."
    )


if __name__ == "__main__":
    main()
