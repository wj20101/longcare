#!/usr/bin/env python3
"""Reject secrets, PII, host paths, raw serials, photos, and tracked device reports."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import NoReturn


def fail(message: str) -> NoReturn:
    print(f"[real-device-report-privacy][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def controlled_report_root(project_root: Path, report_root: Path) -> Path:
    allowed = (project_root / "build/reports/real-device-acceptance").resolve()
    resolved = report_root.resolve()
    try:
        resolved.relative_to(allowed)
    except ValueError:
        fail("report root must remain under build/reports/real-device-acceptance")
    if not resolved.is_dir():
        fail(f"report root does not exist: {resolved.name}")
    return resolved


def scan_text(text: str, relative: str, raw_serial: str | None) -> list[str]:
    findings: list[str] = []
    checks = (
        (
            "credential-or-account-value",
            r"(?i)(?:authorization\s*:\s*bearer\s+(?!<redacted)[^\s]+|"
            r"(?:token|access_token|refresh_token|password|verification[_-]?code|验证码|"
            r"account|username|mobile|phone|idcard|identity[_-]?card)\s*[:=]\s*"
            r"(?!<redacted)[^\s,}\]]+)",
        ),
        ("phone-number", r"(?<!\d)1[3-9]\d{9}(?!\d)"),
        ("identity-card", r"(?<![0-9A-Za-z])\d{17}[0-9Xx](?![0-9A-Za-z])"),
        ("host-absolute-path", r"(?:/Users/[^\s\"']+|/home/[^\s\"']+|[A-Za-z]:\\Users\\[^\s\"']+)"),
        ("full-url-query", r"https?://[^\s\"?#]+\?(?!<redacted-query>)[^\s\"#]+"),
        ("plain-serial-field", r"(?i)[\"']?(?:device)?serial(?:number)?[\"']?\s*[:=]\s*[\"']?(?!<redacted)[A-Za-z0-9._:-]+"),
        ("emulator-serial", r"\bemulator-\d+\b"),
    )
    for label, pattern in checks:
        if re.search(pattern, text):
            findings.append(f"{relative}: {label}")
    if raw_serial and raw_serial in text:
        findings.append(f"{relative}: explicit raw serial")
    return findings


def git_tracking_guard(project_root: Path) -> None:
    if not (project_root / ".git").exists():
        return
    try:
        result = subprocess.run(
            ["git", "-C", str(project_root), "ls-files", "--", "build"],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        fail("cannot execute Git report tracking guard")
    if result.returncode != 0:
        fail("cannot execute Git report tracking guard")
    tracked = [line for line in result.stdout.splitlines() if line.strip()]
    if tracked:
        fail(f"build reports or outputs must not be tracked by Git: {tracked[:5]}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path, default=Path.cwd())
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--manifest", type=Path)
    group.add_argument("--report-root", type=Path)
    parser.add_argument("--serial")
    args = parser.parse_args()

    project_root = args.project_root.resolve()
    if args.manifest is not None:
        manifest = args.manifest.resolve()
        report_root = manifest.parent
        tool = Path(__file__).with_name("real_device_acceptance_manifest.py")
        result = subprocess.run(
            [
                sys.executable,
                str(tool),
                "verify",
                "--project-root",
                str(project_root),
                "--manifest",
                str(manifest),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail("execution manifest schema/hash verification failed before privacy scan")
    else:
        report_root = args.report_root.resolve()
    report_root = controlled_report_root(project_root, report_root)

    findings: list[str] = []
    files = sorted(report_root.rglob("*"))
    if not files:
        fail("report root is empty")
    for path in files:
        relative = path.relative_to(project_root).as_posix()
        if path.is_symlink():
            findings.append(f"{relative}: symlink is forbidden")
            continue
        if path.is_dir():
            continue
        lowered = path.name.lower()
        if lowered.endswith((".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif")):
            findings.append(f"{relative}: photo/image artifact is forbidden")
            continue
        if ".raw.log" in lowered or "raw-log" in lowered or "logcat-raw" in lowered:
            findings.append(f"{relative}: raw log artifact is forbidden")
        if path.suffix.lower() not in {".json", ".log"}:
            findings.append(f"{relative}: unsupported report file type")
            continue
        if path.stat().st_size > 25 * 1024 * 1024:
            findings.append(f"{relative}: report exceeds 25 MiB privacy scan limit")
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            findings.append(f"{relative}: report is not UTF-8 text")
            continue
        findings.extend(scan_text(text, relative, args.serial))

    git_tracking_guard(project_root)
    if findings:
        for finding in findings:
            print(f"[real-device-report-privacy][FAIL] {finding}", file=sys.stderr)
        raise SystemExit(1)
    print(
        "[real-device-report-privacy][PASS] report tree contains only sanitized text evidence; "
        "no credentials, PII, host paths, raw serials, URL queries, photos, or tracked build files."
    )


if __name__ == "__main__":
    main()
