"""Exercise release policy scripts; accepted vendor risks must not hide other failures."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "scripts/quality/verify_production_release_config.sh"
VENDOR = ROOT / "scripts/quality/verify_vendor_sdk_release_readiness.sh"


class ReleasePolicyTest(unittest.TestCase):
    def config(self, *args):
        return subprocess.run(["bash", str(CONFIG), *args], text=True, capture_output=True)

    def vendor(self, report):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lint.txt"
            if report is not None:
                path.write_text(report)
            return subprocess.run(["bash", str(VENDOR), str(path)], text=True, capture_output=True)

    def test_current_production_risks_warn_without_failing(self):
        result = self.config("--production-requested", "true", "--acceptance-requested", "false",
                             "--temporary-qlz-key-present", "true", "--qlz-test-mode", "true",
                             "--known-unsafe-qlz-sdk-present", "true", "--known-unsafe-face-sdk-present", "true")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("[WARN]", result.stderr)
        self.assertIn("not guaranteed", result.stderr)

    def test_mode_conflict_and_implicit_acceptance_fail(self):
        for args in (("--production-requested", "true", "--acceptance-requested", "true"), ()):
            self.assertNotEqual(0, self.config(*args).returncode)

    def test_explicit_acceptance_and_clean_production_pass(self):
        for args in (("--acceptance-requested", "true"), ("--production-requested", "true")):
            self.assertEqual(0, self.config(*args).returncode)

    def test_missing_invalid_or_unknown_option_fails(self):
        for args in (("--production-requested",), ("--production-requested", "typo"),
                     ("--ignore-everything", "true")):
            self.assertNotEqual(0, self.config(*args).returncode)

    def test_current_vendor_findings_warn(self):
        lines = (
            "jetified-qlzsdk-1.3.0.5-protobufLiteRelease-ui/classes.jar: Warning [TrustAllX509TrustManager]",
            "jetified-WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc/libYTCommonLiveness.so: Warning [Aligned16KB]",
            "jetified-WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc/proguard.txt: Warning [GlobalOptionInConsumerRules]",
        )
        for line in lines:
            result = self.vendor(line + "\n0 errors, 1 warnings\n")
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("accepted risk, not fixed", result.stderr)

    def test_different_vendor_versions_are_not_automatically_accepted(self):
        for line in ("qlzsdk-2.0.0/classes.jar: Warning [TrustAllX509TrustManager]",
                     "WbCloudFaceLiveSdk-face-v7.0/libYTCommonLiveness.so: Warning [Aligned16KB]",
                     "WbCloudFaceLiveSdk-face-v7.0/proguard.txt: Warning [GlobalOptionInConsumerRules]"):
            self.assertNotEqual(0, self.vendor(line + "\n").returncode)

    def test_missing_or_empty_report_fails(self):
        for report in (None, ""):
            self.assertNotEqual(0, self.vendor(report).returncode)

    def test_clean_report_passes(self):
        result = self.vendor("No issues found.\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("WARN", result.stderr)

    def test_directory_is_not_a_report(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(["bash", str(VENDOR), directory], text=True, capture_output=True)
            self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
