"""Run release naming/metadata shell steps offline, without publishing anything."""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/android-release.yml").read_text()


def step(name):
    match = re.search(r"^      - name: " + re.escape(name) + r"\n(.*?)(?=^      - name: |^  [\w-]+:|\Z)",
                      WORKFLOW, re.MULTILINE | re.DOTALL)
    if match is None:
        raise AssertionError(f"Missing workflow step: {name}")
    return match[1]


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.output = self.root / "github-output"

    def run_step(self, name):
        script = textwrap.dedent(step(name).split("        run: |\n", 1)[1])
        script = script.replace("${{ steps.app_version.outputs.version_code }}", "60")
        script = script.replace("${{ steps.app_version.outputs.version_name }}", "1.0.6")
        self.assertNotIn("${{", script)
        return subprocess.run(["bash", "-euo", "pipefail", "-c", script], cwd=self.root,
                              env=dict(os.environ, GITHUB_OUTPUT=str(self.output)),
                              text=True, capture_output=True)

    def test_apk_and_aab_names_and_content(self):
        for extension, folder in (("apk", "apk"), ("aab", "bundle")):
            directory = self.root / f"app/build/outputs/{folder}/release"
            directory.mkdir(parents=True)
            source = directory / f"app-release.{extension}"
            source.write_bytes(b"unchanged signed artifact")
            result = self.run_step(f"Rename release {extension.upper()} with version metadata")
            self.assertEqual(0, result.returncode, result.stderr)
            artifacts = list(directory.glob(f"*.{extension}"))
            self.assertEqual(1, len(artifacts))
            self.assertRegex(artifacts[0].name, rf"^app-v1\.0\.6-\d{{6}}-60-release\.{extension}$")
            self.assertEqual(b"unchanged signed artifact", artifacts[0].read_bytes())
            self.assertIn(f"{extension}_path={artifacts[0].relative_to(self.root)}\n", self.output.read_text())

    def test_missing_artifacts_fail(self):
        for extension, folder in (("apk", "apk"), ("aab", "bundle")):
            (self.root / f"app/build/outputs/{folder}/release").mkdir(parents=True)
            result = self.run_step(f"Rename release {extension.upper()} with version metadata")
            self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.output.exists())

    def test_release_metadata(self):
        result = self.run_step("Compute release metadata")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("tag_name=v1.0.6-60\nrelease_name=Release v1.0.6 (60)\n", self.output.read_text())

    def test_single_official_release_keeps_guards_and_excludes_assistant(self):
        self.assertNotRegex(WORKFLOW, r"(?i)production|acceptance|release_mode|RELEASE_MODE")
        self.assertNotIn("dual-apk", WORKFLOW)
        self.assertNotIn(":assistant:", WORKFLOW)
        publish = step("Publish artifacts to GitHub Releases")
        for setting in ("prerelease: false", "draft: false", 'make_latest: "true"'):
            self.assertIn(setting, publish)
        self.assertIn("name: app-release-artifacts", step("Upload release artifacts"))
        self.assertIn(":app:assembleRelease :app:bundleRelease", step("Build release APK and AAB"))
        vendor = step("Check vendor SDK risk policy")
        self.assertNotIn("if:", vendor)
        self.assertIn("verify_vendor_sdk_release_readiness.sh", vendor)
        signing = step("Run release-required signing safety checks")
        for variable in ("ANDROID_KEYSTORE_BASE64", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD"):
            self.assertIn(f'Missing secret: {variable}', signing)
        ci = step("Verify Android CI success for target commit")
        self.assertIn("select(.headSha == $target_sha)", ci)
        self.assertIn('"${CONCLUSION}" != "success"', ci)
        self.assertIn("exit 1", step("Reject tag-triggered auto version bump"))


if __name__ == "__main__":
    unittest.main()
