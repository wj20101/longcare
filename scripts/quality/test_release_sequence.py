"""Run version preparation, push and CI guards against local Git/tool fixtures."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReleaseSequenceTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name) / 'checkout'
        self.root.mkdir()
        self.remote = Path(temp.name) / 'remote.git'
        self.env = {k: v for k, v in os.environ.items() if not k.startswith(('GIT_', 'GITHUB_'))}
        self.env.update(GIT_AUTHOR_NAME='test', GIT_AUTHOR_EMAIL='test@example.invalid',
                        GIT_COMMITTER_NAME='test', GIT_COMMITTER_EMAIL='test@example.invalid',
                        GITHUB_REF_TYPE='branch', GITHUB_REF_NAME='master',
                        GITHUB_OUTPUT=str(Path(temp.name) / 'output'))
        self.git('init', '--bare', str(self.remote))
        self.git('init', '-b', 'master')
        for name in ('constants.gradle.kts', 'docs/architecture/tech-stack.md',
                     'scripts/quality/gradle_constants.sh'):
            dest = self.root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, dest)
        self.git('add', '.')
        self.git('commit', '-qm', 'source')
        self.env['GITHUB_SHA'] = self.git('rev-parse', 'HEAD').stdout.strip()
        self.git('remote', 'add', 'origin', str(self.remote))
        self.git('push', '-u', 'origin', 'master')

    def git(self, *args, check=True):
        return subprocess.run(['git', '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', *args],
                              cwd=self.root, env=self.env, capture_output=True, text=True, check=check)

    def run_script(self, name, **env):
        executable = 'python3' if name.endswith('.py') else 'bash'
        return subprocess.run([executable, str(ROOT / 'scripts/release' / name)], cwd=self.root,
                              env=dict(self.env, **env), capture_output=True, text=True)

    def test_prepare_does_not_push_and_push_uses_prepared_sources(self):
        result = self.run_script('prepare_version.py')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.env['GITHUB_SHA'], self.git('rev-parse', 'HEAD').stdout.strip())
        self.assertEqual(self.env['GITHUB_SHA'], self.git('ls-remote', 'origin', 'refs/heads/master').stdout.split()[0])
        self.assertEqual(['constants.gradle.kts', 'docs/architecture/tech-stack.md'],
                         self.git('diff', '--name-only').stdout.splitlines())
        result = self.run_script('push_version.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        self.assertNotEqual(sha, self.env['GITHUB_SHA'])
        self.assertEqual(sha, self.git('ls-remote', 'origin', 'refs/heads/master').stdout.split()[0])
        self.assertIn(f'commit_sha={sha}', Path(self.env['GITHUB_OUTPUT']).read_text())

    def test_advanced_branch_is_not_force_pushed(self):
        self.git('commit', '--allow-empty', '-qm', 'concurrent change')
        self.git('push')
        advanced = self.git('rev-parse', 'HEAD').stdout.strip()
        self.git('switch', '--detach', self.env['GITHUB_SHA'])
        self.assertEqual(0, self.run_script('prepare_version.py').returncode)
        self.assertNotEqual(0, self.run_script('push_version.sh').returncode)
        self.assertEqual(advanced, self.git('ls-remote', 'origin', 'refs/heads/master').stdout.split()[0])

    def test_existing_tag_is_not_replaced(self):
        self.assertEqual(0, self.run_script('prepare_version.py').returncode)
        import re
        constants = (self.root / 'constants.gradle.kts').read_text()
        version = re.search(r'"appVersionCode", (\d+)', constants)[1]
        name = re.search(r'"appVersionName", "([^"]+)"', constants)[1]
        tag = f'v{name}-{version}'
        self.git('tag', tag)
        self.git('push', 'origin', tag)
        self.assertNotEqual(0, self.run_script('push_version.sh').returncode)
        self.assertEqual(self.env['GITHUB_SHA'], self.git('rev-parse', 'HEAD').stdout.strip())

    def test_source_ci_requires_exact_successful_build(self):
        binary = self.root / 'bin'
        binary.mkdir()
        gh = binary / 'gh'
        gh.write_text('''#!/usr/bin/env python3
import os, sys
if sys.argv[1:3] == ['run', 'list']:
    assert '--commit' in sys.argv and os.environ['GITHUB_SHA'] in sys.argv
    print(os.environ['RUNS'])
else:
    print(os.environ['JOBS'])
''')
        gh.chmod(0o755)
        success = dict(databaseId=1, headSha=self.env['GITHUB_SHA'], status='completed', conclusion='success')
        for run, build, passed in ((success, 'success', True), (success, 'skipped', False),
                                   ({**success, 'headSha': 'wrong'}, 'success', False),
                                   ({**success, 'conclusion': 'failure'}, 'success', False),
                                   ({**success, 'status': 'in_progress'}, 'success', False),
                                   (None, 'success', False)):
            result = self.run_script('verify_source_ci.sh', PATH=f'{binary}:{self.env["PATH"]}',
                                     RUNS=json.dumps([run] if run else []),
                                     JOBS=json.dumps({'jobs': [{'name': 'verify-build', 'conclusion': build}]}))
            self.assertEqual(passed, result.returncode == 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
