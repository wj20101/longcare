"""Fail closed when assistant-only validation leaks into the formal application."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = re.compile(
    r"LoginValidationEntry|FaceVerificationValidationActivity|NfcValidationActivity|"
    r"NfcValidationScreen|NfcTestHelper|NfcTestEntrySession|NfcTestConfig|"
    r"showValidationEntrySheet|onMainLogoLongPress|login_validation_entry|"
    r"nfc_validation_|face_validation_"
)


def verify(root: Path, variant: str | None = None) -> list[str]:
    errors = []
    settings = root / "settings.gradle.kts"
    registered = settings.read_text() if settings.exists() else ""
    included = {name for block in re.findall(r'include\(([^)]*)\)', registered)
                for name in re.findall(r'"([^"]+)"', block)}
    for module in (":app", ":assistant", ":integration:txface", ":integration:txface-live", ":integration:txface-normal"):
        if module not in included:
            errors.append(f"Missing registered module: {module}")
    for source_set in ("main", "debug", "release"):
        for path in (root / "app/src" / source_set).rglob("*"):
            if path.suffix in (".kt", ".xml") and FORBIDDEN.search(path.read_text()):
                errors.append(f"Formal app contains validation entry: {path.relative_to(root)}")
    required = (
        "assistant/src/main/kotlin/com/ytone/longcare/assistant/AssistantActivity.kt",
        "assistant/src/main/kotlin/com/ytone/longcare/assistant/AssistantRoot.kt",
        "assistant/src/main/kotlin/com/ytone/longcare/presentation/validation/nfc/NfcValidationScreen.kt",
        "feature/photoupload/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt",
        "feature/identification/src/main/kotlin/com/ytone/longcare/features/face/ui/ManualFaceCaptureScreen.kt",
        "core/ui/src/main/kotlin/com/ytone/longcare/platform/face/FaceSdkUiController.kt",
        "integration/txface/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt",
    )
    for name in required:
        if not (root / name).is_file():
            errors.append(f"Missing owner source: {name}")
    for module, forbidden in (("app", ":assistant"), ("assistant", ":app")):
        build = (root / module / "build.gradle.kts").read_text()
        expected_id = "com.ytone.longcare" + (".assistant" if module == "assistant" else "")
        if not re.search(r'applicationId\s*=\s*"' + re.escape(expected_id) + r'"', build):
            errors.append(f"Unexpected applicationId: {module}")
        if re.search(r'(?:project|projectDependency)\("' + re.escape(forbidden) + r'"\)', build):
            errors.append(f"Forbidden application dependency: {module} -> {forbidden}")
    for directory in ("app", "assistant", "core", "feature"):
        for path in (root / directory).rglob("*.kt"):
            if "build" in path.parts or "test" in path.parts or "androidTest" in path.parts:
                continue
            if re.search(r"import com\.tencent\.cloud\.huiyansdkface", path.read_text()):
                errors.append(f"Vendor implementation outside integration: {path.relative_to(root)}")
    manifest = ET.parse(root / "assistant/src/main/AndroidManifest.xml").getroot()
    launchers = []
    for component in manifest.findall("application/*"):
        if component.get(ANDROID + "exported") == "true":
            if component.tag != "activity" or component.get(ANDROID + "name") != ".AssistantActivity":
                errors.append("Unexpected exported assistant component")
            else:
                launchers.append(component)
                actions = [item.get(ANDROID + "name") for item in component.findall("intent-filter/action")]
                if actions != ["android.intent.action.MAIN"]:
                    errors.append("Assistant launcher accepts non-launcher intents")
    if len(launchers) != 1:
        errors.append("Assistant must have exactly one exported launcher")
    for module in ("app", "assistant"):
        for variant in ([variant] if variant else []):
            merged = root / module / f"build/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml"
            if not merged.exists():
                merged = root / module / f"build/intermediates/merged_manifest/{variant}/process{variant.title()}MainManifest/AndroidManifest.xml"
            if not merged.exists():
                errors.append(f"Missing merged manifest: {module} {variant}")
                continue
            tree = ET.parse(merged).getroot()
            expected_id = "com.ytone.longcare" + (".assistant" if module == "assistant" else "")
            if tree.get("package") != expected_id:
                errors.append(f"Unexpected merged applicationId: {module} {variant}")
            for component in tree.findall("application/*"):
                name = component.get(ANDROID + "name", "")
                if module == "app" and (".validation." in name or ".assistant." in name):
                    errors.append(f"Validation component in merged formal {variant}: {name}")
                if module == "assistant" and component.get(ANDROID + "exported") == "true":
                    approved = name == "com.ytone.longcare.assistant.AssistantActivity"
                    if not approved:
                        errors.append(f"Unexpected merged assistant export: {name}")
    return errors


if __name__ == "__main__":
    failures = verify(Path(sys.argv[1]).resolve(), sys.argv[2] if len(sys.argv) > 2 else None)
    for failure in failures:
        print(f"[validation-isolation][FAIL] {failure}", file=sys.stderr)
    if failures:
        sys.exit(1)
    print("[validation-isolation][PASS] formal app and assistant are isolated")
