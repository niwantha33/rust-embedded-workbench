#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check if a GitHub main commit needs public p2 publication.

This is only a read-only preflight gate. Actual publishing still requires the
full Tycho build, native Eclipse provisioning, SHA checks and fast-forward push.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+\.[0-9]{8,14}\Z")


def last_published_source(record: Path | None) -> str | None:
    if record is None or not record.exists():
        # The known bootstrap branch lacks a record; the publisher itself
        # validates that no foreign/partial content is allowed.
        return None
    if not record.is_file() or record.is_symlink() or record.stat().st_size > 8192:
        raise ValueError("Malformed published channel record")
    parsed: dict[str, str] = {}
    for line in record.read_text(encoding="ascii").splitlines():
        key, sep, value = line.partition("=")
        if not sep or key not in ("version", "source", "digest") or key in parsed:
            raise ValueError("Malformed published channel metadata")
        parsed[key] = value
    if set(parsed) != {"version", "source", "digest"}:
        raise ValueError("Incomplete published channel record")
    if not VERSION.fullmatch(parsed["version"]) or not SHA.fullmatch(
            parsed["source"]) or not DIGEST.fullmatch(parsed["digest"]):
        raise ValueError("Invalid published version, commit or digest")
    return parsed["source"]


def needs_release(source: str, record: Path | None) -> bool:
    if not SHA.fullmatch(source or ""):
        raise ValueError("Expected immutable lowercase Git source SHA")
    return last_published_source(record) != source


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--record", type=Path, required=True)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    try:
        needed = needs_release(args.source, args.record)
    except (ValueError, OSError, UnicodeError) as error:
        print(f"Fail closed: {error}", file=sys.stderr)
        return 1
    value = "true" if needed else "false"
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as out:
            out.write("ready=" + value + "\n")
    print("RELEASE_REQUIRED=" + value)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
