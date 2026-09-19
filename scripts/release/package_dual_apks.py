"""Package exactly two Gradle-declared APKs with verified IDs and checksums."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import time


def package(root: Path, variant: str) -> Path:
    if variant not in ("debug", "release"):
        raise ValueError("Unsupported build variant")
    artifacts = []
    version = None
    for module in ("app", "assistant"):
        directory = root / module / "build/outputs/apk" / variant
        metadata = json.loads((directory / "output-metadata.json").read_text())
        expected_id = "com.ytone.longcare" + (".assistant" if module == "assistant" else "")
        if metadata["applicationId"] != expected_id or len(metadata["elements"]) != 1:
            raise ValueError(f"Unexpected package identity or split APKs: {module}")
        element = metadata["elements"][0]
        base_name = element["versionName"]
        if module == "assistant":
            if not base_name.endswith("-assistant"):
                raise ValueError("Assistant version suffix missing")
            base_name = base_name.removesuffix("-assistant")
        current_version = (base_name, element["versionCode"])
        if version is not None and version != current_version:
            raise ValueError("Formal/assistant versions differ")
        version = current_version
        if not re.fullmatch(r"[A-Za-z0-9._-]+", str(version[0])):
            raise ValueError("Unsafe version filename")
        source = (directory / element["outputFile"]).resolve()
        if source.parent != directory.resolve() or not source.is_file() or source.suffix != ".apk":
            raise ValueError(f"Missing or unsafe APK path: {module}")
        filename = f"longcare-{module}-v{version[0]}-{version[1]}-{variant}.apk"
        artifacts.append((source, filename, expected_id))
    parent = root / "build/outputs/dual-apk"
    parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{variant}-", dir=parent))
    checksums = []
    summary = []
    for source, filename, app_id in artifacts:
        target = stage / filename
        shutil.copy2(source, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        checksums.append(f"{digest}  {filename}\n")
        summary.append({"file": filename, "applicationId": app_id, "sha256": digest})
    (stage / "SHA256SUMS").write_text("".join(checksums))
    (stage / "artifacts.json").write_text(json.dumps({"variant": variant, "versionName": version[0], "versionCode": version[1], "artifacts": summary}, indent=2) + "\n")
    output = parent / variant
    if output.exists():
        output.rename(parent / f".{variant}-previous-{time.time_ns()}")
    stage.rename(output)
    return output


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Usage: package_dual_apks.py ROOT [debug|release]")
    print(package(Path(sys.argv[1]).resolve(), sys.argv[2]))
