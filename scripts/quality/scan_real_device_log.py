#!/usr/bin/env python3
"""Scan and redact a target-package log window before it becomes evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, NoReturn


SIGNATURE_PATTERNS = {
    "ClassNotFoundException": r"\bClassNotFoundException\b",
    "NoSuchMethodException": r"\bNoSuchMethodException\b",
    "NoSuchMethodError": r"\bNoSuchMethodError\b",
    "UnsatisfiedLinkError": r"\bUnsatisfiedLinkError\b",
    "serialization restoration error": (
        r"\bserialization restoration error\b|\bBadParcelableException\b|"
        r"(?:SavedState|Parcel).{0,80}(?:restore|unmarshall|deserialize).{0,40}(?:error|fail|exception)"
    ),
    "FATAL EXCEPTION": r"\bFATAL EXCEPTION\b",
    "ANR in com.ytone.longcare": r"\bANR in com\.ytone\.longcare\b",
    "Process com.ytone.longcare has died": r"\bProcess com\.ytone\.longcare has died\b",
    "REAL_DEVICE_ACCEPTANCE_TIMEOUT": r"\bREAL_DEVICE_ACCEPTANCE_TIMEOUT\b",
}


def fail(message: str) -> NoReturn:
    print(f"[real-device-log][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def redact(raw: str, serial: str | None = None) -> str:
    text = raw
    if serial:
        text = text.replace(serial, "<redacted-serial>")
    text = re.sub(
        r"(?i)\b(authorization\s*:\s*bearer|bearer)\s+[A-Za-z0-9._~+/=-]+",
        r"\1 <redacted-token>",
        text,
    )
    text = re.sub(
        r"(?i)\b(token|access_token|refresh_token|password|verification[_-]?code|验证码|"
        r"account|username|mobile|phone|idcard|identity[_-]?card)\s*[:=]\s*([^\s,&;]+)",
        r"\1=<redacted>",
        text,
    )
    text = re.sub(r"(?<!\d)1[3-9]\d{9}(?!\d)", "<redacted-phone>", text)
    text = re.sub(r"(?<![0-9A-Za-z])\d{17}[0-9Xx](?![0-9A-Za-z])", "<redacted-id-card>", text)
    text = re.sub(
        r"(https?://[^\s?#]+)\?[^\s#]*",
        r"\1?<redacted-query>",
        text,
        flags=re.IGNORECASE,
    )
    return text


def scan_text(raw: str, forbidden_signatures: list[str], serial: str | None = None) -> tuple[str, list[str]]:
    sanitized = redact(raw, serial)
    matches: list[str] = []
    for signature in forbidden_signatures:
        pattern = SIGNATURE_PATTERNS.get(signature, re.escape(signature))
        if re.search(pattern, raw, flags=re.IGNORECASE | re.DOTALL):
            matches.append(signature)
    return sanitized, matches


def controlled_relative(path: Path, project_root: Path, label: str) -> str:
    try:
        return path.resolve().relative_to(project_root.resolve()).as_posix()
    except ValueError:
        fail(f"{label} must remain under project root")


def scan_to_files(
    raw: str,
    config: dict[str, Any],
    sanitized_output: Path,
    result_output: Path,
    project_root: Path,
    serial: str | None,
) -> dict[str, Any]:
    signatures = config.get("forbiddenLogSignatures")
    if not isinstance(signatures, list) or not all(isinstance(item, str) for item in signatures):
        fail("acceptance config forbiddenLogSignatures is incomplete")
    sanitized, matches = scan_text(raw, signatures, serial)
    sanitized_output.parent.mkdir(parents=True, exist_ok=True)
    sanitized_output.write_text(sanitized, encoding="utf-8")
    log_hash = sha256_file(sanitized_output)
    result = {
        "schemaVersion": 1,
        "status": "passed" if not matches else "failed",
        "forbiddenMatches": matches,
        "sanitizedLog": {
            "path": controlled_relative(sanitized_output, project_root, "sanitized log"),
            "sha256": log_hash,
            "sizeBytes": sanitized_output.stat().st_size,
        },
        "redactionPolicy": "sanitized-target-window-only",
    }
    result_output.parent.mkdir(parents=True, exist_ok=True)
    result_output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--sanitized-output", required=True, type=Path)
    parser.add_argument("--result-output", required=True, type=Path)
    parser.add_argument("--project-root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--config",
        type=Path,
        default=Path(__file__).with_name("real_device_acceptance.json"),
    )
    parser.add_argument("--serial")
    args = parser.parse_args()
    try:
        config = json.loads(args.config.read_text(encoding="utf-8"))
        raw = args.input.read_text(encoding="utf-8", errors="replace")
    except (OSError, json.JSONDecodeError) as error:
        fail(f"cannot read scanner input: {error}")
    if not isinstance(config, dict):
        fail("acceptance config root must be an object")
    result = scan_to_files(
        raw,
        config,
        args.sanitized_output.resolve(),
        args.result_output.resolve(),
        args.project_root.resolve(),
        args.serial,
    )
    if result["status"] != "passed":
        fail("forbidden runtime signatures detected: " + ", ".join(result["forbiddenMatches"]))
    print("[real-device-log][PASS] target log window sanitized with no forbidden runtime signatures.")


if __name__ == "__main__":
    main()
