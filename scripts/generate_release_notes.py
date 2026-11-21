#!/usr/bin/env python3
"""Generate Cat Browser release notes from commits between version tags."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

TAG_PATTERN = re.compile(r"^v?\d+\.\d+\.\d+(?:-beta(?:\.(?:[1-9]|[1-8]\d|9[0-8]))?)?$")
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
CONVENTIONAL_COMMIT = re.compile(
    r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]+)\))?(?P<breaking>!)?:\s*(?P<subject>.+)$",
    re.IGNORECASE,
)

CATEGORY_ORDER = (
    "✨ New Features",
    "🐛 Bug Fixes",
    "⚡ Performance Improvements",
    "🎨 UI/UX",
    "⚠️ Breaking Changes",
    "🔧 Technical Changes",
    "📦 Other Changes",
)

TYPE_TO_CATEGORY = {
    "feat": "✨ New Features",
    "fix": "🐛 Bug Fixes",
    "perf": "⚡ Performance Improvements",
    "ui": "🎨 UI/UX",
    "style": "🔧 Technical Changes",
    "refactor": "🔧 Technical Changes",
    "docs": "🔧 Technical Changes",
    "test": "🔧 Technical Changes",
    "build": "🔧 Technical Changes",
    "ci": "🔧 Technical Changes",
    "chore": "🔧 Technical Changes",
    "revert": "🔧 Technical Changes",
}


def run_git(*arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def escape_markdown(value: str) -> str:
    """Escape commit subjects so they cannot alter the generated Markdown structure."""
    return re.sub(r"([\\`*_{}\[\]()#+\-.!|<>~])", r"\\\1", value)

def parse_commit_record(record: str) -> tuple[str, str, str] | None:
    parts = record.split("\x1f", 2)
    if len(parts) != 3:
        return None
    sha, subject, body = (part.strip() for part in parts)
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not subject:
        return None
    return sha, subject, body


def classify_commit(subject: str, body: str = "") -> tuple[str, str]:
    """Return a release-note section and a human-readable change description."""
    match = CONVENTIONAL_COMMIT.match(subject)
    if match is None:
        return "📦 Other Changes", subject

    commit_type = match.group("type").lower()
    scope = match.group("scope")
    description = match.group("subject").strip()
    if scope:
        description = f"{scope}: {description}"

    is_breaking = bool(match.group("breaking")) or bool(
        re.search(r"(?im)^BREAKING CHANGE:\s*", body)
    )
    if is_breaking:
        return "⚠️ Breaking Changes", description

    return TYPE_TO_CATEGORY.get(commit_type, "📦 Other Changes"), description


def find_previous_tag(tag: str) -> str | None:
    result = run_git(
        "describe",
        "--tags",
        "--abbrev=0",
        "--match",
        "v[0-9]*",
        "--match",
        "[0-9]*",
        f"{tag}^",
        check=False,
    )
    previous = result.stdout.strip()
    return previous if result.returncode == 0 and previous else None

def load_commits(tag: str, previous_tag: str | None) -> list[tuple[str, str, str]]:
    revision = f"{previous_tag}..{tag}" if previous_tag else tag
    result = run_git("log", "--no-merges", "--format=%H%x1f%s%x1f%b%x1e", revision)
    commits: list[tuple[str, str, str]] = []
    for record in result.stdout.split("\x1e"):
        parsed = parse_commit_record(record)
        if parsed is not None:
            commits.append(parsed)
    return commits


def render_release_notes(
    tag: str,
    repository: str,
    commits: list[tuple[str, str, str]],
    previous_tag: str | None,
) -> str:
    version = tag.removeprefix("v")
    sections: dict[str, list[str]] = defaultdict(list)

    for sha, subject, body in commits:
        category, description = classify_commit(subject, body)
        short_sha = sha[:7]
        commit_url = f"https://github.com/{repository}/commit/{sha}"
        bullet = (
            f"- {escape_markdown(description)} "
            f"([`{short_sha}`]({commit_url}))"
        )
        sections[category].append(bullet)

    output = [f"## Cat Browser {version}", ""]
    if not commits:
        output.extend(["No commits were found in this release range.", ""])
    else:
        for category in CATEGORY_ORDER:
            entries = sections.get(category)
            if entries:
                output.extend([f"### {category}", "", *entries, ""])

    if previous_tag:
        changelog_url = f"https://github.com/{repository}/compare/{previous_tag}...{tag}"
    else:
        changelog_url = f"https://github.com/{repository}/commits/{tag}"
    output.extend([f"### Full Changelog", "", f"[{previous_tag or 'Repository history'} → {tag}]({changelog_url})", ""])
    return "\n".join(output).rstrip() + "\n"


def generate_release_notes(tag: str, repository: str) -> str:
    if not TAG_PATTERN.fullmatch(tag):
        raise ValueError("Tag must use vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-beta[.N].")
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise ValueError("Repository must use owner/name format.")

    verified_tag = run_git("rev-parse", "--verify", f"{tag}^{{commit}}", check=False)
    if verified_tag.returncode != 0:
        raise ValueError(f"Git tag does not exist locally: {tag}")

    previous_tag = find_previous_tag(tag)
    commits = load_commits(tag, previous_tag)
    return render_release_notes(tag, repository, commits, previous_tag)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Release tag, for example v0.2.8-beta.1")
    parser.add_argument("--repo", required=True, help="GitHub repository in owner/name format")
    parser.add_argument("--output", type=Path, help="Write notes to this file instead of stdout")
    args = parser.parse_args()

    try:
        notes = generate_release_notes(args.tag, args.repo)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Release notes generation failed: {error}", file=sys.stderr)
        return 1

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(notes, encoding="utf-8")
    else:
        sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
