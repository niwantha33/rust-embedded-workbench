#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Publish a verified public Eclipse p2 preview using the built-in GITHUB_TOKEN.

No custom signing key. Installation is subject to native Eclipse p2 trust
prompts: unsigned/untrusted artifacts MUST NOT be installed silently.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

from rew_update_site import require_monotonic, site_digest, site_version

REPO = "niwantha33/rust-embedded-workbench"


def git(*args: str, cwd: Path | None = None) -> str:
    done = subprocess.run(["git", *args], cwd=cwd, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=False)
    if done.returncode:
        raise RuntimeError(f"Git {args[0]} failed, exit={done.returncode}")
    return done.stdout.decode("utf-8").strip()


def main_sha(source: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Expected an immutable source SHA")
    if git("rev-parse", "HEAD") != source:
        raise ValueError("Source checkout mismatch")
    remote = git("ls-remote", "origin", "refs/heads/main").split()
    if len(remote) != 2 or remote[0] != source:
        raise ValueError("Main advanced; refusing a stale release")


def previous_version(root: Path) -> str | None:
    names = git("ls-tree", "--name-only", "HEAD", cwd=root).splitlines()
    record = root / "public-channel.properties"
    if record.is_file():
        if "site" not in names or not (root / "site").is_dir():
            raise ValueError("Previous release is incomplete")
        fields: dict[str, str] = {}
        for line in record.read_text(encoding="ascii").splitlines():
            name, delimiter, value = line.partition("=")
            if not delimiter or name in fields or name not in ("version", "source", "digest"):
                raise ValueError("Unexpected previous release metadata")
            fields[name] = value
        if set(fields) != {"version", "source", "digest"}:
            raise ValueError("Incomplete previous release metadata")
        if not re.fullmatch(r"[a-f0-9]{40}", fields["source"]) or not re.fullmatch(
                r"[a-f0-9]{64}", fields["digest"]):
            raise ValueError("Invalid previous release provenance")
        if site_version(root / "site") != fields["version"] or site_digest(
                root / "site") != fields["digest"]:
            raise ValueError("Published site no longer matches its recorded digest")
        if not (root / "releases" / fields["version"]).is_dir():
            raise ValueError("Missing immutable release archive")
        return fields["version"]
    if names == ["README.md"] and "BOOTSTRAP_ONLY" in (
            root / "README.md").read_text(encoding="utf-8"):
        return None
    raise ValueError("Public update branch is not the known empty bootstrap")


def publish(args: argparse.Namespace) -> None:
    if not os.environ.get("GH_TOKEN"):
        raise ValueError("Missing short-lived CI write token")
    if not re.fullmatch(r"[a-f0-9]{64}", args.expected_digest):
        raise ValueError("Missing verified artifact digest")
    if not args.site.is_dir():
        raise ValueError("No qualified Eclipse p2 site")
    version = site_version(args.site)
    if version != args.expected_version or site_digest(args.site) != args.expected_digest:
        raise ValueError("Release version or p2 integrity mismatch")
    main_sha(args.source_sha)

    with tempfile.TemporaryDirectory(prefix="rew-public-updates-") as tmp:
        root = Path(tmp) / "updates"
        git("fetch", "--no-tags", "origin",
            "refs/heads/updates:refs/remotes/origin/updates")
        git("worktree", "add", "--detach", str(root), "refs/remotes/origin/updates")
        previous = previous_version(root)
        require_monotonic(version, previous)
        archive = root / "releases" / version
        if archive.exists():
            raise ValueError("Immutable release directory already exists")
        shutil.copytree(args.site, archive, symlinks=False)
        if site_digest(archive) != args.expected_digest:
            raise ValueError("Archived artifact mismatch")
        site = root / "site"
        if site.exists():
            if site.is_symlink() or not site.is_dir():
                raise ValueError("Unexpected site object")
            shutil.rmtree(site)
        shutil.copytree(archive, site, symlinks=False)
        if site_digest(site) != args.expected_digest:
            raise ValueError("Published artifact mismatch")
        (root / "public-channel.properties").write_text(
            f"version={version}\nsource={args.source_sha}\ndigest={args.expected_digest}\n",
            encoding="ascii", newline="\n")
        (root / ".nojekyll").write_text("", encoding="ascii")
        (root / "README.md").write_text(
            "# Public Rust Embedded Workbench Eclipse p2 updates\n\n"
            "This GitHub branch is only for verified CI-built Eclipse p2 releases. "
            "Source lives on main. The current p2 repository is in site/ and "
            "immutable release copies are under releases/.\n\n"
            "Preview content may be unsigned. Eclipse will request explicit "
            "trust/licence approval before installing untrusted artifacts.\n\n"
            "Public site: https://raw.githubusercontent.com/"
            "niwantha33/rust-embedded-workbench/updates/site/\n", encoding="utf-8")
        git("add", "--", "README.md", ".nojekyll", "public-channel.properties",
            "site", "releases/" + version, cwd=root)
        git("-c", "user.name=rew-public-p2-ci",
            "-c", "user.email=rew-public-p2-ci@users.noreply.github.com",
            "commit", "-m", "updates: publish qualified p2 " + version, cwd=root)
        main_sha(args.source_sha)

        # No persisted credentials and no external secret needed.
        askpass = Path(tmp) / "askpass.sh"
        askpass.write_text(
            "#!/bin/sh\n"
            "case \"$1\" in\n"
            "  *Username*) printf 'x-access-token' ;;\n"
            "  *Password*) printf '%s' \"$GH_TOKEN\" ;;\n"
            "  *) exit 1 ;;\n"
            "esac\n", encoding="ascii")
        askpass.chmod(0o700)
        env = dict(os.environ, GIT_ASKPASS=str(askpass), GIT_TERMINAL_PROMPT="0")
        pushed = subprocess.run(
            ["git", "-C", str(root), "push",
             "https://github.com/" + REPO + ".git", "HEAD:refs/heads/updates"],
            env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if pushed.returncode:
            raise RuntimeError("Could not fast-forward public updates branch")
        print(f"Published verified public p2 {version}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--expected-digest", required=True)
    try:
        publish(parser.parse_args())
        return 0
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print("Public p2 publication blocked: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
