"""Storage policy tests use a fake gh executable; no remote deletion is possible."""
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class StorageCleanupTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        binary = self.root / 'gh'
        binary.write_text('''#!/usr/bin/env python3
import os, sys
args = ' '.join(sys.argv[1:])
if 'DELETE' in args or args.startswith('run delete'):
    with open(os.environ['DELETIONS'], 'a') as f: f.write(args + '\\n')
    sys.exit(int(os.environ.get('DELETE_FAIL', '0')))
if '/actions/caches?' in args: print(os.environ.get('CACHES', ''))
elif '/actions/artifacts?' in args: print(os.environ.get('ARTIFACTS', ''))
elif '/actions/runs?' in args: print('')
else: sys.exit(1)
''')
        binary.chmod(0o755)
        self.env = dict(os.environ, PATH=f'{self.root}:{os.environ["PATH"]}', GH_TOKEN='offline-fixture',
                        DELETIONS=str(self.root / 'deletions'), GITHUB_STEP_SUMMARY=str(self.root / 'summary'))
        self.recent = datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
        self.old = (datetime.now(timezone.utc) - timedelta(days=10)).strftime('%Y-%m-%dT%H:%M:%SZ')

    def run_script(self, script, *args, **env):
        return subprocess.run(['bash', str(ROOT / 'scripts/quality' / script), '--repo', 'test/repo', *args],
                              env=dict(self.env, **env), capture_output=True, text=True)

    def cache(self, id, created, accessed):
        return json.dumps(dict(id=id, key=f'cache-{id}', ref='refs/heads/master', size_in_bytes=2*1024*1024,
                               created_at=created, last_accessed_at=accessed))

    def test_recent_creation_and_access_are_protected(self):
        caches = '\n'.join((self.cache(1, self.recent, self.recent),
                             self.cache(2, self.old, self.recent), self.cache(3, self.old, self.old)))
        result = self.run_script('cleanup_github_actions_caches.sh', '--max-total-mb', '1', CACHES=caches)
        self.assertEqual(0, result.returncode, result.stderr)
        deletions = Path(self.env['DELETIONS']).read_text()
        self.assertIn('caches/3', deletions)
        self.assertNotIn('caches/1', deletions)
        self.assertNotIn('caches/2', deletions)
        self.assertIn('remains above threshold', result.stdout)

    def test_cache_dry_run_and_deletion_failure(self):
        caches = self.cache(3, self.old, self.old)
        result = self.run_script('cleanup_github_actions_caches.sh', '--max-total-mb', '1', '--dry-run', CACHES=caches)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(Path(self.env['DELETIONS']).exists())
        self.assertIn('would delete', result.stdout)
        result = self.run_script('cleanup_github_actions_caches.sh', '--max-total-mb', '1', CACHES=caches, DELETE_FAIL='1')
        self.assertNotEqual(0, result.returncode)

    def test_artifact_capacity_cannot_override_seven_days(self):
        artifacts = f'1\trecent\t2097152\t{self.recent}\tfalse\turl\n2\told\t2097152\t{self.old}\tfalse\turl'
        result = self.run_script('cleanup_github_actions_storage.sh', '--artifact-max-total-mb', '1', ARTIFACTS=artifacts)
        self.assertEqual(0, result.returncode, result.stderr)
        deletions = Path(self.env['DELETIONS']).read_text()
        self.assertIn('artifacts/2', deletions)
        self.assertNotIn('artifacts/1', deletions)

    def test_short_retention_is_rejected(self):
        for arg in ('--run-keep-days', '--artifact-keep-days'):
            self.assertNotEqual(0, self.run_script('cleanup_github_actions_storage.sh', arg, '2').returncode)
        self.assertFalse(Path(self.env['DELETIONS']).exists())


if __name__ == '__main__':
    unittest.main()
