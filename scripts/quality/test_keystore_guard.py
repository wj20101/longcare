"""Repository-root and tracked-keystore checks must work with directory or file git metadata."""

from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / "scripts/quality/verify_no_tracked_keystore_files.sh"


class KeystoreGuardTest(unittest.TestCase):
    def fixture(self, directory, git_file=False):
        repository = Path(directory) / "repo"
        repository.mkdir()
        command = ["git", "init", "--quiet"]
        if git_file:
            command += ["--separate-git-dir", str(Path(directory) / "metadata")]
        subprocess.run(command + [str(repository)], check=True, capture_output=True)
        return repository

    def guard(self, repository):
        return subprocess.run(["bash", str(GUARD), str(repository)], text=True, capture_output=True)

    def test_clean_repository_passes_with_both_git_metadata_forms(self):
        for git_file in (False, True):
            with self.subTest(git_file=git_file), tempfile.TemporaryDirectory() as directory:
                repository = self.fixture(directory, git_file)
                self.assertEqual(git_file, (repository / ".git").is_file())
                result = self.guard(repository)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_tracked_keystore_is_blocked_with_both_metadata_forms(self):
        for git_file in (False, True):
            with self.subTest(git_file=git_file), tempfile.TemporaryDirectory() as directory:
                repository = self.fixture(directory, git_file)
                for name in ("release.jks", "debug.keystore"):
                    (repository / name).write_text("test fixture; not a key")
                subprocess.run(["git", "-C", str(repository), "add", "release.jks", "debug.keystore"], check=True)
                result = self.guard(repository)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("tracked keystore files detected", result.stderr)

    def test_subdirectory_cannot_hide_a_key_tracked_at_the_repository_root(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.fixture(directory, git_file=True)
            (repository / "release.jks").write_text("test fixture; not a key")
            subprocess.run(["git", "-C", str(repository), "add", "release.jks"], check=True)
            child = repository / "app"
            child.mkdir()
            result = self.guard(child)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("expected repository root", result.stderr)

    def test_non_repository_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.guard(Path(directory))
            self.assertNotEqual(0, result.returncode)
            self.assertIn("git repository not found", result.stderr)


if __name__ == "__main__":
    unittest.main()
