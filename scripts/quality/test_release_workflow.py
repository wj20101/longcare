"""Run release naming/metadata shell steps offline, without publishing anything."""

import os
import hashlib
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

    def run_step(self, name, **env):
        script = textwrap.dedent(step(name).split("        run: |\n", 1)[1])
        script = script.replace("${{ steps.app_version.outputs.version_code }}", "60")
        script = script.replace("${{ steps.app_version.outputs.version_name }}", "1.0.6")
        self.assertNotIn("${{", script)
        return subprocess.run(["bash", "-euo", "pipefail", "-c", script], cwd=self.root,
                              env=dict(os.environ, GITHUB_OUTPUT=str(self.output), **env),
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

    def test_checksums_include_both_installers_and_fail_without_aab(self):
        paths = ("app/build/outputs/apk/release/app.apk",
                 "app/build/outputs/bundle/release/app.aab")
        for relative in paths:
            path = self.root / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(relative.encode())
        result = self.run_step("Generate release checksums")
        self.assertEqual(0, result.returncode, result.stderr)
        checksums = (self.root / "app/build/outputs/release-checksums.txt").read_text()
        self.assertEqual(2, len(checksums.splitlines()))
        for relative in paths:
            self.assertIn(f"{hashlib.sha256(relative.encode()).hexdigest()}  {relative}\n", checksums)
        (self.root / paths[-1]).unlink()
        self.assertNotEqual(0, self.run_step("Generate release checksums").returncode)

    def prepare_apk_verification(self):
        tools = self.root / "sdk/build-tools/37.0.0"
        tools.mkdir(parents=True)
        scripts = {
            "apksigner": '''#!/usr/bin/env bash
set -eu
if [[ "${BAD_SIGNATURE:-}" == true ]]; then exit 1; fi
cert=official
echo "${SIGNER_LABEL:-V2 Signer:} certificate SHA-256 digest: $cert"
''',
            "aapt": '''#!/usr/bin/env bash
set -eu
package="${APP_PACKAGE:-com.ytone.longcare}"
name="${APP_VERSION_NAME:-1.0.6}"
code="${APP_VERSION_CODE:-60}"
echo "package: name='$package' versionCode='$code' versionName='$name' platformBuildVersionName='16'"
if [[ "${DEBUGGABLE:-}" == true ]]; then echo application-debuggable; fi
''',
        }
        for name, script in scripts.items():
            path = tools / name
            path.write_text(script)
            path.chmod(0o755)
        for module in ("app",):
            apk = self.root / f"{module}/build/outputs/apk/release/{module}-release.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"APK fixture")
            mapping = self.root / f"{module}/build/outputs/mapping/release/mapping.txt"
            mapping.parent.mkdir(parents=True)
            mapping.write_text("mapping fixture")
        return {"ANDROID_HOME": str(self.root / "sdk")}

    def test_apk_identity_and_signing_validation(self):
        env = self.prepare_apk_verification()
        name = "Verify release APK identities and signatures"
        result = self.run_step(name, **env)
        self.assertEqual(0, result.returncode, result.stderr)
        result = self.run_step(name, **env, SIGNER_LABEL="Signer #1")
        self.assertEqual(0, result.returncode, result.stderr)
        for invalid in ({"BAD_SIGNATURE": "true"}, {"APP_PACKAGE": "com.invalid"},
                        {"APP_VERSION_CODE": "59"},
                        {"APP_VERSION_NAME": "1.0.5"}, {"DEBUGGABLE": "true"}):
            with self.subTest(invalid=invalid):
                self.assertNotEqual(0, self.run_step(name, **env, **invalid).returncode)
        mapping = self.root / "app/build/outputs/mapping/release/mapping.txt"
        mapping.unlink()
        self.assertNotEqual(0, self.run_step(name, **env).returncode)
        mapping.write_text("mapping fixture")
        (self.root / "app/build/outputs/apk/release/app-release.apk").unlink()
        self.assertNotEqual(0, self.run_step(name, **env).returncode)

    def test_single_official_release_keeps_guards_without_assistant(self):
        self.assertNotRegex(WORKFLOW, r"(?i)production|acceptance|release_mode|RELEASE_MODE")
        self.assertNotIn("dual-apk", WORKFLOW)
        publish = step("Publish artifacts to GitHub Releases")
        for setting in ("prerelease: false", "draft: false", 'make_latest: "true"'):
            self.assertIn(setting, publish)
        self.assertIn("name: app-release-artifacts", step("Upload release artifacts"))
        self.assertIn(":app:assembleRelease :app:bundleRelease", step("Build release APK and AAB"))
        self.assertNotIn("assistant", WORKFLOW)
        self.assertIn("fail_on_unmatched_files: true", publish)
        self.assertIn("verify_validation_app_isolation.sh", step("Run ci-required quality gates"))
        ordered_steps = ("Build release APK and AAB", "Run release-required exported component guard",
                         "Verify release APK identities and signatures", "Generate release checksums",
                         "Upload release artifacts", "Publish artifacts to GitHub Releases")
        positions = [WORKFLOW.index(f"- name: {name}\n") for name in ordered_steps]
        self.assertEqual(sorted(positions), positions)
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
