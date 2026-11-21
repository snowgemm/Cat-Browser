import tempfile
import unittest
from pathlib import Path

from generate_release_notes import (
    classify_commit,
    escape_markdown,
    generate_release_notes,
    render_release_notes,
)


class ReleaseNotesClassificationTest(unittest.TestCase):
    def test_conventional_commit_categories(self):
        self.assertEqual(
            classify_commit("feat(bookmarks): add folders"),
            ("✨ New Features", "bookmarks: add folders"),
        )
        self.assertEqual(
            classify_commit("fix: prevent updater crash"),
            ("🐛 Bug Fixes", "prevent updater crash"),
        )
        self.assertEqual(
            classify_commit("perf: reduce tab switch latency"),
            ("⚡ Performance Improvements", "reduce tab switch latency"),
        )
        self.assertEqual(
            classify_commit("style: format Kotlin sources"),
            ("🔧 Technical Changes", "format Kotlin sources"),
        )

    def test_breaking_change_marker_in_subject_or_body(self):
        self.assertEqual(
            classify_commit("feat!: replace update API"),
            ("⚠️ Breaking Changes", "replace update API"),
        )
        self.assertEqual(
            classify_commit("feat: replace settings schema", "BREAKING CHANGE: migrate preferences"),
            ("⚠️ Breaking Changes", "replace settings schema"),
        )

    def test_rejects_invalid_beta_sequence(self):
        for tag in ("v0.2.8-beta.0", "v0.2.8-beta.99"):
            with self.subTest(tag=tag):
                with self.assertRaises(ValueError):
                    generate_release_notes(tag, "snowgemm/Cat-Browser")

    def test_escapes_markdown_control_characters_in_subjects(self):
        escaped = escape_markdown("<literal> [label](https://example.com) ~~tilde~~")
        self.assertEqual(r"\<literal\> \[label\]\(https://example\.com\) \~\~tilde\~\~", escaped)

    def test_non_conventional_commit_is_not_dropped(self):
        self.assertEqual(
            classify_commit("Improve browser startup"),
            ("📦 Other Changes", "Improve browser startup"),
        )


class ReleaseNotesRenderingTest(unittest.TestCase):
    def test_groups_commits_and_adds_traceable_commit_links(self):
        commits = [
            ("a" * 40, "feat: add share menu", ""),
            ("b" * 40, "fix(updater): reject invalid digest", ""),
            ("c" * 40, "style: format code", ""),
        ]
        result = render_release_notes("v0.2.8-beta.1", "snowgemm/Cat-Browser", commits, "v0.2.7-beta")

        self.assertIn("## Cat Browser 0.2.8-beta.1", result)
        self.assertIn("### ✨ New Features", result)
        self.assertIn("### 🐛 Bug Fixes", result)
        self.assertIn("### 🔧 Technical Changes", result)
        self.assertIn("https://github.com/snowgemm/Cat-Browser/commit/" + "a" * 40, result)
        self.assertIn("/compare/v0.2.7-beta...v0.2.8-beta.1", result)

    def test_empty_range_is_explicit_and_does_not_invent_changes(self):
        result = render_release_notes("v1.0.0", "snowgemm/Cat-Browser", [], None)
        self.assertIn("No commits were found in this release range.", result)
        self.assertNotIn("✨ New Features", result)


class ReleaseNotesGitIntegrationTest(unittest.TestCase):
    def test_generates_notes_from_real_local_tag_range(self):
        import subprocess

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.email", "test@example.com"], check=True)
            (root / "README.md").write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "README.md"], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "chore: initial commit"], check=True)
            subprocess.run(["git", "-C", str(root), "tag", "v0.1.0"], check=True)
            (root / "README.md").write_text("feature\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "commit", "-qam", "feat: add reading list"], check=True)
            subprocess.run(["git", "-C", str(root), "tag", "v0.2.0"], check=True)

            previous = Path.cwd()
            try:
                import os
                os.chdir(root)
                notes = generate_release_notes("v0.2.0", "snowgemm/Cat-Browser")
            finally:
                os.chdir(previous)

            self.assertIn("add reading list", notes)
            self.assertNotIn("initial commit", notes)
            self.assertIn("compare/v0.1.0...v0.2.0", notes)


if __name__ == "__main__":
    unittest.main()
