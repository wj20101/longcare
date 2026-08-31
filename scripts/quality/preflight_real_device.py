#!/usr/bin/env python3
"""Fail-closed preflight for a qualified LongCare API 36 physical device."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, NoReturn


def fail(message: str) -> NoReturn:
    print(f"[real-device-preflight][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read {label} {path}: {error}")
    if not isinstance(value, dict):
        fail(f"{label} root must be an object")
    return value


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def require_non_empty(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} is missing or empty")
    return value.strip()


def run_adb(adb: str, serial: str | None, arguments: list[str]) -> str:
    command = [adb]
    if serial is not None:
        command.extend(["-s", serial])
    command.extend(arguments)
    try:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        fail(f"adb command failed before qualification: {' '.join(arguments[:2])}")
    if result.returncode != 0:
        fail(f"adb command returned {result.returncode} before qualification: {' '.join(arguments[:2])}")
    return result.stdout.replace("\r", "")


def connected_serials_from_adb(adb: str) -> list[str]:
    output = run_adb(adb, None, ["devices"])
    serials: list[str] = []
    for line in output.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            serials.append(fields[0])
    return serials


def collect_snapshot(adb: str, serial: str) -> dict[str, Any]:
    properties = {
        name: run_adb(adb, serial, ["shell", "getprop", name]).strip()
        for name in (
            "ro.kernel.qemu",
            "ro.build.version.sdk",
            "ro.product.cpu.abi",
            "ro.build.fingerprint",
            "ro.product.model",
        )
    }
    return {
        "selectedSerial": serial,
        "connectedSerials": connected_serials_from_adb(adb),
        "properties": properties,
        "cpuPresent": run_adb(adb, serial, ["shell", "cat", "/sys/devices/system/cpu/present"]).strip(),
        "cpuInfo": run_adb(adb, serial, ["shell", "cat", "/proc/cpuinfo"]),
        "batteryDump": run_adb(adb, serial, ["shell", "dumpsys", "battery"]),
        "thermalDump": run_adb(adb, serial, ["shell", "dumpsys", "thermalservice"]),
    }


def parse_cpu_cores(snapshot: dict[str, Any]) -> int | None:
    cpu_present = snapshot.get("cpuPresent")
    if isinstance(cpu_present, str):
        match = re.fullmatch(r"\s*(\d+)(?:-(\d+))?\s*", cpu_present)
        if match:
            start = int(match.group(1))
            end = int(match.group(2) or match.group(1))
            if end >= start:
                return end - start + 1
    cpu_info = snapshot.get("cpuInfo")
    if isinstance(cpu_info, str):
        processors = re.findall(r"(?mi)^\s*processor\s*:\s*\d+\s*$", cpu_info)
        if processors:
            return len(processors)
    return None


def first_int(patterns: tuple[str, ...], text: str) -> int | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE | re.MULTILINE)
        if match:
            return int(match.group(1))
    return None


def first_bool(patterns: tuple[str, ...], text: str) -> bool | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE | re.MULTILINE)
        if match:
            return match.group(1).lower() == "true"
    return None


def parse_battery(text: Any) -> tuple[int | None, int | None, bool | None]:
    if not isinstance(text, str):
        return None, None, None
    level = first_int((r"^\s*level\s*:\s*(\d+)\s*$", r"\bmLevel\s*=\s*(\d+)\b"), text)
    status = first_int((r"^\s*status\s*:\s*(\d+)\s*$", r"\bmStatus\s*=\s*(\d+)\b"), text)
    powered_values = [
        first_bool((r"^\s*AC powered\s*:\s*(true|false)\s*$", r"\bmAcOnline\s*=\s*(true|false)\b"), text),
        first_bool((r"^\s*USB powered\s*:\s*(true|false)\s*$", r"\bmUsbOnline\s*=\s*(true|false)\b"), text),
        first_bool((r"^\s*Wireless powered\s*:\s*(true|false)\s*$", r"\bmWirelessOnline\s*=\s*(true|false)\b"), text),
    ]
    known_powered = [value for value in powered_values if value is not None]
    powered = any(known_powered) if known_powered else None
    return level, status, powered


def parse_thermal_status(text: Any) -> int | None:
    if not isinstance(text, str):
        return None
    return first_int(
        (
            r"^\s*Thermal Status\s*:\s*(\d+)\s*$",
            r"^\s*Status\s*:\s*(\d+)\s*$",
            r"\bmStatus\s*=\s*(\d+)\b",
            r"\bcurrent\s+status\s*[:=]\s*(\d+)\b",
        ),
        text,
    )


def validate_snapshot(snapshot: dict[str, Any], policy: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    selected = snapshot.get("selectedSerial")
    if not isinstance(selected, str) or not selected:
        fail("ANDROID_SERIAL is required; no device may be selected implicitly")
    connected = snapshot.get("connectedSerials")
    if not isinstance(connected, list) or not all(isinstance(item, str) for item in connected):
        fail("connected device inventory is missing or malformed")
    if selected not in connected:
        fail("the explicitly selected device is not online")

    properties = snapshot.get("properties")
    if not isinstance(properties, dict):
        fail("device properties are missing")
    for property_name in policy.get("requiredProperties", []):
        if property_name not in properties or not isinstance(properties.get(property_name), str):
            fail(f"device property {property_name} was not collected")
        # Physical Android builds commonly expose ro.kernel.qemu as an empty value.
        # Only the explicit value "1" is an emulator signal; the read itself is mandatory.
        if property_name != "ro.kernel.qemu":
            require_non_empty(properties.get(property_name), f"device property {property_name}")

    serial_hash = sha256_text(selected)
    qemu = str(properties.get("ro.kernel.qemu", "")).strip()
    api_text = str(properties.get("ro.build.version.sdk", "")).strip()
    abi = str(properties.get("ro.product.cpu.abi", "")).strip()
    fingerprint = str(properties.get("ro.build.fingerprint", "")).strip()
    model = str(properties.get("ro.product.model", "")).strip()
    try:
        api_level = int(api_text)
    except ValueError:
        fail("device API level is not an integer")
    cpu_cores = parse_cpu_cores(snapshot)
    battery_level, battery_status, powered = parse_battery(snapshot.get("batteryDump"))
    thermal_status = parse_thermal_status(snapshot.get("thermalDump"))

    errors: list[str] = []
    if qemu == "1":
        errors.append("deviceType: emulator/qemu devices cannot provide physical acceptance evidence")
    if api_level != policy.get("requiredApiLevel"):
        suffix = "; API 29 or earlier cannot guarantee TTFD evidence" if api_level <= 29 else ""
        errors.append(
            f"apiLevel: expected {policy.get('requiredApiLevel')}, got {api_level}{suffix}"
        )
    if abi != policy.get("requiredPrimaryAbi"):
        errors.append(f"primaryAbi: expected {policy.get('requiredPrimaryAbi')}, got {abi}")
    if cpu_cores is None:
        errors.append("cpuCores: unable to parse CPU topology")
    elif cpu_cores < int(policy.get("minimumCpuCores", 1)):
        errors.append(
            f"cpuCores: expected >= {policy.get('minimumCpuCores')}, got {cpu_cores}"
        )
    if battery_level is None or battery_status is None or powered is None:
        errors.append("battery: unable to parse level, status, and charging source")
    else:
        if battery_level < int(policy.get("minimumBatteryPercent", 0)):
            errors.append(
                f"battery.level: expected >= {policy.get('minimumBatteryPercent')}%, got {battery_level}%"
            )
        if policy.get("requireNotCharging") is True and (powered or battery_status == 2):
            errors.append("battery.charging: device must be unplugged and not charging")
    if thermal_status is None:
        errors.append("thermalStatus: unable to parse thermal service status")
    elif thermal_status > int(policy.get("maximumThermalStatus", 0)):
        errors.append(
            f"thermalStatus: expected <= {policy.get('maximumThermalStatus')}, got {thermal_status}"
        )

    report = {
        "schemaVersion": 1,
        "status": "qualified" if not errors else "rejected",
        "deviceIdHash": serial_hash,
        "deviceType": "emulator" if qemu == "1" else "physical",
        "apiLevel": api_level,
        "primaryAbi": abi,
        "cpuCores": cpu_cores,
        "fingerprint": fingerprint,
        "model": model,
        "battery": {
            "levelPercent": battery_level,
            "statusCode": battery_status,
            "powered": powered,
        },
        "thermalStatus": thermal_status,
        "reasons": errors,
    }
    return report, errors


def write_report(report: dict[str, Any], output: Path | None) -> None:
    payload = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if output is None:
        print(payload, end="")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(payload, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("real_device_acceptance.json"),
    )
    parser.add_argument("--snapshot", type=Path, help="Synthetic device snapshot for fixture-only validation")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="Explicit serial; defaults only to ANDROID_SERIAL")
    args = parser.parse_args()

    config = load_object(args.config.resolve(), "acceptance config")
    policy = config.get("deviceEligibility")
    if not isinstance(policy, dict):
        fail("deviceEligibility is missing from acceptance config")

    if args.snapshot is not None:
        snapshot = load_object(args.snapshot.resolve(), "device snapshot")
        if args.serial:
            snapshot["selectedSerial"] = args.serial
    else:
        serial = args.serial or os.environ.get("ANDROID_SERIAL")
        if not serial:
            connected_count = len(connected_serials_from_adb(args.adb))
            fail(
                "ANDROID_SERIAL is required; no device may be selected implicitly "
                f"(online-device-count={connected_count})"
            )
        snapshot = collect_snapshot(args.adb, serial)

    report, errors = validate_snapshot(snapshot, policy)
    write_report(report, args.output.resolve() if args.output else None)
    if errors:
        print(
            "[real-device-preflight][FAIL] device rejected before installation: " + " | ".join(errors),
            file=sys.stderr,
        )
        raise SystemExit(1)
    print(
        "[real-device-preflight][PASS] qualified API 36 physical ARM64 device; "
        f"deviceIdHash={report['deviceIdHash']}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
