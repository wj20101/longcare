"""Exercise the actual CI scripts without starting an emulator or changing host KVM."""

import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/android-ci.yml").read_text()


class KvmPreflightTest(unittest.TestCase):
    def run_preflight(self, exists=True, readable=True, writable=True, udev_ok=True):
        match = re.search(
            r"      - name: Enable KVM acceleration\n        run: \|\n(.*?)(?=      - name:)",
            WORKFLOW, re.S,
        )
        self.assertIsNotNone(match)
        script = textwrap.dedent(match[1])
        with tempfile.TemporaryDirectory() as directory:
            device = Path(directory) / "kvm"
            if exists:
                device.touch()
            # Inject only OS boundaries; execute the production control flow unchanged.
            prelude = f"""
                sudo() {{
                    echo "sudo $*" >&2
                    if [[ "$1" == tee ]]; then cat; else return {0 if udev_ok else 1}; fi
                }}
                test() {{
                    case "$1" in
                        -r) return {0 if readable else 1} ;;
                        -w) return {0 if writable else 1} ;;
                        *) builtin test "$@" ;;
                    esac
                }}
            """
            return subprocess.run(
                ["bash", "-euo", "pipefail", "-c", prelude + script.replace("/dev/kvm", shlex.quote(str(device)))],
                text=True, capture_output=True,
            )

    def test_missing_device_fails_before_sudo(self):
        result = self.run_preflight(exists=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unavailable", result.stdout)
        self.assertNotIn("sudo", result.stderr)

    def test_read_or_write_denial_fails_with_reason(self):
        for readable, writable in ((False, True), (True, False), (False, False)):
            with self.subTest(readable=readable, writable=writable):
                result = self.run_preflight(readable=readable, writable=writable)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("not readable and writable", result.stdout)

    def test_udev_failure_is_not_ignored(self):
        self.assertNotEqual(0, self.run_preflight(udev_ok=False).returncode)

    def test_available_device_applies_rule_then_passes(self):
        result = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('MODE="0666"', result.stdout)
        self.assertIn("udevadm control --reload-rules", result.stderr)
        self.assertIn("udevadm trigger --name-match=kvm", result.stderr)

    def test_preflight_precedes_emulator(self):
        self.assertLess(WORKFLOW.index("name: Enable KVM acceleration"),
                        WORKFLOW.index("name: Run selected instrumentation smoke tests"))


class ConcurrencyTest(unittest.TestCase):
    def test_pr_groups_are_stable_isolated_and_cancel_only_prs(self):
        group = re.search(r"^  group: (.*)$", WORKFLOW, re.M)[1]
        cancel = re.search(r"^  cancel-in-progress: (.*)$", WORKFLOW, re.M)[1]
        # Deliberately accept only the two supported expressions, so a SHA-based
        # or otherwise drifting expression fails instead of being silently ignored.
        expressions = re.findall(r"\$\{\{ (.*?) \}\}", group)
        self.assertEqual(["github.event_name", "github.event.pull_request.number || github.ref"], expressions)
        self.assertEqual("${{ github.event_name == 'pull_request' }}", cancel)

        def render(event, pr, ref, sha):
            values = {"github.event_name": event, "github.event.pull_request.number || github.ref": str(pr or ref)}
            return re.sub(r"\$\{\{ (.*?) \}\}", lambda match: values[match[1]], group)

        first = render("pull_request", 115, "refs/pull/115/merge", "old")
        self.assertEqual(first, render("pull_request", 115, "refs/pull/115/merge", "new"))
        self.assertNotEqual(first, render("pull_request", 120, "refs/pull/120/merge", "new"))
        self.assertNotEqual(first, render("push", None, "refs/heads/master", "new"))


class AffectedPathsTest(unittest.TestCase):
    def test_each_ci_path_triggers_smoke_but_docs_do_not(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / "scripts/quality/affected-modules.sh"
            script.parent.mkdir(parents=True)
            script.write_bytes((ROOT / "scripts/quality/affected-modules.sh").read_bytes())
            env = os.environ.copy()
            for key in list(env):
                if key.startswith("GIT_"):
                    env.pop(key)
            env.update(GIT_AUTHOR_NAME="CI test", GIT_AUTHOR_EMAIL="ci@example.invalid",
                       GIT_COMMITTER_NAME="CI test", GIT_COMMITTER_EMAIL="ci@example.invalid")

            def git(*args):
                return subprocess.check_output(
                    ["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false", *args],
                    cwd=root, env=env, text=True, stderr=subprocess.PIPE,
                ).strip()

            git("init", "-q")
            git("add", ".")
            git("commit", "-qm", "base")
            base = git("rev-parse", "HEAD")
            for path, expected in (
                (".github/workflows/android-ci.yml", "true"),
                (".github/scripts/run-instrumentation-smoke.sh", "true"),
                ("scripts/quality/affected-modules.sh", "true"),
                ("docs/example.md", "false"),
            ):
                with self.subTest(path=path):
                    target = root / path
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open("a") as file:
                        file.write("\n# fixture change\n")
                    git("add", path)
                    git("commit", "-qm", path)
                    head = git("rev-parse", "HEAD")
                    output = subprocess.check_output(
                        ["bash", str(script), "--base", base, "--head", head, "--format", "github"],
                        cwd=root, env=env, text=True,
                    )
                    fields = dict(line.split("=", 1) for line in output.splitlines())
                    self.assertEqual(expected, fields["run_instrumentation"])
                    self.assertEqual("1", fields["changed_files_count"])
                    self.assertIn(":feature:carddiagnostics:testDebugUnitTest", fields["verify_tasks"])
                    self.assertIn(":app:testDebugUnitTest", fields["verify_tasks"])
                    self.assertIn("--tests com.ytone.longcare.features.login.ui.LoginCardDiagnosticsEntryTest", fields["verify_tasks"])
                    self.assertIn("--tests com.ytone.longcare.navigation.*", fields["verify_tasks"])
                    self.assertIn("com.ytone.longcare.ExampleInstrumentedTest", fields["smoke_test_classes"])
                    base = head


if __name__ == "__main__":
    unittest.main()
