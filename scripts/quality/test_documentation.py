"""Project documentation checks preserve version and link validation boundaries."""
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
import re
import shutil
import tempfile
import unittest
from unittest.mock import patch

from verify_documentation import check_versions, main

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

    def test_vendored_skill_examples_do_not_disable_project_link_validation(self):
        index = self.root / 'docs/README.md'
        index.write_text('[Tech stack](architecture/tech-stack.md)\n')
        skill = self.root / '.agents/skills/example/SKILL.md'
        skill.parent.mkdir(parents=True)
        skill.write_text('[Example screenshot](path/to/screenshot)\n')
        files = [Path('docs/README.md'), Path('.agents/skills/example/SKILL.md')]
        with patch('verify_documentation.markdown_files', return_value=files), \
                patch('sys.argv', ['verify_documentation.py', '--root', str(self.root)]), \
                redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            self.assertEqual(0, main())
            index.write_text(index.read_text() + '[Broken project link](missing.md)\n')
            self.assertEqual(1, main())


if __name__ == '__main__':
    unittest.main()
