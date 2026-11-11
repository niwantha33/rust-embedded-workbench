#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fail-closed Eclipse p2 site checks and signed-channel payload preparation.

This tool does not build an Eclipse product or publish releases. It refuses to
turn an incomplete/nonmatching site into a trusted update manifest.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

FEATURE = "io.github.niwantha33.rew.feature"
FEATURE_GROUP = FEATURE + ".feature.group"
VERSION = re.compile(r"[0-9]{1,8}\.[0-9]{1,8}\.[0-9]{1,8}\.[0-9]{1,14}\Z")
HASH = re.compile(r"[0-9a-f]{40}\Z")
NAME = re.compile(r"(?:p2\.index|(?:content|artifacts)\.(?:jar|xml|xml\.xz)|(?:plugins|features)/[A-Za-z0-9_.-]+\.jar)\Z")
MAX_FILE = 8_388_608
MAX_TOTAL = 67_108_864
MAX_FILES = 100


def _files(site: Path) -> dict[str, Path]:
    if site.is_symlink() or not site.is_dir():
        raise ValueError("Site is missing or a symbolic link")
    result: dict[str, Path] = {}
    total = 0
    for entry in site.rglob("*"):
        # Reject symlinks, special files and subtrees; never follow them.
        if entry.is_symlink() or not (entry.is_dir() or entry.is_file()):
            raise ValueError(f"Unsafe p2 site entry: {entry}")
        if entry.is_dir():
            continue
        if not stat.S_ISREG(entry.stat(follow_symlinks=False).st_mode):
            raise ValueError("Unsafe p2 site file type")
        name = entry.relative_to(site).as_posix()
        if not NAME.fullmatch(name):
            raise ValueError(f"Unexpected p2 site file: {name}")
        size = entry.stat().st_size
        if not 0 < size <= MAX_FILE:
            raise ValueError(f"Empty or oversized p2 file: {name}")
        total += size
        if total > MAX_TOTAL:
            raise ValueError("Total p2 site exceeds the size budget")
        if name in result:
            raise ValueError("Duplicate p2 artifact")
        result[name] = entry
        if len(result) > MAX_FILES:
            raise ValueError("Too many p2 artifacts")
    if not all((key in result for key in ("p2.index",))):
        raise ValueError("Missing p2.index")
    if not ("content.jar" in result or "content.xml" in result or "content.xml.xz" in result):
        raise ValueError("Missing p2 content metadata")
    if not ("artifacts.jar" in result or "artifacts.xml" in result or "artifacts.xml.xz" in result):
        raise ValueError("Missing p2 artifact metadata")
    return result


def site_version(site: Path) -> str:
    files = _files(site)
    features = [(name, path) for name, path in files.items()
                if name.startswith("features/") and name.endswith(".jar")]
    if len(features) != 1:
        raise ValueError("Expected exactly one REW feature archive")
    filename, jar = features[0]
    if not filename.startswith("features/" + FEATURE + "_"):
        raise ValueError("Foreign Eclipse feature")
    try:
        with zipfile.ZipFile(jar) as archive:
            info = archive.getinfo("feature.xml")
            if info.file_size > 200_000 or info.file_size < 1:
                raise ValueError("Feature XML violates size bounds")
            feature = ET.fromstring(archive.read(info))
    except (zipfile.BadZipFile, KeyError, ET.ParseError) as exc:
        raise ValueError("Invalid feature archive") from exc
    if feature.attrib.get("id") != FEATURE:
        raise ValueError("Unexpected feature ID")
    version = feature.attrib.get("version", "")
    if not VERSION.fullmatch(version):
        raise ValueError("Feature version is not a qualified numeric release")
    if filename != f"features/{FEATURE}_{version}.jar":
        raise ValueError("Feature filename and embedded version disagree")
    if not any(name.startswith("plugins/io.github.niwantha33.rew.")
               for name in files):
        raise ValueError("No REW Eclipse plugin in p2 site")
    return version


def sha256_bytes(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def site_digest(site: Path) -> str:
    paths = _files(site)
    digest = hashlib.sha256()
    for name, path in sorted(paths.items()):
        data = (f"{name}\0{path.stat().st_size}\0{sha256_bytes(path)}\n").encode("ascii")
        digest.update(data)
    return digest.hexdigest()


def manifest(site: Path, revision: str, notes: str = "Rust Embedded Workbench update") -> str:
    if not HASH.fullmatch(revision):
        raise ValueError("Release reference must be a 40-digit lowercase commit SHA")
    if not re.fullmatch(r"[A-Za-z0-9 .,:;_()/+-]{1,256}", notes):
        raise ValueError("Release notes must be a bounded single line")
    version = site_version(site)
    entries = _files(site)
    lines = ["schema=1", f"feature={FEATURE_GROUP}", f"version={version}",
             f"revision={revision}", f"directory=releases/{version}",
             f"notes={notes}", f"files={len(entries)}"]
    for index, (path, absolute) in enumerate(sorted(entries.items())):
        lines.extend((f"file.{index}.path={path}",
                      f"file.{index}.size={absolute.stat().st_size}",
                      f"file.{index}.sha256={sha256_bytes(absolute)}"))
    return "\n".join(lines) + "\n"


def current_version(path: Path) -> str | None:
    if not path.exists():
        return None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            version = line[len("version="):]
            if not VERSION.fullmatch(version):
                raise ValueError("Existing update channel has invalid version")
            return version
    raise ValueError("Existing channel has no version")


def require_monotonic(version: str, previous: str | None) -> None:
    if not VERSION.fullmatch(version):
        raise ValueError("Invalid proposed version")
    if previous is not None:
        if not VERSION.fullmatch(previous):
            raise ValueError("Invalid previous version")
        if tuple(map(int, version.split("."))) <= tuple(map(int, previous.split("."))):
            raise ValueError("Refusing update with same or lower version")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)
    v = sub.add_parser("verify", help="Validate p2 artifact tree")
    v.add_argument("--site", type=Path, required=True)
    d = sub.add_parser("digest", help="Print deterministic p2 site SHA-256")
    d.add_argument("--site", type=Path, required=True)
    g = sub.add_parser("manifest", help="Generate the unsigned manifest to sign in CI")
    g.add_argument("--site", type=Path, required=True)
    g.add_argument("--revision", required=True)
    g.add_argument("--output", required=True, type=Path)
    g.add_argument("--notes", default="Rust Embedded Workbench verified update")
    g.add_argument("--previous-channel", type=Path)
    args = p.parse_args()
    try:
        if args.command == "verify":
            print(json.dumps({"version": site_version(args.site), "digest": site_digest(args.site)}))
        elif args.command == "digest":
            print(site_digest(args.site))
        elif args.command == "manifest":
            version = site_version(args.site)
            require_monotonic(version, current_version(args.previous_channel)
                              if args.previous_channel else None)
            if args.output.exists():
                raise ValueError("Refusing to overwrite an existing manifest")
            args.output.write_text(manifest(args.site, args.revision, args.notes),
                                   encoding="ascii", newline="\n")
            print(json.dumps({"version": version, "manifest": str(args.output)}))
        return 0
    except (ValueError, OSError) as exc:
        print(f"FAIL closed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
