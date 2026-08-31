#!/usr/bin/env python3
"""Initialize and verify a fail-closed real-device acceptance execution ledger."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, NoReturn


REQUIRED_ROLES = {
    "acceptanceApk": "acceptanceRelease",
    "acceptanceAab": "acceptanceRelease",
    "mapping": "acceptanceRelease",
    "benchmarkTargetApk": "benchmarkRelease",
    "benchmarkTestApk": "benchmarkRelease",
    "baselineProfile": "sharedProfileInput",
    "startupProfile": "sharedProfileInput",
}
APK_ROLES = {"acceptanceApk", "benchmarkTargetApk", "benchmarkTestApk"}
VERDICT_MAPPINGS = {
    "r8RuntimeAcceptance": ("prune-deterministic-project-r8-rules", "5.1", {"passed", "unverified"}),
    "startupProfileBenefit": (
        "separate-startup-and-baseline-profile-semantics",
        "7.5",
        {"verified", "unverified"},
    ),
}


def fail(message: str) -> NoReturn:
    print(f"[real-device-manifest][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {label} {path}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} root must be an object")
    return value


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as error:
        fail(f"cannot hash artifact {path.name}: {error}")
    return digest.hexdigest()


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} must be a non-empty string")
    return value.strip()


def require_sha(value: Any, label: str, *, git: bool = False) -> str:
    text = require_string(value, label).lower()
    pattern = r"[0-9a-f]{7,64}" if git else r"[0-9a-f]{64}"
    if not re.fullmatch(pattern, text):
        fail(f"{label} must be a {'Git ' if git else ''}SHA-256 value")
    return text


def controlled_relative_path(raw: Any, project_root: Path, label: str) -> tuple[str, Path]:
    raw_path = Path(require_string(raw, label))
    if raw_path.is_absolute():
        fail(f"{label} must be project-relative; absolute local paths are forbidden")
    unresolved = project_root / raw_path
    if unresolved.is_symlink():
        fail(f"{label} must not be a symlink")
    path = unresolved.resolve()
    try:
        relative = path.relative_to(project_root)
    except ValueError:
        fail(f"{label} escapes project root")
    if not path.is_file():
        fail(f"{label} does not exist: {relative.as_posix()}")
    return relative.as_posix(), path


def actual_git_state(project_root: Path) -> tuple[str, str] | None:
    if not (project_root / ".git").exists():
        return None
    try:
        result = subprocess.run(
            ["git", "-C", str(project_root), "status", "--porcelain=v1", "--untracked-files=all"],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        fail("cannot read Git working-tree state")
    if result.returncode != 0:
        fail("cannot read Git working-tree state")
    payload = result.stdout
    return ("clean" if not payload else "dirty", hashlib.sha256(payload.encode()).hexdigest())


def load_descriptor(path: Path, project_root: Path) -> tuple[dict[str, Any], dict[str, tuple[str, Path, dict[str, Any]]]]:
    descriptor = load_object(path, "build identity descriptor")
    if descriptor.get("schemaVersion") != 1:
        fail("build identity descriptor schemaVersion must be 1")
    git_sha = require_sha(descriptor.get("gitSha"), "build identity gitSha", git=True)
    package_name = require_string(descriptor.get("packageName"), "build identity packageName")
    version_name = require_string(descriptor.get("versionName"), "build identity versionName")
    test_package = require_string(
        descriptor.get("benchmarkTestPackageName"),
        "build identity benchmarkTestPackageName",
    )
    if descriptor.get("workingTreeState") not in {"clean", "dirty"}:
        fail("build identity workingTreeState must be clean or dirty")
    require_sha(descriptor.get("workingTreeDigest"), "build identity workingTreeDigest")

    entries = descriptor.get("artifacts")
    if not isinstance(entries, list):
        fail("build identity artifacts must be an array")
    by_role: dict[str, tuple[str, Path, dict[str, Any]]] = {}
    seen_paths: set[Path] = set()
    for index, raw_entry in enumerate(entries):
        if not isinstance(raw_entry, dict):
            fail(f"build identity artifacts[{index}] must be an object")
        role = require_string(raw_entry.get("role"), f"build identity artifacts[{index}].role")
        if role not in REQUIRED_ROLES:
            fail(f"build identity contains unknown artifact role: {role}")
        if role in by_role:
            fail(f"build identity contains duplicate artifact role: {role}")
        relative, resolved = controlled_relative_path(
            raw_entry.get("path"), project_root, f"build identity artifact {role}.path"
        )
        if resolved in seen_paths:
            fail(f"build identity reuses one file for multiple roles: {role}")
        seen_paths.add(resolved)
        if raw_entry.get("variant") != REQUIRED_ROLES[role]:
            fail(f"build identity artifact {role}.variant must be {REQUIRED_ROLES[role]}")
        if require_sha(raw_entry.get("buildSha"), f"build identity artifact {role}.buildSha", git=True) != git_sha:
            fail(f"build identity artifact {role} belongs to a different build SHA")
        expected_package = test_package if role == "benchmarkTestApk" else package_name
        if raw_entry.get("packageName") != expected_package:
            fail(f"build identity artifact {role}.packageName does not match its declared package")
        if raw_entry.get("versionName") != version_name:
            fail(f"build identity artifact {role}.versionName does not match build versionName")
        by_role[role] = (relative, resolved, raw_entry)
    missing = sorted(set(REQUIRED_ROLES) - set(by_role))
    if missing:
        fail(f"build identity is missing required artifact roles: {missing}")

    current_git_state = actual_git_state(project_root)
    if current_git_state is not None:
        state, digest = current_git_state
        if descriptor.get("workingTreeState") != state or descriptor.get("workingTreeDigest") != digest:
            fail("build identity working-tree summary no longer matches the project")
        try:
            head = subprocess.run(
                ["git", "-C", str(project_root), "rev-parse", "HEAD"],
                check=True,
                capture_output=True,
                text=True,
                timeout=20,
            ).stdout.strip().lower()
        except (OSError, subprocess.SubprocessError):
            fail("cannot resolve current Git HEAD")
        if head != git_sha:
            fail("build identity gitSha does not match current Git HEAD")

    descriptor["gitSha"] = git_sha
    descriptor["packageName"] = package_name
    descriptor["versionName"] = version_name
    descriptor["benchmarkTestPackageName"] = test_package
    return descriptor, by_role


def verify_artifact_prerequisite(
    verification: dict[str, Any],
    artifacts: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    if verification.get("schemaVersion") != 1:
        fail("artifact verification schemaVersion must be 1")
    if verification.get("status") != "passed":
        fail("acceptance APK/AAB must pass the Release artifact verifier before manifest initialization")
    if verification.get("verifier") != "release-profile-artifacts-v1":
        fail("artifact verification was not produced by release-profile-artifacts-v1")
    verified_artifacts = verification.get("artifacts")
    if not isinstance(verified_artifacts, dict):
        fail("artifact verification artifacts are missing")
    for role in ("acceptanceApk", "acceptanceAab"):
        verified = verified_artifacts.get(role)
        if not isinstance(verified, dict):
            fail(f"artifact verification is missing {role}")
        if verified.get("sha256") != artifacts[role]["sha256"]:
            fail(f"artifact verification hash does not match explicit {role}")
        if verified.get("sizeBytes") != artifacts[role]["sizeBytes"]:
            fail(f"artifact verification size does not match explicit {role}")
    return {
        "schemaVersion": 1,
        "status": "passed",
        "verifier": "release-profile-artifacts-v1",
        "acceptanceApkSha256": artifacts["acceptanceApk"]["sha256"],
        "acceptanceAabSha256": artifacts["acceptanceAab"]["sha256"],
    }


def validate_manifest_location(manifest_path: Path, project_root: Path, execution_id: str | None = None) -> None:
    report_root = (project_root / "build/reports/real-device-acceptance").resolve()
    try:
        relative = manifest_path.resolve().relative_to(report_root)
    except ValueError:
        fail("manifest must remain under build/reports/real-device-acceptance")
    if relative.name != "manifest.json" or len(relative.parts) != 2:
        fail("manifest path must be <report-root>/<execution-id>/manifest.json")
    if execution_id is not None and relative.parts[0] != execution_id:
        fail("manifest execution directory does not match executionId")


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def load_controlled_manifest(path: Path, project_root: Path) -> dict[str, Any]:
    manifest = load_object(path, "execution manifest")
    if manifest.get("schemaVersion") != 1:
        fail("execution manifest schemaVersion must be 1")
    execution_id = require_string(manifest.get("executionId"), "execution manifest executionId")
    validate_manifest_location(path, project_root, execution_id)
    return manifest


def controlled_report_file(raw: Any, project_root: Path, execution_id: str, label: str) -> tuple[str, Path]:
    relative, path = controlled_relative_path(raw, project_root, label)
    prefix = f"build/reports/real-device-acceptance/{execution_id}/"
    if not relative.startswith(prefix):
        fail(f"{label} must remain inside the current execution report directory")
    return relative, path


def initialize(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    execution_id = require_string(args.execution_id, "executionId")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{5,79}", execution_id):
        fail("executionId must be 6-80 safe filename characters")
    manifest_path = project_root / "build/reports/real-device-acceptance" / execution_id / "manifest.json"
    validate_manifest_location(manifest_path, project_root, execution_id)
    if manifest_path.exists():
        fail("execution manifest already exists; report overwrite is forbidden")

    config = load_object(args.config.resolve(), "acceptance config")
    device = load_object(args.device_report.resolve(), "device preflight report")
    if device.get("status") != "qualified":
        fail("device preflight report must be qualified")
    device_id_hash = require_sha(device.get("deviceIdHash"), "device report deviceIdHash")
    if device.get("deviceType") != "physical":
        fail("device preflight report must describe a physical device")

    descriptor, described = load_descriptor(args.build_identity.resolve(), project_root)
    explicit_paths = {
        "acceptanceApk": args.acceptance_apk,
        "acceptanceAab": args.acceptance_aab,
        "mapping": args.mapping,
        "benchmarkTargetApk": args.benchmark_target_apk,
        "benchmarkTestApk": args.benchmark_test_apk,
        "baselineProfile": args.baseline_profile,
        "startupProfile": args.startup_profile,
    }
    artifacts: dict[str, dict[str, Any]] = {}
    initial_hashes: dict[str, str] = {}
    for role, explicit in explicit_paths.items():
        relative, resolved = controlled_relative_path(explicit, project_root, f"--{role}")
        described_relative, described_path, entry = described[role]
        if resolved != described_path or relative != described_relative:
            fail(f"explicit {role} path does not match build identity descriptor")
        if role in APK_ROLES and resolved.suffix.lower() != ".apk":
            fail(f"explicit {role} must end with .apk")
        if role == "acceptanceAab" and resolved.suffix.lower() != ".aab":
            fail("explicit acceptanceAab must end with .aab")
        digest = sha256_file(resolved)
        initial_hashes[role] = digest
        artifacts[role] = {
            "path": relative,
            "sha256": digest,
            "sizeBytes": resolved.stat().st_size,
            "variant": entry["variant"],
            "packageName": entry["packageName"],
            "versionName": entry["versionName"],
            "buildSha": entry["buildSha"].lower(),
        }

    verification = load_object(args.artifact_verification.resolve(), "Release artifact verification")
    artifact_verification = verify_artifact_prerequisite(verification, artifacts)

    smoke = config.get("releaseSmoke")
    if not isinstance(smoke, dict) or not isinstance(smoke.get("scenarios"), list):
        fail("acceptance config Release smoke catalog is incomplete")
    scenarios = [
        {
            "id": scenario["id"],
            "prerequisites": scenario["prerequisites"],
            "actions": scenario["actions"],
            "executionMethods": scenario["executionMethods"],
            "expectedTargetNode": scenario["targetNode"],
            "result": None,
        }
        for scenario in smoke["scenarios"]
        if isinstance(scenario, dict)
    ]
    if len(scenarios) != smoke.get("requiredCount"):
        fail("acceptance config Release smoke catalog is incomplete")

    manifest = {
        "schemaVersion": 1,
        "executionId": execution_id,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "project": {
            "gitSha": descriptor["gitSha"],
            "workingTreeState": descriptor["workingTreeState"],
            "workingTreeDigest": descriptor["workingTreeDigest"],
            "packageName": descriptor["packageName"],
            "benchmarkTestPackageName": descriptor["benchmarkTestPackageName"],
            "versionName": descriptor["versionName"],
            "acceptanceVariant": "acceptanceRelease",
            "benchmarkVariant": "benchmarkRelease",
        },
        "device": {
            "deviceIdHash": device_id_hash,
            "deviceType": device.get("deviceType"),
            "apiLevel": device.get("apiLevel"),
            "primaryAbi": device.get("primaryAbi"),
            "cpuCores": device.get("cpuCores"),
            "fingerprint": device.get("fingerprint"),
            "model": device.get("model"),
            "battery": device.get("battery"),
            "thermalStatus": device.get("thermalStatus"),
        },
        "artifacts": artifacts,
        "artifactVerification": artifact_verification,
        "scenarios": scenarios,
        "benchmark": {"rounds": [], "comparison": None},
        "verdicts": {
            "r8RuntimeAcceptance": {
                "status": "unverified",
                "reasons": ["Release smoke is incomplete"],
                "change": "prune-deterministic-project-r8-rules",
                "task": "5.1",
            },
            "startupProfileBenefit": {
                "status": "unverified",
                "reasons": ["Two physical benchmark rounds are incomplete"],
                "change": "separate-startup-and-baseline-profile-semantics",
                "task": "7.5",
            },
        },
        "productionReadiness": {
            "status": "independent-fail-closed-gates-required",
            "source": "existing production configuration and vendor readiness gates",
        },
    }

    for role, (_, path, _) in described.items():
        if sha256_file(path) != initial_hashes[role]:
            fail(f"artifact {role} changed during manifest initialization")
    manifest_path.parent.mkdir(parents=True, exist_ok=False)
    atomic_write_json(manifest_path, manifest)
    print(f"[real-device-manifest][PASS] initialized {manifest_path.relative_to(project_root)}")


def verify_manifest(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    manifest = load_controlled_manifest(manifest_path, project_root)
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(REQUIRED_ROLES):
        fail("execution manifest must contain exactly the seven artifact roles")
    resolved_paths: set[Path] = set()
    for role in REQUIRED_ROLES:
        entry = artifacts.get(role)
        if not isinstance(entry, dict):
            fail(f"execution manifest artifact {role} must be an object")
        relative, path = controlled_relative_path(
            entry.get("path"), project_root, f"execution manifest artifact {role}.path"
        )
        if relative != entry.get("path"):
            fail(f"execution manifest artifact {role}.path is not normalized")
        if path in resolved_paths:
            fail(f"execution manifest reuses one file for multiple roles: {role}")
        resolved_paths.add(path)
        expected = require_sha(entry.get("sha256"), f"execution manifest artifact {role}.sha256")
        if sha256_file(path) != expected:
            fail(f"execution manifest artifact {role} hash changed after initialization")
        if entry.get("sizeBytes") != path.stat().st_size:
            fail(f"execution manifest artifact {role} size changed after initialization")
    verification = manifest.get("artifactVerification")
    if not isinstance(verification, dict) or verification.get("status") != "passed":
        fail("execution manifest is missing the passed Release artifact prerequisite")
    if verification.get("acceptanceApkSha256") != artifacts["acceptanceApk"]["sha256"]:
        fail("execution manifest acceptance APK no longer matches artifact verification")
    if verification.get("acceptanceAabSha256") != artifacts["acceptanceAab"]["sha256"]:
        fail("execution manifest acceptance AAB no longer matches artifact verification")
    execution_id = manifest["executionId"]
    scenarios = manifest.get("scenarios")
    if not isinstance(scenarios, list):
        fail("execution manifest scenarios must be an array")
    for scenario in scenarios:
        if not isinstance(scenario, dict) or scenario.get("result") is None:
            continue
        result = scenario.get("result")
        log = result.get("log") if isinstance(result, dict) else None
        if not isinstance(log, dict):
            fail(f"scenario {scenario.get('id')} result log is missing")
        _, log_path = controlled_report_file(
            log.get("path"), project_root, execution_id, f"scenario {scenario.get('id')} log"
        )
        if sha256_file(log_path) != log.get("sha256"):
            fail(f"scenario {scenario.get('id')} sanitized log hash changed")
    benchmark = manifest.get("benchmark")
    if not isinstance(benchmark, dict):
        fail("execution manifest benchmark ledger must be an object")
    rounds = benchmark.get("rounds")
    if not isinstance(rounds, list) or len(rounds) > 2:
        fail("execution manifest benchmark rounds must contain at most two entries")
    observed_rounds: set[int] = set()
    for round_entry in rounds:
        if not isinstance(round_entry, dict) or round_entry.get("round") not in {1, 2}:
            fail("execution manifest benchmark round index must be 1 or 2")
        round_index = round_entry["round"]
        if round_index in observed_rounds:
            fail(f"execution manifest benchmark round {round_index} is duplicated")
        observed_rounds.add(round_index)
        for report_name in ("preflightBefore", "preflightAfter", "rawReport", "normalizedReport"):
            report = round_entry.get(report_name)
            if not isinstance(report, dict):
                fail(f"benchmark round {round_index} {report_name} is missing")
            _, report_path = controlled_report_file(
                report.get("path"),
                project_root,
                execution_id,
                f"benchmark round {round_index} {report_name}",
            )
            if sha256_file(report_path) != report.get("sha256"):
                fail(f"benchmark round {round_index} {report_name} hash changed")
    comparison = benchmark.get("comparison")
    if comparison is not None:
        if not isinstance(comparison, dict):
            fail("execution manifest benchmark comparison must be an object or null")
        _, comparison_path = controlled_report_file(
            comparison.get("path"), project_root, execution_id, "benchmark comparison"
        )
        if sha256_file(comparison_path) != comparison.get("sha256"):
            fail("benchmark comparison hash changed")
    verdicts = manifest.get("verdicts")
    if not isinstance(verdicts, dict) or set(verdicts) != set(VERDICT_MAPPINGS):
        fail("execution manifest must contain exactly the two independent verdicts")
    if "overallVerdict" in manifest or "overallStatus" in manifest:
        fail("execution manifest must not contain a single overall verdict")
    for verdict_id, (change, task, allowed_statuses) in VERDICT_MAPPINGS.items():
        verdict = verdicts.get(verdict_id)
        if not isinstance(verdict, dict):
            fail(f"execution manifest verdict {verdict_id} must be an object")
        if verdict.get("change") != change or verdict.get("task") != task:
            fail(f"execution manifest verdict {verdict_id} maps to the wrong OpenSpec task")
        if verdict.get("status") not in allowed_statuses:
            fail(f"execution manifest verdict {verdict_id} has an invalid status")
    print(
        "[real-device-manifest][PASS] execution schema, controlled paths, artifact hashes, "
        "and Release artifact prerequisite are intact."
    )


def attach_benchmark_round(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    manifest = load_controlled_manifest(manifest_path, project_root)
    execution_id = manifest["executionId"]
    benchmark = manifest.get("benchmark")
    if not isinstance(benchmark, dict) or not isinstance(benchmark.get("rounds"), list):
        fail("execution manifest benchmark ledger is incomplete")
    if args.round not in {1, 2}:
        fail("benchmark round must be 1 or 2")
    if any(isinstance(item, dict) and item.get("round") == args.round for item in benchmark["rounds"]):
        fail(f"benchmark round {args.round} already exists; report overwrite is forbidden")

    report_arguments = {
        "preflightBefore": args.preflight_before,
        "preflightAfter": args.preflight_after,
        "rawReport": args.raw_report,
        "normalizedReport": args.normalized_report,
    }
    reports: dict[str, dict[str, Any]] = {}
    for name, path_value in report_arguments.items():
        path = path_value.resolve()
        raw = path.relative_to(project_root).as_posix() if path.is_relative_to(project_root) else str(path)
        relative, controlled = controlled_report_file(
            raw, project_root, execution_id, f"benchmark round {args.round} {name}"
        )
        reports[name] = {
            "path": relative,
            "sha256": sha256_file(controlled),
            "sizeBytes": controlled.stat().st_size,
        }
    before = load_object(args.preflight_before.resolve(), "benchmark preflight before")
    after = load_object(args.preflight_after.resolve(), "benchmark preflight after")
    device = manifest.get("device")
    project = manifest.get("project")
    if not isinstance(device, dict) or not isinstance(project, dict):
        fail("execution device/build identity is incomplete")
    for label, preflight in (("before", before), ("after", after)):
        if preflight.get("status") != "qualified":
            fail(f"benchmark round {args.round} preflight {label} is not qualified")
        if preflight.get("deviceIdHash") != device.get("deviceIdHash"):
            fail(f"benchmark round {args.round} preflight {label} belongs to another device")

    normalized = load_object(args.normalized_report.resolve(), "normalized benchmark report")
    if normalized.get("deviceType") != "physical" or normalized.get("evidenceScope") != "physical-raw-measurement":
        fail(f"benchmark round {args.round} normalized report must be physical raw measurement")
    results = normalized.get("results")
    if not isinstance(results, list) or len(results) != 8:
        fail(f"benchmark round {args.round} normalized report must contain eight scenario/mode results")
    medians: list[dict[str, Any]] = []
    for result in results:
        if not isinstance(result, dict) or not isinstance(result.get("metadata"), dict):
            fail(f"benchmark round {args.round} normalized result metadata is incomplete")
        metadata = result["metadata"]
        if metadata.get("deviceIdHash") != device.get("deviceIdHash"):
            fail(f"benchmark round {args.round} normalized report belongs to another device")
        if str(metadata.get("buildSha", "")).lower() != str(project.get("gitSha", "")).lower():
            fail(f"benchmark round {args.round} normalized report belongs to another build")
        metrics = result.get("metrics")
        if not isinstance(metrics, dict):
            fail(f"benchmark round {args.round} normalized result metrics are incomplete")
        metric_medians: dict[str, float] = {}
        for metric in ("timeToInitialDisplayMs", "timeToFullDisplayMs"):
            samples = metrics.get(metric)
            if not isinstance(samples, list) or len(samples) != 10:
                fail(f"benchmark round {args.round} {result.get('scenario')}/{result.get('mode')} {metric} must have 10 samples")
            metric_medians[metric] = float(statistics.median(samples))
        medians.append(
            {
                "scenario": result.get("scenario"),
                "mode": result.get("mode"),
                "profileStatus": result.get("profileStatus"),
                "sampleCount": result.get("iterations"),
                "mediansMs": metric_medians,
            }
        )
    benchmark["rounds"].append(
        {
            "round": args.round,
            **reports,
            "deviceState": {
                "before": {"battery": before.get("battery"), "thermalStatus": before.get("thermalStatus")},
                "after": {"battery": after.get("battery"), "thermalStatus": after.get("thermalStatus")},
            },
            "results": medians,
        }
    )
    benchmark["rounds"].sort(key=lambda item: item["round"])
    atomic_write_json(manifest_path, manifest)
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    print(f"[real-device-manifest][PASS] attached benchmark round {args.round} with raw/normalized hashes and medians.")


def attach_comparison(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    manifest = load_controlled_manifest(manifest_path, project_root)
    benchmark = manifest.get("benchmark")
    verdicts = manifest.get("verdicts")
    if not isinstance(benchmark, dict) or not isinstance(verdicts, dict):
        fail("execution benchmark or verdict ledger is incomplete")
    rounds = benchmark.get("rounds")
    if not isinstance(rounds, list) or {item.get("round") for item in rounds if isinstance(item, dict)} != {1, 2}:
        fail("benchmark comparison requires exactly rounds 1 and 2")
    if benchmark.get("comparison") is not None:
        fail("benchmark comparison already exists; report overwrite is forbidden")
    comparison_path = args.comparison.resolve()
    raw = (
        comparison_path.relative_to(project_root).as_posix()
        if comparison_path.is_relative_to(project_root)
        else str(comparison_path)
    )
    relative, controlled = controlled_report_file(
        raw, project_root, manifest["executionId"], "benchmark comparison"
    )
    comparison = load_object(controlled, "benchmark comparison")
    if comparison.get("schemaVersion") != 1 or comparison.get("verdictId") != "startupProfileBenefit":
        fail("benchmark comparison schema or verdictId is invalid")
    status = comparison.get("status")
    if status not in {"verified", "unverified"}:
        fail("benchmark comparison status must be verified or unverified")
    expected_hashes = {
        item["normalizedReport"]["sha256"]
        for item in rounds
        if isinstance(item.get("normalizedReport"), dict)
    }
    comparison_hashes = {
        item.get("sha256")
        for item in comparison.get("rounds", [])
        if isinstance(item, dict)
    }
    if expected_hashes != comparison_hashes:
        fail("benchmark comparison does not reference the two manifest-bound normalized reports")
    identity = comparison.get("identity")
    project = manifest.get("project")
    device = manifest.get("device")
    if status == "verified":
        if not isinstance(identity, dict):
            fail("verified benchmark comparison identity is missing")
        if identity.get("deviceIdHash") != device.get("deviceIdHash") or str(identity.get("buildSha", "")).lower() != str(project.get("gitSha", "")).lower():
            fail("verified benchmark comparison belongs to another device/build")
    benchmark["comparison"] = {
        "path": relative,
        "sha256": sha256_file(controlled),
        "sizeBytes": controlled.stat().st_size,
        "status": status,
        "consistentlyImprovedComparisonCount": comparison.get("consistentlyImprovedComparisonCount"),
    }
    verdict = verdicts.get("startupProfileBenefit")
    if not isinstance(verdict, dict):
        fail("startupProfileBenefit verdict is missing")
    verdict["status"] = status
    verdict["reasons"] = comparison.get("reasons", [])
    verdict["evaluatedAt"] = comparison.get("evaluatedAt")
    atomic_write_json(manifest_path, manifest)
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    if status != "verified":
        print("[real-device-manifest][FAIL] startupProfileBenefit remains unverified", file=sys.stderr)
        raise SystemExit(1)
    print("[real-device-manifest][PASS] startupProfileBenefit=verified attached independently of R8 verdict.")


def record_scenario(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    manifest = load_controlled_manifest(manifest_path, project_root)
    execution_id = manifest["executionId"]
    scenarios = manifest.get("scenarios")
    if not isinstance(scenarios, list):
        fail("execution manifest scenarios must be an array")
    scenario = next(
        (item for item in scenarios if isinstance(item, dict) and item.get("id") == args.scenario),
        None,
    )
    if scenario is None:
        fail(f"unknown Release smoke scenario: {args.scenario}")
    if scenario.get("result") is not None:
        fail(f"Release smoke scenario already has a result: {args.scenario}")

    session_path = args.session.resolve()
    _, _ = controlled_report_file(
        session_path.relative_to(project_root).as_posix() if session_path.is_relative_to(project_root) else str(session_path),
        project_root,
        execution_id,
        "scenario session",
    )
    session = load_object(session_path, "scenario session")
    project = manifest.get("project")
    device = manifest.get("device")
    artifacts = manifest.get("artifacts")
    if not isinstance(project, dict) or not isinstance(device, dict) or not isinstance(artifacts, dict):
        fail("execution manifest identity is incomplete")
    expected_context = {
        "executionId": execution_id,
        "scenarioId": args.scenario,
        "deviceIdHash": device.get("deviceIdHash"),
        "buildSha": project.get("gitSha"),
        "acceptanceApkSha256": artifacts.get("acceptanceApk", {}).get("sha256")
        if isinstance(artifacts.get("acceptanceApk"), dict)
        else None,
    }
    for key, expected in expected_context.items():
        if session.get(key) != expected:
            fail(f"scenario session {key} does not match this execution")

    actions = scenario.get("actions")
    if not isinstance(actions, list) or not actions:
        fail(f"scenario {args.scenario} actions are incomplete")
    completed = sorted(set(args.completed_action or []))
    if any(index < 1 or index > len(actions) for index in completed):
        fail(f"scenario {args.scenario} completed action index is out of range")
    blockers = sorted(set(args.blocker or []))
    prerequisites = scenario.get("prerequisites")
    if not isinstance(prerequisites, list):
        fail(f"scenario {args.scenario} prerequisites are incomplete")
    unknown_blockers = sorted(set(blockers) - set(prerequisites))
    if unknown_blockers:
        fail(f"scenario {args.scenario} contains unknown typed blockers: {unknown_blockers}")

    log_scan_path = args.log_scan.resolve()
    raw_scan_path = (
        log_scan_path.relative_to(project_root).as_posix()
        if log_scan_path.is_relative_to(project_root)
        else str(log_scan_path)
    )
    scan_relative, _ = controlled_report_file(
        raw_scan_path, project_root, execution_id, "scenario log scan"
    )
    scan = load_object(log_scan_path, "scenario log scan")
    if scan.get("schemaVersion") != 1:
        fail("scenario log scan schemaVersion must be 1")
    sanitized = scan.get("sanitizedLog")
    if not isinstance(sanitized, dict):
        fail("scenario log scan sanitizedLog is missing")
    log_relative, log_path = controlled_report_file(
        sanitized.get("path"), project_root, execution_id, "scenario sanitized log"
    )
    log_hash = require_sha(sanitized.get("sha256"), "scenario sanitized log sha256")
    if sha256_file(log_path) != log_hash:
        fail("scenario sanitized log hash changed before result recording")
    forbidden_matches = scan.get("forbiddenMatches")
    if not isinstance(forbidden_matches, list) or not all(isinstance(item, str) for item in forbidden_matches):
        fail("scenario log scan forbiddenMatches must be an array")

    failure_reasons = {
        "target-not-reached",
        "forbidden-log-signature",
        "process-died",
        "timeout",
        "unexpected-runtime-error",
    }
    target_evidence: dict[str, Any] | None = None
    failure_reason: str | None = None
    if args.status == "passed":
        if completed != list(range(1, len(actions) + 1)):
            fail(f"scenario {args.scenario} cannot pass until every required action is completed")
        if args.target_node != scenario.get("expectedTargetNode"):
            fail(f"scenario {args.scenario} target node must be {scenario.get('expectedTargetNode')}")
        if blockers:
            fail(f"scenario {args.scenario} passed result cannot contain blockers")
        if scan.get("status") != "passed" or forbidden_matches:
            fail(f"scenario {args.scenario} passed result is overturned by forbidden log signatures")
        if session.get("processAliveAtCapture") is not True:
            fail(f"scenario {args.scenario} cannot pass after the target process died")
        target_evidence = {
            "node": args.target_node,
            "observedAt": require_string(session.get("endedAt"), "scenario session endedAt"),
        }
    elif args.status == "blocked":
        if not blockers:
            fail(f"scenario {args.scenario} blocked result requires typed external conditions")
        if args.target_node is not None:
            fail(f"scenario {args.scenario} blocked result cannot claim target evidence")
    elif args.status == "failed":
        if args.failure_reason not in failure_reasons:
            fail(
                f"scenario {args.scenario} failed result requires one of {sorted(failure_reasons)}"
            )
        failure_reason = args.failure_reason
        if forbidden_matches and failure_reason != "forbidden-log-signature":
            fail(f"scenario {args.scenario} forbidden signatures require failure-reason=forbidden-log-signature")
    else:
        fail("scenario status must be passed, failed, or blocked")

    scenario["result"] = {
        "status": args.status,
        "recordedAt": datetime.now(timezone.utc).isoformat(),
        "completedActionIndexes": completed,
        "targetEvidence": target_evidence,
        "blockers": blockers,
        "failureReason": failure_reason,
        "session": {
            **expected_context,
            "startedAt": session.get("startedAt"),
            "endedAt": session.get("endedAt"),
            "processId": session.get("processId"),
            "processAliveAtCapture": session.get("processAliveAtCapture"),
        },
        "log": {
            "scanPath": scan_relative,
            "path": log_relative,
            "sha256": log_hash,
            "status": scan.get("status"),
            "forbiddenMatches": forbidden_matches,
        },
    }
    atomic_write_json(manifest_path, manifest)
    print(f"[real-device-manifest][PASS] recorded {args.scenario}={args.status}")


def aggregate_r8(args: argparse.Namespace) -> None:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest(argparse.Namespace(project_root=project_root, manifest=manifest_path))
    manifest = load_controlled_manifest(manifest_path, project_root)
    scenarios = manifest.get("scenarios")
    project = manifest.get("project")
    device = manifest.get("device")
    artifacts = manifest.get("artifacts")
    verdicts = manifest.get("verdicts")
    if not isinstance(scenarios, list) or len(scenarios) != 10:
        fail("R8 aggregation requires the complete ten-scenario catalog")
    if not all(isinstance(value, dict) for value in (project, device, artifacts, verdicts)):
        fail("execution identity or verdicts are incomplete")
    expected_context = {
        "executionId": manifest.get("executionId"),
        "deviceIdHash": device.get("deviceIdHash"),
        "buildSha": project.get("gitSha"),
        "acceptanceApkSha256": artifacts.get("acceptanceApk", {}).get("sha256")
        if isinstance(artifacts.get("acceptanceApk"), dict)
        else None,
    }
    reasons: list[str] = []
    for scenario in scenarios:
        scenario_id = scenario.get("id") if isinstance(scenario, dict) else "unknown"
        result = scenario.get("result") if isinstance(scenario, dict) else None
        if not isinstance(result, dict):
            reasons.append(f"scenario {scenario_id}: missing result")
            continue
        status = result.get("status")
        if status != "passed":
            reasons.append(f"scenario {scenario_id}: status={status}")
            continue
        session = result.get("session")
        if not isinstance(session, dict):
            reasons.append(f"scenario {scenario_id}: missing session identity")
            continue
        mismatches = [key for key, value in expected_context.items() if session.get(key) != value]
        if mismatches:
            reasons.append(f"scenario {scenario_id}: cross-execution identity mismatch {mismatches}")
        log = result.get("log")
        if not isinstance(log, dict) or log.get("status") != "passed" or log.get("forbiddenMatches"):
            reasons.append(f"scenario {scenario_id}: forbidden or incomplete runtime log")
            continue
        try:
            _, log_path = controlled_report_file(
                log.get("path"), project_root, manifest["executionId"], f"scenario {scenario_id} log"
            )
            if sha256_file(log_path) != log.get("sha256"):
                reasons.append(f"scenario {scenario_id}: sanitized log hash changed")
        except SystemExit:
            reasons.append(f"scenario {scenario_id}: sanitized log path escaped execution")

    verdict = verdicts.get("r8RuntimeAcceptance")
    if not isinstance(verdict, dict):
        fail("r8RuntimeAcceptance verdict is missing")
    verdict["status"] = "unverified" if reasons else "passed"
    verdict["reasons"] = reasons
    verdict["evaluatedAt"] = datetime.now(timezone.utc).isoformat()
    atomic_write_json(manifest_path, manifest)
    if reasons:
        print("[real-device-manifest][FAIL] r8RuntimeAcceptance remains unverified:", file=sys.stderr)
        for reason in reasons:
            print(f"- {reason}", file=sys.stderr)
        raise SystemExit(1)
    print("[real-device-manifest][PASS] r8RuntimeAcceptance=passed; all ten scenarios and logs match one device/build.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    init = subparsers.add_parser("init")
    init.add_argument("--project-root", type=Path, default=Path.cwd())
    init.add_argument("--config", type=Path, default=Path(__file__).with_name("real_device_acceptance.json"))
    init.add_argument("--execution-id", required=True)
    init.add_argument("--device-report", required=True, type=Path)
    init.add_argument("--build-identity", required=True, type=Path)
    init.add_argument("--artifact-verification", required=True, type=Path)
    init.add_argument("--acceptance-apk", required=True)
    init.add_argument("--acceptance-aab", required=True)
    init.add_argument("--mapping", required=True)
    init.add_argument("--benchmark-target-apk", required=True)
    init.add_argument("--benchmark-test-apk", required=True)
    init.add_argument("--baseline-profile", required=True)
    init.add_argument("--startup-profile", required=True)
    init.set_defaults(handler=initialize)

    verify = subparsers.add_parser("verify")
    verify.add_argument("--project-root", type=Path, default=Path.cwd())
    verify.add_argument("--manifest", required=True, type=Path)
    verify.set_defaults(handler=verify_manifest)

    record = subparsers.add_parser("record-scenario")
    record.add_argument("--project-root", type=Path, default=Path.cwd())
    record.add_argument("--manifest", required=True, type=Path)
    record.add_argument("--session", required=True, type=Path)
    record.add_argument("--log-scan", required=True, type=Path)
    record.add_argument("--scenario", required=True)
    record.add_argument("--status", required=True, choices=("passed", "failed", "blocked"))
    record.add_argument("--completed-action", action="append", type=int)
    record.add_argument("--target-node")
    record.add_argument("--blocker", action="append")
    record.add_argument("--failure-reason")
    record.set_defaults(handler=record_scenario)

    aggregate = subparsers.add_parser("aggregate-r8")
    aggregate.add_argument("--project-root", type=Path, default=Path.cwd())
    aggregate.add_argument("--manifest", required=True, type=Path)
    aggregate.set_defaults(handler=aggregate_r8)

    attach_round = subparsers.add_parser("attach-benchmark-round")
    attach_round.add_argument("--project-root", type=Path, default=Path.cwd())
    attach_round.add_argument("--manifest", required=True, type=Path)
    attach_round.add_argument("--round", required=True, type=int)
    attach_round.add_argument("--preflight-before", required=True, type=Path)
    attach_round.add_argument("--preflight-after", required=True, type=Path)
    attach_round.add_argument("--raw-report", required=True, type=Path)
    attach_round.add_argument("--normalized-report", required=True, type=Path)
    attach_round.set_defaults(handler=attach_benchmark_round)

    attach_result = subparsers.add_parser("attach-comparison")
    attach_result.add_argument("--project-root", type=Path, default=Path.cwd())
    attach_result.add_argument("--manifest", required=True, type=Path)
    attach_result.add_argument("--comparison", required=True, type=Path)
    attach_result.set_defaults(handler=attach_comparison)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
