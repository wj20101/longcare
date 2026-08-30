#!/usr/bin/env python3
"""Fail-closed governance for project-owned Release R8 rules."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


GLOBAL_DISABLES = {
    "-dontshrink",
    "-dontoptimize",
    "-dontobfuscate",
}

REQUIRED_RUNTIME_RULES = {
    "-keep,allowoptimization,allowshrinking,allowobfuscation class "
    "com.ytone.longcare.model.result.ApiResult": (
        "Retrofit suspend ApiResult<T> generic signature rule"
    ),
}

REMOVED_RULE_FINGERPRINTS = {
    "-keepclasseswithmembernames class *{native <methods>;}": "generic native keep",
    "-keepclassmembers enum *{public static **[] values();public static ** valueOf(java.lang.String);}": "generic enum keep",
    "-keep @androidx.annotation.Keep class *{*;}": "AndroidX @Keep class rule",
    "-keepclasseswithmembers class *{@androidx.annotation.Keep <fields>;}": "AndroidX @Keep field rule",
    "-keepclasseswithmembers class *{@androidx.annotation.Keep <methods>;}": "AndroidX @Keep method rule",
    "-keepclassmembers class kotlinx.serialization.internal.*{*;}": "Kotlinx Serialization internal member rule",
    "-keepclassmembers class **$$serializer{*;}": "Kotlinx serializer member rule",
    "-keep class **$$serializer{*;}": "Kotlinx serializer class rule",
    "-keepclassmembers class *{@kotlinx.serialization.Serializable <fields>;@kotlinx.serialization.Transient <fields>;}": "Kotlinx annotation field rule",
    "-keepnames class *{@kotlinx.serialization.Serializable <methods>;}": "Kotlinx global class-name rule",
    "-keep class com.autonavi.aps.amapapi.model.**{*;}": "subsumed AMap model rule",
    "-keep class com.comm.*{*;}": "subsumed QLZ communication rule",
    "-keep class com.evenmed.util.**{*;}": "unused QLZ utility rule",
    "-keep class com.falth.data.*{*;}": "subsumed QLZ data rule",
    "-keep class com.evenmed.sdk.chekpage.TreatmentBaseAct{*;}": "unused QLZ TreatmentBaseAct rule",
}


@dataclass(frozen=True)
class Directive:
    line: int
    rule: str


class VerificationInputError(ValueError):
    pass


def canonicalize_rule(rule: str) -> str:
    normalized = re.sub(r"\s+", " ", rule.strip())
    normalized = re.sub(r"\s*([{},;])\s*", r"\1", normalized)
    return normalized


def parse_directives(path: Path) -> list[Directive]:
    directives: list[Directive] = []
    active_parts: list[str] = []
    active_line = 0
    brace_depth = 0

    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        content = raw_line.split("#", 1)[0].strip()
        if not content:
            continue

        if not active_parts:
            if not content.startswith("-"):
                continue
            active_parts = [content]
            active_line = line_number
            brace_depth = content.count("{") - content.count("}")
        else:
            active_parts.append(content)
            brace_depth += content.count("{") - content.count("}")

        if brace_depth <= 0:
            directives.append(
                Directive(active_line, canonicalize_rule(" ".join(active_parts)))
            )
            active_parts = []
            active_line = 0
            brace_depth = 0

    if active_parts:
        raise VerificationInputError(
            f"{path}: unterminated R8 directive starting at line {active_line}"
        )
    return directives


def is_package_wide_keep(rule: str) -> bool:
    header = rule.split("{", 1)[0]
    return bool(
        re.match(
            r"^-keep(?:,[^\s]+)*\s+(?:(?:public|protected|private|abstract|final)\s+)*"
            r"(?:class|interface|enum|@interface)\s+[^\s{]*\.\*\*$",
            header,
        )
    )


def load_allowlist(path: Path) -> dict[str, tuple[str, str]]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise VerificationInputError(f"{path}: invalid allowlist JSON: {exc}") from exc

    if payload.get("version") != 1 or not isinstance(payload.get("rules"), list):
        raise VerificationInputError(
            f"{path}: allowlist must contain version=1 and a rules array"
        )

    allowlist: dict[str, tuple[str, str]] = {}
    for index, entry in enumerate(payload["rules"], start=1):
        if not isinstance(entry, dict):
            raise VerificationInputError(f"{path}: rules[{index}] must be an object")
        raw_rule = entry.get("rule")
        owner = entry.get("owner")
        reason = entry.get("reason")
        if not all(isinstance(value, str) and value.strip() for value in (raw_rule, owner, reason)):
            raise VerificationInputError(
                f"{path}: rules[{index}] requires non-empty rule, owner, and reason"
            )
        rule = canonicalize_rule(raw_rule)
        if not is_package_wide_keep(rule):
            raise VerificationInputError(
                f"{path}: rules[{index}] is not a package-wide -keep rule: {rule}"
            )
        if rule in allowlist:
            raise VerificationInputError(
                f"{path}: duplicate allowlist rule at rules[{index}]: {rule}"
            )
        allowlist[rule] = (owner.strip(), reason.strip())
    return allowlist


def extract_braced_block(text: str, opening_brace: int) -> str:
    depth = 0
    state = "code"
    index = opening_brace
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""
        if state == "line-comment":
            if char == "\n":
                state = "code"
        elif state == "block-comment":
            if char == "*" and next_char == "/":
                state = "code"
                index += 1
        elif state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        else:
            if char == "/" and next_char == "/":
                state = "line-comment"
                index += 1
            elif char == "/" and next_char == "*":
                state = "block-comment"
                index += 1
            elif char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return text[opening_brace : index + 1]
        index += 1
    raise VerificationInputError("unterminated Kotlin block while reading Release R8 wiring")


def find_named_block(text: str, name_pattern: str, start: int = 0) -> tuple[str, int]:
    match = re.search(rf"\b{name_pattern}\s*\{{", text[start:])
    if match is None:
        raise VerificationInputError(f"missing Kotlin block: {name_pattern}")
    match_start = start + match.start()
    opening_brace = text.find("{", match_start)
    return extract_braced_block(text, opening_brace), match_start


def relative_path(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def verify_release_wiring(build_file: Path, root: Path) -> list[str]:
    display = relative_path(build_file, root)
    text = build_file.read_text(encoding="utf-8")
    try:
        build_types, build_types_start = find_named_block(text, "buildTypes")
        release, _ = find_named_block(build_types, "release")
    except VerificationInputError as exc:
        return [f"{display}: unable to resolve Release build type: {exc}"]

    del build_types_start
    errors: list[str] = []
    required_patterns = (
        (
            r"getDefaultProguardFile\s*\(\s*\"proguard-android-optimize\.txt\"\s*\)",
            "optimized Android default rules",
        ),
        (r"\"proguard-rules\.pro\"", "project rules"),
        (
            r"\"txkyc-face-consumer-proguard-rules\.pro\"",
            "Tencent face rules",
        ),
    )
    if not re.search(r"\bproguardFiles\s*\(", release):
        errors.append(f"{display}: Release build type is missing proguardFiles(...)")
    for pattern, label in required_patterns:
        if not re.search(pattern, release):
            errors.append(f"{display}: Release proguardFiles is missing {label}")
    return errors


def verify_project(root: Path) -> tuple[list[str], int]:
    proguard_path = root / "app/proguard-rules.pro"
    build_file = root / "app/build.gradle.kts"
    txkyc_path = root / "app/txkyc-face-consumer-proguard-rules.pro"
    allowlist_path = root / "scripts/quality/project_r8_package_keep_allowlist.json"

    required_files = (proguard_path, build_file, txkyc_path, allowlist_path)
    missing = [path for path in required_files if not path.is_file()]
    if missing:
        return (
            [
                f"{relative_path(path, root)}: required project R8 governance input is missing"
                for path in missing
            ],
            0,
        )

    directives = parse_directives(proguard_path)
    directive_rules = {directive.rule for directive in directives}
    allowlist = load_allowlist(allowlist_path)
    errors = verify_release_wiring(build_file, root)
    broad_directives: dict[str, Directive] = {}
    proguard_display = relative_path(proguard_path, root)

    for directive in directives:
        if directive.rule in GLOBAL_DISABLES:
            errors.append(
                f"{proguard_display}:{directive.line}: forbidden global directive "
                f"'{directive.rule}'"
            )
        fingerprint = REMOVED_RULE_FINGERPRINTS.get(directive.rule)
        if fingerprint is not None:
            errors.append(
                f"{proguard_display}:{directive.line}: removed rule fingerprint "
                f"'{fingerprint}' reintroduced: {directive.rule}"
            )
        if is_package_wide_keep(directive.rule):
            previous = broad_directives.get(directive.rule)
            if previous is not None:
                errors.append(
                    f"{proguard_display}:{directive.line}: duplicate package-wide keep "
                    f"(first declared at line {previous.line}): {directive.rule}"
                )
            else:
                broad_directives[directive.rule] = directive

    for rule, label in REQUIRED_RUNTIME_RULES.items():
        if rule not in directive_rules:
            errors.append(
                f"{proguard_display}: required runtime rule '{label}' is missing: {rule}"
            )

    for rule, directive in sorted(broad_directives.items(), key=lambda item: item[1].line):
        if rule not in allowlist:
            errors.append(
                f"{proguard_display}:{directive.line}: package-wide keep is not allowlisted: {rule}"
            )

    allowlist_display = relative_path(allowlist_path, root)
    for rule in sorted(set(allowlist) - set(broad_directives)):
        errors.append(f"{allowlist_display}: stale package-wide keep entry: {rule}")

    return errors, len(broad_directives)


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify project-owned Release R8 rules and their allowlist."
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Project root containing app/ and scripts/quality/.",
    )
    return parser.parse_args(argv)


def main(argv: Iterable[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    root = args.project_root.expanduser().resolve()
    try:
        errors, broad_count = verify_project(root)
    except (OSError, VerificationInputError) as exc:
        errors = [str(exc)]
        broad_count = 0

    if errors:
        for error in errors:
            print(f"[project-r8][FAIL] {error}", file=sys.stderr)
        print(
            f"[project-r8][FAIL] verification failed with {len(errors)} issue(s).",
            file=sys.stderr,
        )
        return 1

    print(
        "[project-r8][PASS] Release wiring and project rules are governed; "
        f"package-wide allowlist entries={broad_count}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
