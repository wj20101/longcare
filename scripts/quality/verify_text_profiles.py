#!/usr/bin/env python3
"""Verify generated Baseline/Startup text semantics and generator coverage."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


PROFILE_FLAGS = re.compile(r"^[HSP]+(?=L)")


def fail(message: str) -> None:
    print(f"[text-profile][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def source_rules(path: Path, label: str) -> set[str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        fail(f"cannot read {label} {path}: {error}")
    rules = {
        line.strip()
        for line in lines
        if line.strip() and not line.lstrip().startswith("#")
    }
    if not rules:
        fail(f"{label} Profile must contain at least one normalized rule")
    return rules


def rule_identities(rules: set[str]) -> set[str]:
    """Compare Profile membership without conflating ART H/S/P usage flags with code identity."""
    return {PROFILE_FLAGS.sub("", rule) for rule in rules}


def verify_generator(generator: Path, config_path: Path) -> int:
    try:
        source = generator.read_text(encoding="utf-8")
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read generator/config: {error}")

    expected = {
        scenario["id"].upper(): scenario["classification"] == "startup"
        for scenario in config.get("scenarios", [])
        if isinstance(scenario, dict)
    }
    if len(expected) != 6:
        fail("quality config must define exactly six scenarios")

    collection_pattern = re.compile(
        r"collectScenario\s*\(\s*"
        r"scenario\s*=\s*ProfileScenario\.([A-Z_]+)\s*,\s*"
        r"includeInStartupProfile\s*=\s*(true|false)\s*,?\s*\)",
        re.DOTALL,
    )
    actual: dict[str, bool] = {}
    for scenario, startup_value in collection_pattern.findall(source):
        if scenario in actual:
            fail(f"generator collects scenario more than once: {scenario}")
        actual[scenario] = startup_value == "true"
    if set(actual) != set(expected):
        fail(
            "generator scenario coverage mismatch: "
            f"missing={sorted(set(expected) - set(actual))}, "
            f"extra={sorted(set(actual) - set(expected))}"
        )
    mismatched = sorted(
        scenario for scenario in expected if actual[scenario] != expected[scenario]
    )
    if mismatched:
        fail(f"generator Startup/Baseline classification mismatch: {mismatched}")
    return len(actual)


def sha256(path: Path) -> str:
    try:
        return hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as error:
        fail(f"cannot hash Profile {path}: {error}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--startup", required=True, type=Path)
    parser.add_argument("--generator", required=True, type=Path)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("startup_profile_quality.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        help="Optional machine-readable JSON report written only after successful verification.",
    )
    args = parser.parse_args()

    baseline_source = source_rules(args.baseline, "Baseline")
    startup_source = source_rules(args.startup, "Startup")
    baseline = rule_identities(baseline_source)
    startup = rule_identities(startup_source)
    unexpected_startup = sorted(startup - baseline)
    if unexpected_startup:
        fail(
            "Startup Profile must be a subset of Baseline Profile; "
            f"unexpected rules={unexpected_startup[:5]}"
        )
    baseline_only = baseline - startup
    if not baseline_only:
        fail("Baseline Profile must be a strict superset of Startup Profile")

    scenario_count = verify_generator(args.generator, args.config)
    if args.report is not None:
        report = {
            "schemaVersion": 1,
            "status": "verified",
            "baseline": {
                "path": str(args.baseline),
                "sha256": sha256(args.baseline),
                "normalizedRuleCount": len(baseline),
                "sourceRuleCount": len(baseline_source),
            },
            "startup": {
                "path": str(args.startup),
                "sha256": sha256(args.startup),
                "normalizedRuleCount": len(startup),
                "sourceRuleCount": len(startup_source),
            },
            "startupIsBaselineSubset": True,
            "baselineIsStrictSuperset": True,
            "baselineOnlyRuleCount": len(baseline_only),
            "generatorScenarioCount": scenario_count,
        }
        try:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(
                json.dumps(report, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        except OSError as error:
            fail(f"cannot write report {args.report}: {error}")
    print(
        "[text-profile][PASS] Startup is a strict Baseline subset "
        f"(startup={len(startup)}, baseline={len(baseline)}, "
        f"baselineOnly={len(baseline_only)}) and all six scenarios are collected."
    )


if __name__ == "__main__":
    main()
