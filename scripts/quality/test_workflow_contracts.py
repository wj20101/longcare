"""Small workflow contracts; step titles and exact timeouts are not policy."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class WorkflowContractsTest(unittest.TestCase):
    def test_actions_permissions_timeouts_and_reports(self):
        for path in (ROOT / '.github/workflows').glob('*.yml'):
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn('permissions:', text)
                self.assertIn('concurrency:', text)
                self.assertRegex(text, r'timeout-minutes: [1-9][0-9]*')
                self.assertNotRegex(text, r'uses: [^\s]+@(main|master|HEAD)\b')
                for retention in re.findall(r'retention-days: (\d+)', text):
                    self.assertGreaterEqual(int(retention), 7)
        wrapper = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertRegex(wrapper, r'distributionSha256Sum=[a-f0-9]{64}')

    def test_build_validation_is_not_a_fixed_test_filter(self):
        ci = (ROOT / '.github/workflows/android-ci.yml').read_text()
        tasks = (ROOT / 'scripts/quality/run_jvm_tests.sh').read_text()
        self.assertIn('run_jvm_tests.sh', ci)
        for task in ('testDebugUnitTest', ':core:model:test', ':core:domain:test'):
            self.assertIn(task, tasks)
        self.assertNotIn('--tests', tasks)
        self.assertNotIn('affected_scope', ci)
        self.assertNotIn('bundleDebug', ci)
        self.assertIn('**/build/test-results/**', ci)
        self.assertIn('run_ci_checks.sh', ci)
        self.assertIn('verify_documentation.py', ci)
        self.assertNotIn('actions: write', ci)

    def test_security_and_business_gates_are_still_called(self):
        gates = (ROOT / 'scripts/quality/run_ci_checks.sh').read_text()
        for script in ('verify_no_tracked_keystore_files.sh', 'verify_gradle_stability.sh',
                       'verify_ci_workflow_quality.sh', 'verify_validation_app_isolation.sh',
                       'verify_lint_ignore_policy.sh', 'verify_jetpack_compat_apis.sh',
                       'verify_baselineprofile_journeys.sh', 'verify_cancellation_guards.sh',
                       'verify_no_empty_catch_blocks.sh', 'verify_target_sdk_upgrade.sh',
                       'verify_exact_alarm_permission_config.sh', 'verify_architecture_boundaries.sh',
                       'verify_module_dependency_whitelist.sh', 'verify_module_api_visibility.sh'):
            self.assertIn(script, gates)
        self.assertNotIn('|| true', gates)

    def test_maintenance_does_not_gate_builds(self):
        for filename in ('android-ci.yml', 'android-release.yml', 'baseline-profile.yml'):
            text = (ROOT / '.github/workflows' / filename).read_text()
            self.assertNotIn('cleanup-caches', text)
            self.assertNotIn('cleanup_github_actions', text)
        self.assertIn('cleanup_github_actions_storage.sh',
                      (ROOT / '.github/workflows/actions-runs-cleanup.yml').read_text())

    def test_kvm_and_baseline_have_single_implementations(self):
        for filename in ('android-ci.yml', 'baseline-profile.yml'):
            text = (ROOT / '.github/workflows' / filename).read_text()
            self.assertIn('bash scripts/quality/enable_kvm.sh', text)
            self.assertLess(text.index('actions/checkout@'), text.index('bash scripts/quality/enable_kvm.sh'))
        release = (ROOT / '.github/workflows/android-release.yml').read_text()
        self.assertNotIn('generateReleaseBaselineProfile', release)
        self.assertFalse((ROOT / '.github/scripts/run-baseline-profile.sh').exists())
        migration = (ROOT / '.github/workflows/face-sdk-migration-check.yml').read_text()
        self.assertNotIn('constants.gradle.kts', migration)
        self.assertIn('TencentFaceConventionPlugin.kt', migration)


if __name__ == '__main__':
    unittest.main()
