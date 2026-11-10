"""Keep reviewed vendor exceptions narrow without disabling warning or expiry checks."""

from datetime import date, timedelta
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GATE = ROOT / "scripts/lint/verify_lint_warning_allowlist.sh"
WAIVERS = ROOT / "scripts/lint/lint_warning_waivers.json"
KNOWN = (
    "/cache/jetified-crashreport-4.1.9.7/jni/x86_64/libBugly_Native.so: Warning: unaligned [Aligned16KB]\n"
    "/cache/jetified-qlzsdk-1.3.0.5-protobufLiteRelease-ui/jars/classes.jar: Warning: weak TLS [TrustAllX509TrustManager]\n"
    "/cache/jetified-qcloud-foundation-1.5.82/jars/classes.jar: Warning: dormant branch [TrustAllX509TrustManager]\n"
)


class LintWarningPolicyTest(unittest.TestCase):
    def gate(self, report=KNOWN, current_date="2026-11-10"):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lint.txt"
            path.write_text(report)
            return subprocess.run(
                ["bash", str(GATE), str(path), str(WAIVERS)],
                cwd=ROOT,
                env={**os.environ, "LINT_WAIVER_DATE": current_date, "LINT_ENFORCE_UNUSED_WAIVERS": "true"},
                text=True,
                capture_output=True,
            )

    def test_reviewed_current_sources_pass(self):
        result = self.gate()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_current_coordinate_and_face_sources_pass(self):
        for line in (
            "gradle/libs.versions.toml: Warning: native library (from com.tencent.bugly:crashreport:4.1.9.7) is unaligned [Aligned16KB]",
            "/cache/jetified-WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc/jni/arm64-v8a/libYTCommonLiveness.so: Warning: unaligned [Aligned16KB]",
            "gradle/libs.versions.toml: Warning: (from com.tencent.cloud.huiyansdkface:wbcloudface-live:6.6.2-8e4718fc) unaligned [Aligned16KB]",
            "gradle/libs.versions.toml: Warning: (from com.qcloud:qcloud-foundation:1.5.82) weak TLS [TrustAllX509TrustManager]",
        ):
            with self.subTest(source=line):
                result = self.gate(KNOWN + line + "\n")
                self.assertEqual(0, result.returncode, result.stderr)

    def test_other_versions_are_not_automatically_accepted(self):
        for old, new in (
            ("crashreport-4.1.9.7/", "crashreport-4.1.9.8/"),
            ("crashreport-4.1.9.7/", "crashreport-4.1.9.70/"),
            ("qcloud-foundation-1.5.82/", "qcloud-foundation-1.5.83/"),
            ("qlzsdk-1.3.0.5-", "qlzsdk-1.3.0.6-"),
        ):
            with self.subTest(version=new):
                result = self.gate(KNOWN.replace(old, new))
                self.assertNotEqual(0, result.returncode)
                self.assertIn("unexpected sources", result.stderr)

    def test_app_source_cannot_use_a_vendor_warning_exception(self):
        result = self.gate(KNOWN + "app/src/main/Tls.kt: Warning: weak TLS [TrustAllX509TrustManager]\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unexpected sources", result.stderr)

    def test_new_warning_id_is_blocked(self):
        result = self.gate(KNOWN + "app/src/main/New.kt: Warning: new issue [NewBlockingIssue]\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("outside waiver allowlist", result.stderr)

    def test_expired_exceptions_still_block(self):
        entries = json.loads(WAIVERS.read_text())["waivers"]
        expired_date = max(date.fromisoformat(entry["review_by"]) for entry in entries) + timedelta(days=1)
        result = self.gate(current_date=expired_date.isoformat())
        self.assertNotEqual(0, result.returncode)
        self.assertIn("expired lint waivers", result.stderr)


if __name__ == "__main__":
    unittest.main()
