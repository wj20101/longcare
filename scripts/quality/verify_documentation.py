#!/usr/bin/env python3
"""Check repository Markdown index, local links/anchors, and live tech snapshots.

Read-only, standard library only. Does not access external URLs or certify prose semantics.
Historical and dated analysis documents are link-checked but not version-rewritten.
"""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit


def markdown_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"],
        cwd=root, check=True, capture_output=True,
    )
    return sorted({Path(p.decode()) for p in result.stdout.split(b"\0") if p and (root / p.decode()).is_file()})


def prose(text: str) -> str:
    return re.sub(r"^(`{3,}|~{3,})[^\n]*\n.*?^\1\s*$", "", text, flags=re.M | re.S)


def anchors(text: str) -> set[str]:
    result = set(re.findall(r'<(?:a|h[1-6])\s+[^>]*(?:id|name)=["\']([^"\']+)', text))
    used: Counter[str] = Counter()
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", prose(text), re.M):
        heading = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", heading)
        slug = re.sub(r"[^\w\-\s]", "", heading.lower()).replace(" ", "-")
        count = used[slug]
        used[slug] += 1
        result.add(slug if count == 0 else f"{slug}-{count}")
    return result


def check_links(root: Path, files: list[Path]) -> list[str]:
    errors = []
    for relative in files:
        source = root / relative
        for match in re.finditer(r"!?\[[^\]\n]*\]\((<[^>]+>|[^\s)]+)(?:\s+[\"'][^\n]*?[\"'])?\)", prose(source.read_text())):
            target = match.group(1).strip("<>")
            url = urlsplit(target)
            if url.scheme or url.netloc:
                continue
            destination = (source.parent / unquote(url.path)).resolve() if url.path else source
            if not destination.exists():
                errors.append(f"{relative}: missing link {target}")
            elif url.fragment and destination.suffix == ".md":
                if unquote(url.fragment) not in anchors(destination.read_text()):
                    errors.append(f"{relative}: missing anchor {target}")
    return errors


def document_group(path: Path) -> str:
    name = path.as_posix()
    for prefix, group in (
        ("openspec/changes/archive/", "历史变更"),
        ("openspec/changes/", "未归档变更（完成状态见 tasks）"),
        ("openspec/specs/", "主规格"),
        (".agents/skills/", "工具技能"),
        ("docs/compliance/", "历史合规"),
        ("docs/analysis/", "分析基线"),
    ):
        if name.startswith(prefix):
            return group
    return "当前说明"


def check_index(root: Path, files: list[Path]) -> list[str]:
    index = root / "docs/README.md"
    if not index.exists():
        return ["docs/README.md: missing index"]
    linked = {index.resolve()}
    for target in re.findall(r"\[[^\]\n]*\]\(([^\s)]+)\)", prose(index.read_text())):
        url = urlsplit(target)
        if not url.scheme and not url.netloc:
            linked.add((index.parent / unquote(url.path)).resolve())
    required = []
    for path in files:
        group = document_group(path)
        if group in {"当前说明", "分析基线", "历史合规", "主规格"}:
            required.append(path)
        elif group.startswith("未归档变更") and path.name == "tasks.md":
            required.append(path)
    return [f"index: missing {path}" for path in required if (root / path).resolve() not in linked]


def check_versions(root: Path) -> list[str]:
    constants = (root / "constants.gradle.kts").read_text()
    values = dict(re.findall(r'extra\.set\("(\w+)",\s*"?([^"\)]+)"?\)', constants))
    catalog = (root / "gradle/libs.versions.toml").read_text().split("[libraries]", 1)[0]
    versions = dict(re.findall(r'^([\w]+)\s*=\s*"([^"]+)"', catalog, re.M))
    doc = (root / "docs/architecture/tech-stack.md").read_text()
    required = {
        "compileSdk": f'| `compileSdk` | {values["appCompileSdkVersion"]} |',
        "targetSdk": f'| `targetSdk` | {values["appTargetSdkVersion"]} |',
        "minSdk": f'| `minSdk` | {values["appMinSdkVersion"]} |',
        "JDK": f'| JDK / JVM toolchain | {values["appJdkVersion"]} |',
        "AGP": f'| Android Gradle Plugin | {versions["agp"]} |',
        "Kotlin": f'| Kotlin | {versions["kotlin"]} |',
        "KSP": f'| KSP | {versions["ksp"]} |',
        "CameraX": f'| Camera | CameraX | {versions["androidxCamera"]} |',
        "Compose": f'| UI | Jetpack Compose BOM | {versions["composeBom"]} |',
        "Room": f'| Persistence | Room | {versions["androidxRoom"]} |',
    }
    wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text()
    version = re.search(r"gradle-([0-9.]+)-(?:bin|all)\.zip", wrapper)
    if not version:
        return ["wrapper: cannot resolve distribution version"]
    required["Gradle"] = f'| Gradle Wrapper | {version.group(1)} |'
    return [f"tech-stack: stale {key}; expected {value}" for key, value in required.items() if value not in doc]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--list", action="store_true", help="List Markdown paths and directory-based categories; does not validate")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        files = markdown_files(root)
        if args.list:
            for path in files:
                print(f"{document_group(path)}\t{path.as_posix()}")
            print(f"[docs][LIST] {len(files)} Markdown files")
            return 0
        errors = check_links(root, files) + check_index(root, files) + check_versions(root)
    except (OSError, subprocess.CalledProcessError, KeyError, ValueError) as error:
        print(f"[docs][FAIL] unable to check: {error}", file=sys.stderr)
        return 1
    for error in errors:
        print(f"[docs][FAIL] {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"[docs][PASS] {len(files)} Markdown files: local links/anchors, index coverage, selected live versions")
    print("[docs][NOTE] External URLs, reference-style links and prose semantics require separate review.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
