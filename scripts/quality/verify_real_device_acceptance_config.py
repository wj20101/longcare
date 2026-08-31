#!/usr/bin/env python3
"""Validate the static contract for LongCare real-device acceptance evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, NoReturn


EXPECTED_SCENARIOS = {
    "login",
    "typed_navigation_state_restoration",
    "location",
    "identification_mlkit",
    "tencent_face",
    "photo_upload",
    "service_countdown",
    "qlz_evaluation",
    "video_call",
    "app_update",
}
EXPECTED_BENCHMARK_SCENARIOS = {
    "first_run_privacy",
    "logged_out",
    "care_home",
    "sales_home",
}
EXPECTED_MODES = {"none", "baseline-profile-required"}
EXPECTED_METRICS = {"timeToInitialDisplayMs", "timeToFullDisplayMs"}
EXPECTED_FORBIDDEN_SIGNATURES = {
    "ClassNotFoundException",
    "NoSuchMethodException",
    "NoSuchMethodError",
    "UnsatisfiedLinkError",
    "serialization restoration error",
    "FATAL EXCEPTION",
    "ANR in com.ytone.longcare",
    "Process com.ytone.longcare has died",
    "REAL_DEVICE_ACCEPTANCE_TIMEOUT",
}
EXPECTED_VERDICTS = {
    "r8RuntimeAcceptance": ("passed", "prune-deterministic-project-r8-rules", "5.1"),
    "startupProfileBenefit": (
        "verified",
        "separate-startup-and-baseline-profile-semantics",
        "7.5",
    ),
}
EXPECTED_RESULTS = {"passed", "failed", "blocked"}
EXPECTED_REPORT_SECRETS = {
    "account",
    "verification-code",
    "token",
    "phone",
    "identity-card",
    "photo",
    "raw-serial",
    "url-query",
}


def fail(message: str) -> NoReturn:
    print(f"[real-device-acceptance-config][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {label}: {path}")
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {label} {path}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} root must be an object")
    return value


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{label} must be an object")
    return value


def require_unique_strings(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    if not isinstance(value, list) or (not value and not allow_empty):
        fail(f"{label} must be {'an array' if allow_empty else 'a non-empty array'}")
    if not all(isinstance(item, str) and item.strip() for item in value):
        fail(f"{label} must contain non-empty strings")
    items = [item.strip() for item in value]
    if len(items) != len(set(items)):
        fail(f"{label} contains duplicates")
    return items


def require_int(value: Any, label: str, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        fail(f"{label} must be an integer >= {minimum}")
    return value


def require_number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
        fail(f"{label} must be a non-negative number")
    return float(value)


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        fail(f"cannot read target platform matrix {path}: {error}")
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"target platform matrix contains malformed line: {line}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def validate(config_path: Path, project_root: Path) -> None:
    config = load_object(config_path, "real-device acceptance config")
    startup_path = project_root / "scripts/quality/startup_profile_quality.json"
    matrix_path = project_root / "scripts/quality/target_platform_test_matrix.properties"
    startup = load_object(startup_path, "Startup quality config")
    matrix = parse_properties(matrix_path)

    if config.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")

    device = require_object(config.get("deviceEligibility"), "deviceEligibility")
    required_device_fields = {
        "deviceType",
        "requiredApiLevel",
        "requiredPrimaryAbi",
        "minimumCpuCores",
        "minimumBatteryPercent",
        "maximumThermalStatus",
        "requireNotCharging",
        "requireTimeToFullDisplay",
        "anonymousDeviceIdAlgorithm",
        "requiredProperties",
    }
    missing_device_fields = sorted(required_device_fields - set(device))
    if missing_device_fields:
        fail(f"deviceEligibility missing required fields: {missing_device_fields}")
    if device.get("deviceType") != "physical":
        fail("deviceEligibility.deviceType must be physical")
    try:
        target_api = int(matrix["current_target_api"])
    except (KeyError, ValueError):
        fail("target platform matrix current_target_api must be an integer")
    if device.get("requiredApiLevel") != target_api:
        fail(f"deviceEligibility.requiredApiLevel must match current_target_api={target_api}")
    startup_acceptance = require_object(startup.get("benefitAcceptance"), "Startup benefitAcceptance")
    if device.get("requiredPrimaryAbi") != startup_acceptance.get("requiredAbi"):
        fail("deviceEligibility.requiredPrimaryAbi must match Startup benefitAcceptance.requiredAbi")
    if device.get("requiredPrimaryAbi") != "arm64-v8a":
        fail("deviceEligibility.requiredPrimaryAbi must be arm64-v8a")
    if device.get("minimumCpuCores") != startup_acceptance.get("minimumCpuCores"):
        fail("deviceEligibility.minimumCpuCores must match Startup benefitAcceptance.minimumCpuCores")
    require_int(device.get("minimumBatteryPercent"), "deviceEligibility.minimumBatteryPercent")
    require_int(device.get("maximumThermalStatus"), "deviceEligibility.maximumThermalStatus", 0)
    if device.get("requireNotCharging") is not True:
        fail("deviceEligibility.requireNotCharging must be true")
    if device.get("requireTimeToFullDisplay") is not True:
        fail("deviceEligibility.requireTimeToFullDisplay must be true")
    if device.get("anonymousDeviceIdAlgorithm") != "sha256":
        fail("deviceEligibility.anonymousDeviceIdAlgorithm must be sha256")
    required_properties = set(require_unique_strings(device.get("requiredProperties"), "deviceEligibility.requiredProperties"))
    if not {"ro.kernel.qemu", "ro.build.fingerprint", "ro.product.model"}.issubset(required_properties):
        fail("deviceEligibility.requiredProperties must include qemu, fingerprint, and model")

    allowed_conditions = set(
        require_unique_strings(config.get("allowedExternalConditions"), "allowedExternalConditions")
    )
    smoke = require_object(config.get("releaseSmoke"), "releaseSmoke")
    if smoke.get("requiredCount") != 10:
        fail("releaseSmoke.requiredCount must be 10")
    results = set(require_unique_strings(smoke.get("allowedResultStatuses"), "releaseSmoke.allowedResultStatuses"))
    if results != EXPECTED_RESULTS:
        fail(f"releaseSmoke.allowedResultStatuses must be exactly {sorted(EXPECTED_RESULTS)}")
    matrix_domains = set(
        item for item in matrix.get("release_device_evidence_required", "").split(",") if item
    )
    declared_domains = set(
        require_unique_strings(smoke.get("requiredEvidenceDomains"), "releaseSmoke.requiredEvidenceDomains")
    )
    if declared_domains != matrix_domains:
        fail("releaseSmoke.requiredEvidenceDomains must match target platform release_device_evidence_required")

    raw_scenarios = smoke.get("scenarios")
    if not isinstance(raw_scenarios, list):
        fail("releaseSmoke.scenarios must be an array")
    scenario_ids: list[str] = []
    covered_domains: set[str] = set()
    for index, raw_scenario in enumerate(raw_scenarios):
        scenario = require_object(raw_scenario, f"releaseSmoke.scenarios[{index}]")
        scenario_id = scenario.get("id")
        if not isinstance(scenario_id, str) or not scenario_id:
            fail(f"releaseSmoke.scenarios[{index}].id must be a non-empty string")
        scenario_ids.append(scenario_id)
        prerequisites = set(
            require_unique_strings(
                scenario.get("prerequisites"), f"releaseSmoke.scenario {scenario_id}.prerequisites"
            )
        )
        unknown_conditions = sorted(prerequisites - allowed_conditions)
        if unknown_conditions:
            fail(
                f"releaseSmoke.scenario {scenario_id}.prerequisites contains unknown external conditions: "
                f"{unknown_conditions}"
            )
        require_unique_strings(scenario.get("actions"), f"releaseSmoke.scenario {scenario_id}.actions")
        target_node = scenario.get("targetNode")
        if not isinstance(target_node, str) or not target_node.strip():
            fail(f"releaseSmoke.scenario {scenario_id}.targetNode must be a non-empty string")
        require_unique_strings(
            scenario.get("executionMethods"), f"releaseSmoke.scenario {scenario_id}.executionMethods"
        )
        if scenario.get("mockSuccessAllowed") is not False:
            fail(f"releaseSmoke.scenario {scenario_id}.mockSuccessAllowed must be false")
        domains = set(
            require_unique_strings(
                scenario.get("evidenceDomains"),
                f"releaseSmoke.scenario {scenario_id}.evidenceDomains",
                allow_empty=True,
            )
        )
        unknown_domains = sorted(domains - declared_domains)
        if unknown_domains:
            fail(f"releaseSmoke.scenario {scenario_id}.evidenceDomains contains unknown domains: {unknown_domains}")
        covered_domains.update(domains)
    if len(scenario_ids) != len(set(scenario_ids)):
        fail("releaseSmoke.scenarios contains duplicate ids")
    if set(scenario_ids) != EXPECTED_SCENARIOS or len(scenario_ids) != smoke.get("requiredCount"):
        missing = sorted(EXPECTED_SCENARIOS - set(scenario_ids))
        unknown = sorted(set(scenario_ids) - EXPECTED_SCENARIOS)
        fail(f"releaseSmoke.scenarios must contain the complete ten-scenario catalog; missing={missing}, unknown={unknown}")
    if covered_domains != declared_domains:
        fail(f"releaseSmoke.scenarios must cover every evidence domain; missing={sorted(declared_domains - covered_domains)}")

    forbidden = set(
        require_unique_strings(config.get("forbiddenLogSignatures"), "forbiddenLogSignatures")
    )
    missing_forbidden = sorted(EXPECTED_FORBIDDEN_SIGNATURES - forbidden)
    if missing_forbidden:
        fail(f"forbiddenLogSignatures missing required signatures: {missing_forbidden}")

    benchmark = require_object(config.get("benchmarkEvidence"), "benchmarkEvidence")
    if benchmark.get("qualityConfig") != "scripts/quality/startup_profile_quality.json":
        fail("benchmarkEvidence.qualityConfig must reference Startup quality config")
    scenarios = set(require_unique_strings(benchmark.get("scenarioIds"), "benchmarkEvidence.scenarioIds"))
    if scenarios != EXPECTED_BENCHMARK_SCENARIOS:
        fail("benchmarkEvidence.scenarioIds must match the four Startup scenarios")
    modes = set(require_unique_strings(benchmark.get("compilationModes"), "benchmarkEvidence.compilationModes"))
    if modes != EXPECTED_MODES:
        fail(f"benchmarkEvidence.compilationModes must be exactly {sorted(EXPECTED_MODES)}")
    metrics = set(require_unique_strings(benchmark.get("requiredMetrics"), "benchmarkEvidence.requiredMetrics"))
    if metrics != EXPECTED_METRICS:
        fail(f"benchmarkEvidence.requiredMetrics must be exactly {sorted(EXPECTED_METRICS)}")
    if benchmark.get("requiredRounds") != startup_acceptance.get("requiredRounds"):
        fail("benchmarkEvidence.requiredRounds must match Startup benefitAcceptance.requiredRounds")
    if benchmark.get("iterationsPerMode") != startup_acceptance.get("iterationsPerMode"):
        fail("benchmarkEvidence.iterationsPerMode must match Startup benefitAcceptance.iterationsPerMode")
    budgets = require_object(benchmark.get("maxMedianRegressionPercent"), "benchmarkEvidence.maxMedianRegressionPercent")
    startup_budgets = require_object(
        startup_acceptance.get("maxMedianRegressionPercent"),
        "Startup benefitAcceptance.maxMedianRegressionPercent",
    )
    if set(budgets) != EXPECTED_METRICS:
        fail("benchmarkEvidence.maxMedianRegressionPercent must provide TTID and TTFD budgets")
    for metric in EXPECTED_METRICS:
        value = require_number(budgets.get(metric), f"benchmarkEvidence.maxMedianRegressionPercent.{metric}")
        if value != require_number(startup_budgets.get(metric), f"Startup budget {metric}"):
            fail(f"benchmarkEvidence.maxMedianRegressionPercent.{metric} drifted from Startup quality config")
    if benchmark.get("minimumConsistentlyImprovedMetrics") != startup_acceptance.get(
        "minimumConsistentlyImprovedMetrics"
    ):
        fail("benchmarkEvidence.minimumConsistentlyImprovedMetrics must match Startup quality config")

    raw_verdicts = config.get("verdicts")
    if not isinstance(raw_verdicts, list):
        fail("verdicts must be an array")
    verdict_ids: list[str] = []
    for index, raw_verdict in enumerate(raw_verdicts):
        verdict = require_object(raw_verdict, f"verdicts[{index}]")
        verdict_id = verdict.get("id")
        if not isinstance(verdict_id, str):
            fail(f"verdicts[{index}].id must be a non-empty string")
        verdict_ids.append(verdict_id)
        expected = EXPECTED_VERDICTS.get(verdict_id)
        if expected is None:
            fail(f"unknown verdict id: {verdict_id}")
        pass_value, change, task = expected
        if verdict.get("passValue") != pass_value:
            fail(f"verdict {verdict_id}.passValue must be {pass_value}")
        if verdict.get("otherwiseValue") != "unverified":
            fail(f"verdict {verdict_id}.otherwiseValue must be unverified")
        if verdict.get("change") != change or verdict.get("task") != task:
            fail(f"verdict {verdict_id} must map only to {change} task {task}")
        tasks_file = project_root / "openspec/changes" / change / "tasks.md"
        try:
            task_text = tasks_file.read_text(encoding="utf-8")
        except OSError as error:
            fail(f"verdict {verdict_id} mapped tasks file cannot be read: {error}")
        if f" {task} " not in task_text:
            fail(f"verdict {verdict_id} mapped task {task} is absent from {tasks_file}")
    if set(verdict_ids) != set(EXPECTED_VERDICTS) or len(verdict_ids) != 2:
        fail("verdicts must contain exactly the two independent verdicts; a single overall verdict is forbidden")

    report = require_object(config.get("reportPolicy"), "reportPolicy")
    if report.get("schemaVersion") != 1:
        fail("reportPolicy.schemaVersion must be 1")
    if report.get("defaultRoot") != "build/reports/real-device-acceptance":
        fail("reportPolicy.defaultRoot must stay under build/reports/real-device-acceptance")
    if set(require_unique_strings(report.get("allowedRoots"), "reportPolicy.allowedRoots")) != {
        "build/reports/real-device-acceptance"
    }:
        fail("reportPolicy.allowedRoots must contain only the controlled build report root")
    if report.get("rawLogRetention") != "sanitized-target-window-only":
        fail("reportPolicy.rawLogRetention must be sanitized-target-window-only")
    if report.get("serialPolicy") != "sha256-only":
        fail("reportPolicy.serialPolicy must be sha256-only")
    if report.get("localAbsolutePathsAllowed") is not False:
        fail("reportPolicy.localAbsolutePathsAllowed must be false")
    if report.get("urlQueryAllowed") is not False:
        fail("reportPolicy.urlQueryAllowed must be false")
    if report.get("photosAllowed") is not False:
        fail("reportPolicy.photosAllowed must be false")
    secrets = set(require_unique_strings(report.get("secretClasses"), "reportPolicy.secretClasses"))
    if secrets != EXPECTED_REPORT_SECRETS:
        fail("reportPolicy.secretClasses must cover account, codes, tokens, PII, photos, serial, and URL query")

    print(
        "[real-device-acceptance-config][PASS] API 36 physical eligibility, ten Release smoke "
        "scenarios, independent verdicts, log signatures, privacy policy, and Startup budgets are aligned."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "config",
        nargs="?",
        type=Path,
        default=Path(__file__).with_name("real_device_acceptance.json"),
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    args = parser.parse_args()
    validate(args.config.resolve(), args.project_root.resolve())


if __name__ == "__main__":
    main()
