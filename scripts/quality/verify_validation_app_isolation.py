"""Keep in-app card diagnostics local and reject retired assistant entry points."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = re.compile(
    r"LoginValidationEntry|FaceVerificationValidationActivity|NfcValidationActivity|"
    r"NfcValidationScreen|NfcTestHelper|NfcTestEntrySession|NfcTestConfig|"
    r"showValidationEntrySheet|onMainLogoLongPress|login_validation_entry|face_validation_"
)


def verify(root: Path, variant: str | None = None) -> list[str]:
    errors = []
    registered = (root / "settings.gradle.kts").read_text()
    included = {name for block in re.findall(r'include\(([^)]*)\)', registered)
                for name in re.findall(r'"([^"]+)"', block)}
    for module in (":app", ":feature:carddiagnostics", ":integration:txface"):
        if module not in included:
            errors.append(f"Missing registered module: {module}")
    if ":assistant" in included or (root / "assistant/build.gradle.kts").exists():
        errors.append("Retired assistant module remains")
    for source_set in ("main", "debug", "release"):
        for path in (root / "app/src" / source_set).rglob("*"):
            if path.suffix in (".kt", ".xml") and FORBIDDEN.search(path.read_text()):
                errors.append(f"Retired validation entry: {path.relative_to(root)}")
    required = (
        "feature/carddiagnostics/src/main/kotlin/com/ytone/longcare/feature/carddiagnostics/CardDiagnosticsScreen.kt",
        "app/src/main/kotlin/com/ytone/longcare/platform/nfc/CardDiagnosticsContent.kt",
        "app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt",
        "feature/photoupload/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt",
        "feature/identification/src/main/kotlin/com/ytone/longcare/features/face/ui/ManualFaceCaptureScreen.kt",
        "core/ui/src/main/kotlin/com/ytone/longcare/platform/face/FaceSdkUiController.kt",
        "integration/txface/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt",
    )
    for name in required:
        if not (root / name).is_file():
            errors.append(f"Missing owner source: {name}")
    build = (root / "app/build.gradle.kts").read_text()
    if not re.search(r'applicationId\s*=\s*"com\.ytone\.longcare"', build):
        errors.append("Unexpected main applicationId")
    if re.search(r'(?:project|projectDependency)\(":assistant"\)', build):
        errors.append("Forbidden dependency on retired assistant")
    diagnostics = list((root / "feature/carddiagnostics/src/main").rglob("*.kt"))
    diagnostics += [root / name for name in required[1:3] if (root / name).exists()]
    diagnostics += list((root / "app/src/main/kotlin/com/ytone/longcare/platform/nfc").glob("CardDiagnostics*.kt"))
    for path in diagnostics:
        if re.search(r"AppEventBus|TagScanned|NfcManager|Repository|retrofit|core\.data|features\.nfc\.vm", path.read_text()):
            errors.append(f"Diagnostics must not enter business or network chain: {path.relative_to(root)}")
    for directory in ("app", "core", "feature"):
        for path in (root / directory).rglob("*.kt"):
            if "build" in path.parts or "test" in path.parts or "androidTest" in path.parts:
                continue
            if re.search(r"import com\.tencent\.cloud\.huiyansdkface", path.read_text()):
                errors.append(f"Vendor implementation outside integration: {path.relative_to(root)}")
    manifests = list((root / "app/src").glob("*/AndroidManifest.xml"))
    if variant:
        merged = root / f"app/build/intermediates/merged_manifest/{variant}/process{variant.title()}MainManifest/AndroidManifest.xml"
        if not merged.exists():
            errors.append(f"Missing merged manifest: app {variant}")
        else:
            manifests.append(merged)
    for manifest in manifests:
        tree = ET.parse(manifest).getroot()
        if manifest.name == "AndroidManifest.xml" and "build" in manifest.parts:
            if tree.get("package") != "com.ytone.longcare":
                errors.append("Unexpected merged applicationId")
        for component in tree.findall("application/*"):
            name = component.get(ANDROID + "name", "")
            if (name.startswith(".") or name.startswith("com.ytone.longcare.")) and any(
                    part in name.lower() for part in ("assistant", "validation", "diagnostics")):
                errors.append(f"Unexpected diagnostic component: {name}")
    return errors


if __name__ == "__main__":
    failures = verify(Path(sys.argv[1]).resolve(), sys.argv[2] if len(sys.argv) > 2 else None)
    for failure in failures:
        print(f"[validation-isolation][FAIL] {failure}", file=sys.stderr)
    if failures:
        sys.exit(1)
    print("[validation-isolation][PASS] local card diagnostics are isolated from business flows")
