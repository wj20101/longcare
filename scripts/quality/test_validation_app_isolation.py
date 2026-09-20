import importlib.util
from pathlib import Path
import tempfile
import unittest
import sys
import subprocess

sys.dont_write_bytecode = True

SPEC = importlib.util.spec_from_file_location("isolation", Path(__file__).with_name("verify_validation_app_isolation.py"))
isolation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(isolation)
PROJECT = Path(__file__).resolve().parents[2]


class IsolationTest(unittest.TestCase):
    def test_release_export_guard_accepts_main_launcher_and_rejects_extra_component(self):
        script = PROJECT / "scripts/quality/verify_release_exported_components.sh"
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "AndroidManifest.xml"
            for package, launcher in (("com.ytone.longcare", "MainActivity"),):
                content = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
                           f' package="{package}">\n<application>\n'
                           f'<activity android:name="{package}.{launcher}" android:exported="true" />\n'
                           '</application>\n</manifest>\n')
                manifest.write_text(content)
                result = subprocess.run(["bash", str(script), str(manifest)], capture_output=True)
                self.assertEqual(0, result.returncode, result.stderr.decode())
                manifest.write_text(content.replace('</application>',
                    f'<activity android:name="{package}.Unexpected" android:exported="true" />\n</application>'))
                result = subprocess.run(["bash", str(script), str(manifest)], capture_output=True)
                self.assertNotEqual(0, result.returncode)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "settings.gradle.kts").write_bytes((PROJECT / "settings.gradle.kts").read_bytes())
        # Copy source only. Build outputs from other variants must not affect a source-only gate.
        for module in ("app", "core", "feature", "integration"):
            for source in (PROJECT / module).rglob("*"):
                if "build" in source.parts or not source.is_file() or source.suffix not in (".kt", ".kts", ".xml"):
                    continue
                relative = source.relative_to(PROJECT)
                target = self.root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())

    def test_current_sources_pass(self):
        self.assertEqual([], isolation.verify(self.root))

    def test_unregistered_diagnostics_is_rejected(self):
        path = self.root / "settings.gradle.kts"
        path.write_text(path.read_text().replace('include(":feature:carddiagnostics")', ''))
        self.assertTrue(isolation.verify(self.root))

    def test_requested_variant_requires_built_manifests(self):
        self.assertTrue(isolation.verify(self.root, "release"))

    def test_formal_hidden_entry_is_rejected(self):
        path = self.root / "app/src/main/kotlin/Injected.kt"
        path.write_text("fun onMainLogoLongPress() = Unit")
        self.assertTrue(isolation.verify(self.root))

    def test_application_dependency_is_rejected(self):
        path = self.root / "app/build.gradle.kts"
        path.write_text(path.read_text() + '\nimplementation(project(":assistant"))')
        self.assertTrue(isolation.verify(self.root))

    def test_wrong_main_package_is_rejected(self):
        path = self.root / "app/build.gradle.kts"
        path.write_text(path.read_text().replace('applicationId = "com.ytone.longcare"', 'applicationId = "com.invalid"'))
        self.assertTrue(isolation.verify(self.root))

    def test_diagnostic_activity_is_rejected(self):
        path = self.root / "app/src/main/AndroidManifest.xml"
        path.write_text(path.read_text().replace("</application>", '<activity android:name=".CardDiagnosticsActivity" android:exported="true" /></application>'))
        self.assertTrue(isolation.verify(self.root))

    def test_business_events_in_diagnostics_are_rejected(self):
        path = self.root / "feature/carddiagnostics/src/main/kotlin/Injected.kt"
        path.write_text("import com.ytone.longcare.common.event.AppEventBus")
        self.assertTrue(isolation.verify(self.root))

    def test_missing_shared_owner_is_rejected(self):
        (self.root / "feature/photoupload/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt").unlink()
        self.assertTrue(isolation.verify(self.root))


if __name__ == "__main__":
    unittest.main()
