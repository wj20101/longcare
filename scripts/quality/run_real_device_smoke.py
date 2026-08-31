#!/usr/bin/env python3
"""Prepare, capture, and record minified acceptance smoke on one bound device/build."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from typing import Any, NoReturn

from real_device_acceptance_manifest import (
    load_controlled_manifest,
    record_scenario,
    sha256_file,
)
from scan_real_device_log import scan_to_files


def fail(message: str) -> NoReturn:
    print(f"[real-device-smoke][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json(path: Path, value: dict[str, Any], *, overwrite: bool = False) -> None:
    if path.exists() and not overwrite:
        fail(f"evidence file already exists; overwrite is forbidden: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def run_command(command: list[str], label: str, *, env: dict[str, str] | None = None) -> str:
    try:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=120,
            env=env,
        )
    except (OSError, subprocess.TimeoutExpired):
        fail(f"{label} command could not complete")
    if result.returncode != 0:
        fail(f"{label} command failed with exit code {result.returncode}")
    return result.stdout.replace("\r", "")


def adb_command(adb: str, serial: str, arguments: list[str], label: str) -> str:
    return run_command([adb, "-s", serial, *arguments], label)


def verify_manifest_with_tool(project_root: Path, manifest_path: Path) -> None:
    tool = Path(__file__).with_name("real_device_acceptance_manifest.py")
    run_command(
        [
            sys.executable,
            str(tool),
            "verify",
            "--project-root",
            str(project_root),
            "--manifest",
            str(manifest_path),
        ],
        "execution manifest verification",
    )


def load_context(args: argparse.Namespace, *, require_serial: bool = True) -> tuple[Path, Path, dict[str, Any], str | None, dict[str, Any]]:
    project_root = args.project_root.resolve()
    manifest_path = args.manifest.resolve()
    verify_manifest_with_tool(project_root, manifest_path)
    manifest = load_controlled_manifest(manifest_path, project_root)
    scenarios = manifest.get("scenarios")
    scenario = next(
        (item for item in scenarios if isinstance(item, dict) and item.get("id") == args.scenario),
        None,
    ) if isinstance(scenarios, list) else None
    if scenario is None:
        fail(f"unknown Release smoke scenario: {args.scenario}")
    if scenario.get("result") is not None:
        fail(f"Release smoke scenario already recorded: {args.scenario}")
    project = manifest.get("project")
    if not isinstance(project, dict) or project.get("acceptanceVariant") != "acceptanceRelease":
        fail("manifest is not bound to the minified non-production acceptance variant")
    serial = args.serial or os.environ.get("ANDROID_SERIAL")
    if require_serial and not serial:
        fail("ANDROID_SERIAL is required; no device may be selected implicitly")
    device = manifest.get("device")
    if not isinstance(device, dict):
        fail("manifest device identity is incomplete")
    if serial and sha256_text(serial) != device.get("deviceIdHash"):
        fail("explicit device does not match the anonymous device bound to this execution")
    return project_root, manifest_path, manifest, serial, scenario


def report_directory(manifest_path: Path) -> Path:
    return manifest_path.parent


def artifact_path(project_root: Path, manifest: dict[str, Any], role: str) -> Path:
    artifacts = manifest.get("artifacts")
    entry = artifacts.get(role) if isinstance(artifacts, dict) else None
    if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
        fail(f"manifest artifact {role} is missing")
    path = (project_root / entry["path"]).resolve()
    try:
        path.relative_to(project_root)
    except ValueError:
        fail(f"manifest artifact {role} escapes project root")
    if sha256_file(path) != entry.get("sha256"):
        fail(f"manifest artifact {role} changed before smoke execution")
    return path


def scenario_context(manifest: dict[str, Any], scenario_id: str) -> dict[str, Any]:
    project = manifest["project"]
    device = manifest["device"]
    acceptance = manifest["artifacts"]["acceptanceApk"]
    return {
        "schemaVersion": 1,
        "executionId": manifest["executionId"],
        "scenarioId": scenario_id,
        "deviceIdHash": device["deviceIdHash"],
        "buildSha": project["gitSha"],
        "acceptanceApkSha256": acceptance["sha256"],
    }


def prepare(args: argparse.Namespace) -> None:
    project_root, manifest_path, manifest, serial, scenario = load_context(args)
    assert serial is not None
    apk = artifact_path(project_root, manifest, "acceptanceApk")
    package_name = manifest["project"].get("packageName")
    if package_name != "com.ytone.longcare":
        fail("acceptance package must be com.ytone.longcare")

    plan = {
        "schemaVersion": 1,
        "executionId": manifest["executionId"],
        "scenarioId": args.scenario,
        "deviceIdHash": manifest["device"]["deviceIdHash"],
        "releaseMode": "minified-acceptance-non-production",
        "productionRelease": False,
        "boundAcceptanceApk": manifest["artifacts"]["acceptanceApk"]["path"],
        "operations": [
            "verify all manifest artifact hashes",
            "re-run API 36 physical device preflight",
            "install only the manifest-bound acceptance APK",
            "clear logcat immediately before the scenario",
            "force-stop and launch the target package",
            "record process id and UTC time window",
            "capture only a sanitized target-process log window",
        ],
        "supplementalConnectedHelper": "scripts/quality/run_connected_instrumentation_suite.sh",
        "globalSecuritySettingMutations": [],
        "productionComponentsCreated": [],
    }
    if args.dry_run:
        print(json.dumps(plan, indent=2))
        print("[real-device-smoke][PASS] dry-run plan contains no device command execution.", file=sys.stderr)
        return

    execution_dir = report_directory(manifest_path)
    preflight_report = execution_dir / "preflight" / f"{args.scenario}-prepare.json"
    preflight_tool = Path(__file__).with_name("preflight_real_device.py")
    run_command(
        [
            sys.executable,
            str(preflight_tool),
            "--adb",
            args.adb,
            "--serial",
            serial,
            "--output",
            str(preflight_report),
        ],
        "physical device preflight",
    )
    adb_command(args.adb, serial, ["install", "-r", str(apk)], "bound acceptance APK installation")
    adb_command(args.adb, serial, ["logcat", "-c"], "log window reset")
    adb_command(args.adb, serial, ["shell", "am", "force-stop", package_name], "target package force-stop")
    started_at = now()
    adb_command(
        args.adb,
        serial,
        ["shell", "monkey", "-p", package_name, "-c", "android.intent.category.LAUNCHER", "1"],
        "target package launch",
    )
    process_id = adb_command(
        args.adb,
        serial,
        ["shell", "pidof", package_name],
        "target process lookup",
    ).strip().split()
    if not process_id or not process_id[0].isdigit():
        fail("target process did not start")
    session = {
        **scenario_context(manifest, args.scenario),
        "startedAt": started_at,
        "endedAt": None,
        "processId": int(process_id[0]),
        "processAliveAtCapture": None,
        "expectedTargetNode": scenario.get("expectedTargetNode"),
        "actionCount": len(scenario.get("actions", [])),
    }
    session_path = execution_dir / "sessions" / f"{args.scenario}.json"
    write_json(session_path, session)
    verify_manifest_with_tool(project_root, manifest_path)
    print(
        f"[real-device-smoke][PASS] prepared {args.scenario}; complete the registered actions and target "
        f"{scenario.get('expectedTargetNode')} before finish."
    )


def finish(args: argparse.Namespace) -> None:
    project_root, manifest_path, manifest, serial, _ = load_context(args)
    assert serial is not None
    execution_dir = report_directory(manifest_path)
    session_path = execution_dir / "sessions" / f"{args.scenario}.json"
    try:
        session = json.loads(session_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read prepared scenario session: {error}")
    if not isinstance(session, dict) or session.get("endedAt") is not None:
        fail("scenario session is missing or already captured")
    process_id = session.get("processId")
    if not isinstance(process_id, int) or process_id <= 0:
        fail("scenario session processId is invalid")
    package_name = manifest["project"]["packageName"]
    raw_log = adb_command(
        args.adb,
        serial,
        ["logcat", f"--pid={process_id}", "-d", "-v", "threadtime"],
        "target process log capture",
    )
    current_pid = adb_command(
        args.adb,
        serial,
        ["shell", "pidof", package_name],
        "target process final lookup",
    ).strip().split()
    process_alive = str(process_id) in current_pid
    session["endedAt"] = now()
    session["processAliveAtCapture"] = process_alive
    write_json(session_path, session, overwrite=True)

    try:
        config = json.loads(Path(__file__).with_name("real_device_acceptance.json").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read acceptance config: {error}")
    sanitized_path = execution_dir / "logs" / f"{args.scenario}.log"
    scan_path = execution_dir / "logs" / f"{args.scenario}.scan.json"
    scan_to_files(raw_log, config, sanitized_path, scan_path, project_root, serial)
    record_scenario(
        SimpleNamespace(
            project_root=project_root,
            manifest=manifest_path,
            session=session_path,
            log_scan=scan_path,
            scenario=args.scenario,
            status=args.status,
            completed_action=args.completed_action,
            target_node=args.target_node,
            blocker=None,
            failure_reason=args.failure_reason,
        )
    )
    verify_manifest_with_tool(project_root, manifest_path)
    print(f"[real-device-smoke][PASS] captured and recorded {args.scenario}={args.status}")


def block(args: argparse.Namespace) -> None:
    project_root, manifest_path, manifest, _, _ = load_context(args, require_serial=False)
    execution_dir = report_directory(manifest_path)
    timestamp = now()
    session = {
        **scenario_context(manifest, args.scenario),
        "startedAt": timestamp,
        "endedAt": timestamp,
        "processId": None,
        "processAliveAtCapture": None,
    }
    session_path = execution_dir / "sessions" / f"{args.scenario}.json"
    write_json(session_path, session)
    config = json.loads(Path(__file__).with_name("real_device_acceptance.json").read_text(encoding="utf-8"))
    sanitized_path = execution_dir / "logs" / f"{args.scenario}.log"
    scan_path = execution_dir / "logs" / f"{args.scenario}.scan.json"
    scan_to_files("", config, sanitized_path, scan_path, project_root, None)
    record_scenario(
        SimpleNamespace(
            project_root=project_root,
            manifest=manifest_path,
            session=session_path,
            log_scan=scan_path,
            scenario=args.scenario,
            status="blocked",
            completed_action=args.completed_action,
            target_node=None,
            blocker=args.blocker,
            failure_reason=None,
        )
    )
    print(f"[real-device-smoke][PASS] recorded {args.scenario}=blocked without installing an APK")


def connected_helper(args: argparse.Namespace) -> None:
    project_root, manifest_path, _, serial, _ = load_context(args)
    assert serial is not None
    if args.dry_run:
        print(
            json.dumps(
                {
                    "helper": "scripts/quality/run_connected_instrumentation_suite.sh",
                    "scope": "supplemental-debug-test-apks-only",
                    "acceptanceVerdict": False,
                    "device": "<explicit-serial>",
                },
                indent=2,
            )
        )
        return
    env = dict(os.environ)
    env["ANDROID_SERIAL"] = serial
    run_command(
        ["bash", str(project_root / "scripts/quality/run_connected_instrumentation_suite.sh")],
        "supplemental connected instrumentation suite",
        env=env,
    )
    verify_manifest_with_tool(project_root, manifest_path)
    print("[real-device-smoke][PASS] supplemental connected suite completed; no Release verdict was inferred.")


def common(subparser: argparse.ArgumentParser, *, scenario: bool = True) -> None:
    subparser.add_argument("--project-root", type=Path, default=Path.cwd())
    subparser.add_argument("--manifest", required=True, type=Path)
    if scenario:
        subparser.add_argument("--scenario", required=True)
    subparser.add_argument("--serial")
    subparser.add_argument("--adb", default="adb")


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser("prepare")
    common(prepare_parser)
    prepare_parser.add_argument("--dry-run", action="store_true")
    prepare_parser.set_defaults(handler=prepare)

    finish_parser = subparsers.add_parser("finish")
    common(finish_parser)
    finish_parser.add_argument("--status", required=True, choices=("passed", "failed"))
    finish_parser.add_argument("--completed-action", action="append", type=int)
    finish_parser.add_argument("--target-node")
    finish_parser.add_argument("--failure-reason")
    finish_parser.set_defaults(handler=finish)

    block_parser = subparsers.add_parser("block")
    common(block_parser)
    block_parser.add_argument("--blocker", action="append", required=True)
    block_parser.add_argument("--completed-action", action="append", type=int)
    block_parser.set_defaults(handler=block)

    helper_parser = subparsers.add_parser("connected-helper")
    common(helper_parser, scenario=False)
    helper_parser.add_argument("--scenario", default="login")
    helper_parser.add_argument("--dry-run", action="store_true")
    helper_parser.set_defaults(handler=connected_helper)

    args = parser.parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
