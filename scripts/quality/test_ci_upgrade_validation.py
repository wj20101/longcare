"""Exercise the actual CI scripts without starting an emulator or changing host KVM."""

from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/android-ci.yml").read_text()


class KvmPreflightTest(unittest.TestCase):
    def run_preflight(self, exists=True, readable=True, writable=True, udev_ok=True,
                      deferred_permissions=False, settle_ok=True):
        script = (ROOT / "scripts/quality/enable_kvm.sh").read_text()
        with tempfile.TemporaryDirectory() as directory:
            device = Path(directory) / "kvm"
            if exists:
                device.touch()
            # Inject only OS boundaries; execute the production control flow unchanged.
            prelude = f"""
                kvm_events_pending=0
                sudo() {{
                    echo "sudo $*" >&2
                    if [[ "$1" == tee ]]; then cat; return; fi
                    if [[ {0 if udev_ok else 1} != 0 ]]; then return 1; fi
                    case "$*" in
                        'udevadm trigger --name-match=kvm') kvm_events_pending=1 ;;
                        'udevadm settle --timeout=30')
                            if [[ {0 if settle_ok else 1} != 0 ]]; then return 1; fi
                            kvm_events_pending=0 ;;
                    esac
                }}
                test() {{
                    if [[ {1 if deferred_permissions else 0} == 1 && "$kvm_events_pending" == 1 ]]; then
                        return 1
                    fi
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

    def test_permissions_are_checked_after_udev_events_complete(self):
        result = self.run_preflight(deferred_permissions=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertLess(result.stderr.index("udevadm trigger --name-match=kvm"),
                        result.stderr.index("udevadm settle --timeout=30"))

    def test_udev_settle_timeout_is_not_ignored(self):
        self.assertNotEqual(0, self.run_preflight(settle_ok=False).returncode)

    def test_available_device_applies_rule_then_passes(self):
        result = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('MODE="0666"', result.stdout)
        self.assertIn("udevadm control --reload-rules", result.stderr)
        self.assertIn("udevadm trigger --name-match=kvm", result.stderr)

    def test_preflight_precedes_emulator(self):
        self.assertLess(WORKFLOW.index("bash scripts/quality/enable_kvm.sh"),
                        WORKFLOW.index("uses: reactivecircus/android-emulator-runner"))


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


if __name__ == "__main__":
    unittest.main()
