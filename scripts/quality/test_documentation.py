"""Application releases must not require a duplicate Markdown version snapshot."""
from pathlib import Path
import re
import shutil
import tempfile
import unittest

from verify_documentation import check_versions

ROOT = Path(__file__).resolve().parents[2]


class DocumentationVersionTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        for name in ('constants.gradle.kts', 'gradle/libs.versions.toml',
                     'gradle/wrapper/gradle-wrapper.properties', 'docs/architecture/tech-stack.md'):
            dest = self.root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, dest)

    def test_application_version_changes_do_not_require_documentation_changes(self):
        constants = self.root / 'constants.gradle.kts'
        updated = re.sub(r'("appVersionCode",\s*)\d+', r'\g<1>9999', constants.read_text())
        updated = re.sub(r'("appVersionName",\s*)"[^"]+"', r'\g<1>"99.0.0"', updated)
        constants.write_text(updated)
        self.assertEqual([], check_versions(self.root))

    def test_sdk_documentation_drift_still_fails(self):
        constants = self.root / 'constants.gradle.kts'
        constants.write_text(re.sub(r'("appTargetSdkVersion",\s*)\d+',
                                    r'\g<1>9999', constants.read_text()))
        errors = check_versions(self.root)
        self.assertEqual(1, len(errors))
        self.assertIn('stale targetSdk', errors[0])

    def test_toolchain_and_dependency_documentation_drift_still_fails(self):
        doc = self.root / 'docs/architecture/tech-stack.md'
        doc.write_text('\n'.join(line for line in doc.read_text().splitlines()
                                 if not line.startswith(('| Gradle Wrapper |', '| Persistence | Room |'))))
        errors = check_versions(self.root)
        self.assertEqual(2, len(errors))
        self.assertTrue(any('stale Gradle' in error for error in errors))
        self.assertTrue(any('stale Room' in error for error in errors))


if __name__ == '__main__':
    unittest.main()
