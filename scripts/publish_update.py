#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Publish a previously qualified p2 update from CI, never a developer desktop.

Requires exact source SHA, sealed site digest, pinned Ed25519 public key,
secret private key in environment and a temporary GitHub Actions write token.
The only remote mutation is a final non-force push of the updates branch.
"""
from __future__ import annotations

import argparse
import base64
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

from rew_update_site import (current_version, manifest, require_monotonic,
                             site_digest, site_version)

OWNER_REPO = "niwantha33/rust-embedded-workbench"


def run(*args: str, cwd: Path | None = None, input_bytes: bytes | None = None) -> str:
    process = subprocess.run(args, cwd=cwd, input=input_bytes,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             check=False)
    if process.returncode:
        raise RuntimeError(f"Command failed: {args[0]} {args[1] if len(args)>1 else ''} "
                           f"(exit {process.returncode})")
    return process.stdout.decode("utf-8", errors="strict").strip()


def publisher(args: argparse.Namespace) -> None:
    key = os.environ.get("REW_UPDATE_SIGNING_KEY_PEM", "")
    token = os.environ.get("GH_TOKEN", "")
    if not key.startswith("-----BEGIN PRIVATE KEY-----") or not token:
        raise ValueError("CI signing identity or temporary repository token is not configured")
    if not args.source_sha or len(args.source_sha) != 40:
        raise ValueError("Missing immutable source commit")
    if not args.expected_digest or len(args.expected_digest) != 64:
        raise ValueError("Missing verified artifact digest")
    if not args.public_key.is_file() or args.public_key.is_symlink():
        raise ValueError("Missing pinned update verification key")
    if site_digest(args.site) != args.expected_digest:
        raise ValueError("Transferred p2 release differs from qualified artifact")
    version = site_version(args.site)
    if version != args.expected_version:
        raise ValueError("Packaged feature version mismatch")
    if run("git", "rev-parse", "HEAD") != args.source_sha:
        raise ValueError("Checked-out source differs from CI-qualified source")
    if run("git", "ls-remote", "origin", "refs/heads/main").split()[0] != args.source_sha:
        raise ValueError("Main has advanced; refuse to publish stale binaries")

    with tempfile.TemporaryDirectory(prefix="rew-publisher-") as tempdir:
        temp = Path(tempdir)
        private = temp / "private.pem"
        private.write_text(key.rstrip("\n") + "\n", encoding="ascii")
        private.chmod(0o600)
        # Binary DER must be compared as raw bytes, not through UTF-8.
        own = subprocess.run(["openssl", "pkey", "-in", str(private), "-pubout",
                              "-outform", "DER"], capture_output=True, check=True).stdout
        trusted = subprocess.run(["openssl", "pkey", "-pubin", "-in", str(args.public_key),
                                  "-outform", "DER"], capture_output=True, check=True).stdout
        if own != trusted:
            raise ValueError("Signing key does not match the public key pinned by the product")

        existing = subprocess.run(["git", "ls-remote", "--exit-code", "origin",
                                   "refs/heads/updates"], capture_output=True)
        if existing.returncode not in (0, 2):
            raise RuntimeError("Could not confirm update branch state")
        worktree = temp / "updates"
        if existing.returncode == 0:
            run("git", "fetch", "--no-tags", "origin",
                "refs/heads/updates:refs/remotes/origin/updates")
            run("git", "worktree", "add", "--detach", str(worktree), "refs/remotes/origin/updates")
            previous = current_version(worktree / "channel.properties")
            raw_sig = base64.b64decode((worktree / "channel.sig").read_text().strip(), validate=True)
            signed = temp / "previous.bin"
            signed.write_bytes(raw_sig)
            run("openssl", "pkeyutl", "-verify", "-pubin", "-inkey",
                str(args.public_key), "-rawin", "-in",
                str(worktree / "channel.properties"), "-sigfile", str(signed))
        else:
            previous = None
            run("git", "worktree", "add", "--detach", str(worktree), args.source_sha)
            run("git", "-C", str(worktree), "switch", "--orphan", "updates")
            # Remove only files in the dedicated temporary worktree.
            run("git", "-C", str(worktree), "clean", "-f", "-d", "-x")
        require_monotonic(version, previous)
        version_root = worktree / "releases" / version
        if version_root.exists():
            raise ValueError("Immutable release path already exists")
        version_root.mkdir(parents=True)
        shutil.copytree(args.site, version_root, dirs_exist_ok=True)
        if site_digest(version_root) != args.expected_digest:
            raise ValueError("Release changed while being staged")
        run("git", "-C", str(worktree), "add", "--", "releases/" + version)
        run("git", "-C", str(worktree), "-c", "user.name=rew-update-ci",
            "-c", "user.email=rew-update-ci@users.noreply.github.com",
            "commit", "-m", "updates: stage immutable p2 " + version)
        artifact_revision = run("git", "-C", str(worktree), "rev-parse", "HEAD")
        channel_bytes = manifest(args.site, artifact_revision).encode("ascii")
        channel_path = worktree / "channel.properties"
        channel_path.write_bytes(channel_bytes)
        sign = subprocess.run(["openssl", "pkeyutl", "-sign", "-rawin",
                               "-inkey", str(private)], input=channel_bytes,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        signature = sign.stdout
        if len(signature) != 64:
            raise ValueError("Unexpected Ed25519 signature output")
        proof = temp / "signature.bin"
        proof.write_bytes(signature)
        run("openssl", "pkeyutl", "-verify", "-pubin", "-inkey", str(args.public_key),
            "-rawin", "-in", str(channel_path), "-sigfile", str(proof))
        (worktree / "channel.sig").write_text(
            base64.b64encode(signature).decode("ascii") + "\n", encoding="ascii")
        run("git", "-C", str(worktree), "add", "--", "channel.properties", "channel.sig")
        run("git", "-C", str(worktree), "-c", "user.name=rew-update-ci",
            "-c", "user.email=rew-update-ci@users.noreply.github.com",
            "commit", "-m", "updates: activate signed p2 " + version)

        # Last optimistic safety check: avoid a stale release after main advances.
        if run("git", "ls-remote", "origin", "refs/heads/main").split()[0] != args.source_sha:
            raise ValueError("Main advanced after packaging; no release pushed")
        auth = temp / "askpass.sh"
        auth.write_text("#!/bin/sh\n"
                        "case \"$1\" in\n"
                        "  *Username*) printf 'x-access-token' ;;\n"
                        "  *Password*) printf '%s' \"$GH_TOKEN\" ;;\n"
                        "  *) exit 1 ;;\n"
                        "esac\n", encoding="ascii")
        auth.chmod(0o700)
        env = dict(os.environ, GIT_ASKPASS=str(auth), GIT_TERMINAL_PROMPT="0")
        subprocess.run(["git", "-C", str(worktree), "push",
                        "https://github.com/" + OWNER_REPO + ".git",
                        "HEAD:refs/heads/updates"],
                       env=env, check=True, stdout=subprocess.DEVNULL)
        print(f"Published signed p2 version {version}; source {args.source_sha}")
        # Worktree is in the temporary directory; cleanup is best effort.
        subprocess.run(["git", "worktree", "prune"], capture_output=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", required=True, type=Path)
    parser.add_argument("--public-key", required=True, type=Path)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--expected-digest", required=True)
    args = parser.parse_args()
    try:
        publisher(args)
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print("Fail closed: release not published (" + str(error) + ")", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
