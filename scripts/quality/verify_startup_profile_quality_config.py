#!/usr/bin/env python3
"""Validate LongCare's machine-readable Startup/Baseline Profile policy."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import NoReturn


EXPECTED_SCENARIOS = {
    "first_run_privacy": ("startup", "clean-install"),
    "logged_out": ("startup", "privacy-consented-logged-out"),
    "care_home": ("startup", "care-session"),
    "sales_home": ("startup", "sales-session"),
    "care_service_records": ("baseline-only", "care-session"),
    "sales_customers": ("baseline-only", "sales-session"),
}
EXPECTED_MODES = {"none", "baseline-profile-required"}
EXPECTED_METRICS = {"timeToInitialDisplayMs", "timeToFullDisplayMs"}
EXPECTED_METADATA = {"device", "apiLevel", "abi", "buildSha"}


def fail(message: str) -> NoReturn:
    print(f"[startup-profile-config][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_object(value: object, label: str) -> dict[str, object]:
    if not isinstance(value, dict):
        fail(f"{label} must be an object")
    return value


def require_unique_strings(value: object, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        fail(f"{label} must be a non-empty array")
    if not all(isinstance(item, str) and item for item in value):
        fail(f"{label} must contain non-empty strings")
    items = list(value)
    if len(items) != len(set(items)):
        fail(f"{label} contains duplicates")
    return items


def require_positive_int(value: object, label: str, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        fail(f"{label} must be an integer >= {minimum}")
    return value


def require_non_negative_number(value: object, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
        fail(f"{label} must be a non-negative number")
    return float(value)


def validate(config_path: Path) -> None:
    try:
        config = require_object(json.loads(config_path.read_text(encoding="utf-8")), "root")
    except FileNotFoundError:
        fail(f"missing config: {config_path}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON: {error}")

    if config.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if config.get("benefitStatus") not in {"unverified", "verified"}:
        fail("benefitStatus must be unverified or verified")

    modes = set(require_unique_strings(config.get("compilationModes"), "compilationModes"))
    if modes != EXPECTED_MODES:
        fail(f"compilationModes must be exactly {sorted(EXPECTED_MODES)}")

    raw_scenarios = config.get("scenarios")
    if not isinstance(raw_scenarios, list):
        fail("scenarios must be an array")
    scenario_ids: list[str] = []
    classifications: list[str] = []
    for index, raw_scenario in enumerate(raw_scenarios):
        scenario = require_object(raw_scenario, f"scenarios[{index}]")
        scenario_id = scenario.get("id")
        if not isinstance(scenario_id, str) or not scenario_id:
            fail(f"scenarios[{index}].id must be a non-empty string")
        if scenario_id not in EXPECTED_SCENARIOS:
            fail(f"unknown scenario id: {scenario_id}")
        scenario_ids.append(scenario_id)

        classification = scenario.get("classification")
        setup_state = scenario.get("setupState")
        expected_classification, expected_setup = EXPECTED_SCENARIOS[scenario_id]
        if classification != expected_classification:
            fail(
                f"scenario {scenario_id} classification must be {expected_classification}, "
                f"got {classification}"
            )
        if setup_state != expected_setup:
            fail(f"scenario {scenario_id} setupState must be {expected_setup}, got {setup_state}")
        classifications.append(expected_classification)
        require_unique_strings(scenario.get("requiredTags"), f"scenario {scenario_id}.requiredTags")

    if len(scenario_ids) != len(set(scenario_ids)):
        fail("scenarios contains duplicate ids")
    if set(scenario_ids) != set(EXPECTED_SCENARIOS):
        missing = sorted(set(EXPECTED_SCENARIOS) - set(scenario_ids))
        fail(f"scenarios must contain the complete six-scenario catalog; missing={missing}")
    if classifications.count("startup") != 4 or classifications.count("baseline-only") != 2:
        fail("scenarios must contain four startup and two baseline-only entries")

    benchmark = require_object(config.get("benchmarkPolicy"), "benchmarkPolicy")
    if benchmark.get("startupMode") != "cold":
        fail("benchmarkPolicy.startupMode must be cold")
    if require_positive_int(benchmark.get("iterationsPerMode"), "benchmarkPolicy.iterationsPerMode") != 10:
        fail("benchmarkPolicy.iterationsPerMode must be 10")
    metrics = set(require_unique_strings(benchmark.get("requiredMetrics"), "benchmarkPolicy.requiredMetrics"))
    if metrics != EXPECTED_METRICS:
        fail(f"benchmarkPolicy.requiredMetrics must be exactly {sorted(EXPECTED_METRICS)}")
    metadata = set(require_unique_strings(benchmark.get("requiredMetadata"), "benchmarkPolicy.requiredMetadata"))
    if metadata != EXPECTED_METADATA:
        fail(f"benchmarkPolicy.requiredMetadata must be exactly {sorted(EXPECTED_METADATA)}")

    acceptance = require_object(config.get("benefitAcceptance"), "benefitAcceptance")
    if acceptance.get("deviceType") != "physical":
        fail("benefitAcceptance.deviceType must be physical")
    if acceptance.get("requiredAbi") != "arm64-v8a":
        fail("benefitAcceptance.requiredAbi must be arm64-v8a")
    require_positive_int(acceptance.get("minimumCpuCores"), "benefitAcceptance.minimumCpuCores", 2)
    require_positive_int(acceptance.get("requiredRounds"), "benefitAcceptance.requiredRounds", 2)
    if require_positive_int(
        acceptance.get("iterationsPerMode"), "benefitAcceptance.iterationsPerMode"
    ) != 10:
        fail("benefitAcceptance.iterationsPerMode must be 10")
    budgets = require_object(
        acceptance.get("maxMedianRegressionPercent"),
        "benefitAcceptance.maxMedianRegressionPercent",
    )
    if set(budgets) != EXPECTED_METRICS:
        fail(
            "benefitAcceptance.maxMedianRegressionPercent must provide complete TTID/TTFD budgets"
        )
    for metric in EXPECTED_METRICS:
        require_non_negative_number(
            budgets.get(metric), f"benefitAcceptance.maxMedianRegressionPercent.{metric}"
        )
    require_positive_int(
        acceptance.get("minimumConsistentlyImprovedMetrics"),
        "benefitAcceptance.minimumConsistentlyImprovedMetrics",
    )
    if acceptance.get("emulatorEvidence") != "journey-and-report-format-only":
        fail("benefitAcceptance.emulatorEvidence must remain journey-and-report-format-only")

    print(
        "[startup-profile-config][PASS] six scenarios, two compilation modes, "
        "TTID/TTFD budgets, and physical-device evidence policy are complete."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "config",
        nargs="?",
        type=Path,
        default=Path(__file__).with_name("startup_profile_quality.json"),
    )
    args = parser.parse_args()
    validate(args.config.resolve())


if __name__ == "__main__":
    main()
