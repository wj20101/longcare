"""Exercise CI selection in a temporary repository, without Android or network."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SelectionTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.env = {k: v for k, v in os.environ.items()
                    if not k.startswith(("GIT_", "GITHUB_")) and k not in ("BASE_REF", "HEAD_REF")}
        self.env.update(GIT_AUTHOR_NAME="test", GIT_AUTHOR_EMAIL="test@example.invalid",
                        GIT_COMMITTER_NAME="test", GIT_COMMITTER_EMAIL="test@example.invalid")
        self.git("init", "-q")
        (self.root / "seed").touch()
        self.git("add", ".")
        self.git("commit", "-qm", "base")
        self.base = self.git("rev-parse", "HEAD").strip()

    def git(self, *args):
        return subprocess.check_output(["git", "-c", "commit.gpgsign=false", "-c",
                                        "core.hooksPath=/dev/null", *args],
                                       cwd=self.root, env=self.env, text=True)

    def select(self, path=None, **env):
        if path:
            file = self.root / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("fixture")
            self.git("add", ".")
            self.git("commit", "-qm", "change")
        result = subprocess.run(["bash", str(ROOT / "scripts/quality/select_ci_smoke.sh")],
                                cwd=self.root, env=dict(self.env, BASE_REF=self.base, **env),
                                text=True, capture_output=True, check=True)
        return dict(line.split("=", 1) for line in result.stdout.splitlines())

    def test_docs_only(self):
        result = self.select("docs/example.md")
        self.assertEqual("false", result["run_build"])
        self.assertEqual("false", result["run_instrumentation"])

    def test_business_logic_builds_without_device(self):
        result = self.select("core/data/src/main/Repository.kt")
        self.assertEqual("true", result["run_build"])
        self.assertEqual("false", result["run_instrumentation"])

    def test_ci_and_platform_changes_run_real_smoke(self):
        for path in (".github/workflows/android-ci.yml", ".github/scripts/run-instrumentation-smoke.sh",
                     "scripts/quality/select_ci_smoke.sh", ".github/actions/android-build-env/action.yml",
                     "app/src/main/AndroidManifest.xml", "gradle/libs.versions.toml"):
            with self.subTest(path=path):
                result = self.select(path)
                self.assertEqual("true", result["run_instrumentation"])
                self.assertIn("MainActivitySmokeTest", result["smoke_test_classes"])
                self.assertNotIn("ExampleInstrumentedTest", result["smoke_test_classes"])

    def test_manual_and_missing_base_never_skip(self):
        for env in ({"GITHUB_EVENT_NAME": "workflow_dispatch"}, {"BASE_REF": "missing"}):
            with self.subTest(env=env):
                options = dict(self.env, BASE_REF=self.base)
                options.update(env)
                output = subprocess.check_output(["bash", str(ROOT / "scripts/quality/select_ci_smoke.sh")],
                                                 cwd=self.root, env=options, text=True)
                self.assertIn("run_build=true", output)
                self.assertIn("run_instrumentation=true", output)

    def test_webview_and_navigation_have_relevant_tests(self):
        result = self.select("app/src/main/kotlin/com/ytone/longcare/platform/webview/NativeBridge.kt")
        self.assertIn("NativeWebViewCloseBridgeTest", result["smoke_test_classes"])
        result = self.select("app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigator.kt")
        self.assertIn("Navigation3StateTest", result["smoke_test_classes"])

    def test_live_test_is_not_auto_selected(self):
        result = self.select("app/src/androidTest/kotlin/com/ytone/longcare/features/sales/SalesEvaluationLiveResultTest.kt")
        self.assertEqual("true", result["run_instrumentation"])
        self.assertNotIn("SalesEvaluationLiveResultTest", result["smoke_test_classes"])


if __name__ == "__main__":
    unittest.main()
