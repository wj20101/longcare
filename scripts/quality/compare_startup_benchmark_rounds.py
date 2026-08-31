#!/usr/bin/env python3
"""Compare two complete physical Startup Macrobenchmark rounds fail-closed."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_object(path: Path, label: str, reasons: list[str]) -> dict[str, Any] | None:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        reasons.append(f"{label}: cannot read JSON: {error}")
        return None
    if not isinstance(value, dict):
        reasons.append(f"{label}: root must be an object")
        return None
    return value


def structural_verdict(path: Path, config: Path, round_index: int, reasons: list[str]) -> bool:
    verifier = Path(__file__).with_name("verify_startup_benchmark_results.py")
    result = subprocess.run(
        [sys.executable, str(verifier), "--config", str(config), str(path)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return True
    detail = (result.stderr or result.stdout).strip().splitlines()
    message = detail[-1] if detail else "structural verifier failed"
    reasons.append(f"round {round_index}: {message}")
    return False


def result_map(report: dict[str, Any]) -> dict[tuple[str, str], dict[str, Any]]:
    results = report.get("results")
    if not isinstance(results, list):
        return {}
    return {
        (item.get("scenario"), item.get("mode")): item
        for item in results
        if isinstance(item, dict)
        and isinstance(item.get("scenario"), str)
        and isinstance(item.get("mode"), str)
    }


def metadata_identity(
    report: dict[str, Any],
    round_index: int,
    required_api: int,
    required_abi: str,
    minimum_cores: int,
    reasons: list[str],
) -> tuple[str, int, str, str] | None:
    results = report.get("results")
    if not isinstance(results, list) or not results:
        reasons.append(f"round {round_index}: results are missing")
        return None
    identities: set[tuple[str, int, str, str]] = set()
    for item in results:
        if not isinstance(item, dict) or not isinstance(item.get("metadata"), dict):
            reasons.append(f"round {round_index}: result metadata is missing")
            return None
        metadata = item["metadata"]
        device_hash = metadata.get("deviceIdHash")
        api_level = metadata.get("apiLevel")
        abi = metadata.get("abi")
        build_sha = metadata.get("buildSha")
        cores = metadata.get("cpuCores")
        if not isinstance(device_hash, str) or not re.fullmatch(r"[0-9a-f]{64}", device_hash):
            reasons.append(f"round {round_index}: metadata.deviceIdHash must be anonymous SHA-256")
            return None
        if api_level != required_api:
            suffix = "; TTFD is not acceptable on API 29 or earlier" if isinstance(api_level, int) and api_level <= 29 else ""
            reasons.append(f"round {round_index}: API must be {required_api}, got {api_level}{suffix}")
        if abi != required_abi:
            reasons.append(f"round {round_index}: ABI must be {required_abi}, got {abi}")
        if not isinstance(cores, int) or isinstance(cores, bool) or cores < minimum_cores:
            reasons.append(f"round {round_index}: cpuCores must be >= {minimum_cores}, got {cores}")
        if not isinstance(build_sha, str) or not re.fullmatch(r"[0-9a-fA-F]{7,64}", build_sha):
            reasons.append(f"round {round_index}: buildSha must be a Git SHA")
            return None
        identities.add((device_hash, api_level, str(abi), build_sha.lower()))
    if len(identities) != 1:
        reasons.append(f"round {round_index}: cross-device or cross-build results are forbidden")
        return None
    return next(iter(identities))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--round-one", type=Path)
    parser.add_argument("--round-two", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--startup-config",
        type=Path,
        default=Path(__file__).with_name("startup_profile_quality.json"),
    )
    parser.add_argument(
        "--acceptance-config",
        type=Path,
        default=Path(__file__).with_name("real_device_acceptance.json"),
    )
    args = parser.parse_args()

    reasons: list[str] = []
    try:
        startup = json.loads(args.startup_config.read_text(encoding="utf-8"))
        acceptance = json.loads(args.acceptance_config.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"[startup-round-comparator][FAIL] cannot read quality config: {error}", file=sys.stderr)
        raise SystemExit(1)
    benchmark = acceptance.get("benchmarkEvidence", {})
    device_policy = acceptance.get("deviceEligibility", {})
    scenarios = benchmark.get("scenarioIds", [])
    metrics = benchmark.get("requiredMetrics", [])
    modes = benchmark.get("compilationModes", [])
    budgets = benchmark.get("maxMedianRegressionPercent", {})
    required_rounds = benchmark.get("requiredRounds")
    if required_rounds != 2:
        reasons.append("acceptance config must require exactly two rounds")

    round_paths = [args.round_one, args.round_two]
    reports: list[dict[str, Any] | None] = []
    round_outputs: list[dict[str, Any]] = []
    identities: list[tuple[str, int, str, str] | None] = []
    maps: list[dict[tuple[str, str], dict[str, Any]]] = []
    for index, path in enumerate(round_paths, start=1):
        if path is None:
            reasons.append(f"round {index}: normalized report is missing")
            reports.append(None)
            identities.append(None)
            maps.append({})
            continue
        resolved = path.resolve()
        report = load_object(resolved, f"round {index}", reasons)
        reports.append(report)
        if report is None:
            identities.append(None)
            maps.append({})
            continue
        structural_verdict(resolved, args.startup_config.resolve(), index, reasons)
        if report.get("deviceType") != "physical":
            reasons.append(f"round {index}: deviceType must be physical")
        if report.get("evidenceScope") != "physical-raw-measurement":
            reasons.append(f"round {index}: evidenceScope must be physical-raw-measurement")
        if report.get("performanceBenefit") != "unverified":
            reasons.append(f"round {index}: input performanceBenefit must remain unverified")
        identity = metadata_identity(
            report,
            index,
            int(device_policy.get("requiredApiLevel", -1)),
            str(device_policy.get("requiredPrimaryAbi", "")),
            int(device_policy.get("minimumCpuCores", 1)),
            reasons,
        )
        identities.append(identity)
        maps.append(result_map(report))
        round_outputs.append(
            {
                "round": index,
                "sourceFile": resolved.name,
                "sha256": sha256_file(resolved),
            }
        )

    if identities[0] is not None and identities[1] is not None and identities[0] != identities[1]:
        mismatched = [
            name
            for position, name in enumerate(("deviceIdHash", "apiLevel", "abi", "buildSha"))
            if identities[0][position] != identities[1][position]
        ]
        reasons.append(f"round 1/2 identity mismatch: {mismatched}")

    comparisons: list[dict[str, Any]] = []
    consistently_improved = 0
    if all(report is not None for report in reports):
        for scenario in scenarios:
            for metric in metrics:
                round_comparisons: list[dict[str, Any]] = []
                improved_each_round: list[bool] = []
                for index, mapping in enumerate(maps, start=1):
                    none = mapping.get((scenario, "none"))
                    profile = mapping.get((scenario, "baseline-profile-required"))
                    if not isinstance(none, dict) or not isinstance(profile, dict):
                        reasons.append(f"round {index} {scenario}/{metric}: missing None/Profile pair")
                        continue
                    none_metrics = none.get("metrics")
                    profile_metrics = profile.get("metrics")
                    none_samples = none_metrics.get(metric) if isinstance(none_metrics, dict) else None
                    profile_samples = profile_metrics.get(metric) if isinstance(profile_metrics, dict) else None
                    if not isinstance(none_samples, list) or not isinstance(profile_samples, list):
                        reasons.append(f"round {index} {scenario}/{metric}: metric samples are missing")
                        continue
                    if len(none_samples) != 10 or len(profile_samples) != 10:
                        reasons.append(f"round {index} {scenario}/{metric}: each mode must contain 10 samples")
                        continue
                    if any(
                        not isinstance(sample, (int, float))
                        or isinstance(sample, bool)
                        or not math.isfinite(sample)
                        or sample <= 0
                        for sample in [*none_samples, *profile_samples]
                    ):
                        reasons.append(f"round {index} {scenario}/{metric}: samples must be finite positive numbers")
                        continue
                    try:
                        none_median = float(statistics.median(none_samples))
                        profile_median = float(statistics.median(profile_samples))
                    except (TypeError, statistics.StatisticsError):
                        reasons.append(f"round {index} {scenario}/{metric}: samples are invalid")
                        continue
                    regression = ((profile_median - none_median) / none_median) * 100.0
                    budget = float(budgets.get(metric, -1))
                    within_budget = regression <= budget
                    improved = profile_median < none_median
                    if not within_budget:
                        reasons.append(
                            f"round {index} {scenario}/{metric}: median regression {regression:.3f}% exceeds {budget:.3f}%"
                        )
                    round_comparisons.append(
                        {
                            "round": index,
                            "noneMedianMs": none_median,
                            "profileMedianMs": profile_median,
                            "regressionPercent": regression,
                            "budgetPercent": budget,
                            "withinBudget": within_budget,
                            "improved": improved,
                        }
                    )
                    improved_each_round.append(improved)
                consistent = len(improved_each_round) == 2 and all(improved_each_round)
                if consistent:
                    consistently_improved += 1
                comparisons.append(
                    {
                        "scenario": scenario,
                        "metric": metric,
                        "rounds": round_comparisons,
                        "consistentlyImproved": consistent,
                    }
                )

    minimum_improved = int(benchmark.get("minimumConsistentlyImprovedMetrics", 1))
    if len(comparisons) == len(scenarios) * len(metrics) and consistently_improved < minimum_improved:
        reasons.append(
            "scenario/metric improvement is not consistent across both rounds: "
            f"required>={minimum_improved}, actual={consistently_improved}"
        )

    status = "verified" if not reasons else "unverified"
    identity_output: dict[str, Any] | None = None
    if identities[0] is not None and identities[1] == identities[0]:
        identity_output = dict(
            zip(("deviceIdHash", "apiLevel", "abi", "buildSha"), identities[0], strict=True)
        )
    output = {
        "schemaVersion": 1,
        "verdictId": "startupProfileBenefit",
        "status": status,
        "evaluatedAt": datetime.now(timezone.utc).isoformat(),
        "identity": identity_output,
        "rounds": round_outputs,
        "comparisons": comparisons,
        "consistentlyImprovedComparisonCount": consistently_improved,
        "reasons": reasons,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    if reasons:
        print("[startup-round-comparator][FAIL] startupProfileBenefit=unverified", file=sys.stderr)
        for reason in reasons:
            print(f"- {reason}", file=sys.stderr)
        raise SystemExit(1)
    print(
        "[startup-round-comparator][PASS] startupProfileBenefit=verified; every comparison is within "
        f"budget and {consistently_improved} scenario/metric items improved in both rounds."
    )


if __name__ == "__main__":
    main()
