import importlib.util
import json
from pathlib import Path
import tempfile
import subprocess
import shutil
import unittest
import sys

sys.dont_write_bytecode = True

SPEC = importlib.util.spec_from_file_location("packaging", Path(__file__).with_name("package_dual_apks.py"))
packaging = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(packaging)


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for module in ("app", "assistant"):
            directory = self.root / module / "build/outputs/apk/debug"
            directory.mkdir(parents=True)
            (directory / f"{module}-debug.apk").write_bytes(b"current-test-apk")
            (directory / "stale.apk").write_bytes(b"old")
            metadata = {"applicationId": "com.ytone.longcare" + (".assistant" if module == "assistant" else ""),
                        "elements": [{"versionName": "1.0.6" + ("-assistant" if module == "assistant" else ""), "versionCode": 59, "outputFile": f"{module}-debug.apk"}]}
            (directory / "output-metadata.json").write_text(json.dumps(metadata))

    def test_exact_pair_ignores_stale_files_and_preserves_previous_export(self):
        output = packaging.package(self.root, "debug", "debug")
        self.assertEqual(2, len(list(output.glob("*.apk"))))
        self.assertEqual(2, len((output / "SHA256SUMS").read_text().splitlines()))
        packaging.package(self.root, "debug", "debug")
        self.assertEqual(2, len(list(output.glob("*.apk"))))

    def test_missing_package_fails_without_publishing_partial_output(self):
        (self.root / "assistant/build/outputs/apk/debug/assistant-debug.apk").unlink()
        with self.assertRaises(ValueError):
            packaging.package(self.root, "debug", "debug")
        self.assertFalse((self.root / "build/outputs/dual-apk/debug").exists())

    def test_wrong_id_and_mixed_versions_are_rejected(self):
        path = self.root / "assistant/build/outputs/apk/debug/output-metadata.json"
        original = json.loads(path.read_text())
        invalid = dict(original, applicationId="com.ytone.longcare")
        path.write_text(json.dumps(invalid))
        with self.assertRaises(ValueError):
            packaging.package(self.root, "debug", "debug")
        original["elements"][0]["versionCode"] = 60
        path.write_text(json.dumps(original))
        with self.assertRaises(ValueError):
            packaging.package(self.root, "debug", "debug")

    def test_production_is_not_a_dual_apk_mode(self):
        with self.assertRaises(ValueError):
            packaging.package(self.root, "production", "release")

    def test_gradle_failure_retires_old_export_and_does_not_publish(self):
        packaging.package(self.root, "debug", "debug")
        script = self.root / "scripts/release/build-dual-apks.sh"
        script.parent.mkdir(parents=True)
        shutil.copy2(Path(__file__).with_name("build-dual-apks.sh"), script)
        gradle = self.root / "gradlew"
        gradle.write_text("#!/bin/sh\nexit 42\n")
        gradle.chmod(0o755)
        result = subprocess.run(["bash", str(script), "--debug"], capture_output=True)
        self.assertEqual(42, result.returncode)
        self.assertFalse((self.root / "build/outputs/dual-apk/debug").exists())


if __name__ == "__main__":
    unittest.main()
