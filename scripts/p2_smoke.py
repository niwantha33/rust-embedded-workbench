#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Install the built REW p2 feature in a disposable copy of Eclipse Platform.

Tests Eclipse provisioning only, not Windows UI acceptance or signed updates.
Does not modify the supplied Eclipse installation or source checkout.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

FEATURE = "io.github.niwantha33.rew.feature.feature.group"
ARCHIVE_PREFIX = "io.github.niwantha33.rew.feature_"


def smoke(eclipse: Path, java: Path, site: Path) -> None:
    if not eclipse.is_dir() or not site.is_dir() or not java.is_file():
        raise ValueError("Missing Eclipse installation, Java runtime or verified p2 site")
    feature_jars = list((site / "features").glob(ARCHIVE_PREFIX + "*.jar"))
    if len(feature_jars) != 1:
        raise ValueError("Expected one Rust Embedded Workbench feature jar")
    with zipfile.ZipFile(feature_jars[0]) as jar:
        info = jar.getinfo("feature.xml")
        if info.file_size > 200_000:
            raise ValueError("Feature metadata exceeds bounds")
        element = ET.fromstring(jar.read(info))
    if element.attrib.get("id") != "io.github.niwantha33.rew.feature":
        raise ValueError("Unexpected feature identity")
    version = element.attrib["version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+\.\d{1,14}", version):
        raise ValueError("Missing fully qualified numeric p2 version")
    with tempfile.TemporaryDirectory(prefix="rew-p2-smoke-") as temporary:
        root = Path(temporary) / "eclipse"
        shutil.copytree(eclipse, root, symlinks=True)
        launchers = list((root / "plugins").glob("org.eclipse.equinox.launcher_*.jar"))
        if len(launchers) != 1:
            raise RuntimeError("Unexpected Eclipse Equinox launcher installation")
        ini = (root / "configuration/config.ini").read_text(errors="replace")
        profile = re.search(r"(?m)^eclipse\.p2\.profile=([^\r\n]+)", ini)
        if not profile:
            raise RuntimeError("Cannot determine Eclipse p2 profile")
        common = [
            str(java), "-XX:-UsePerfData", "-jar", str(launchers[0]), "-nosplash",
            "-application", "org.eclipse.equinox.p2.director",
            "-destination", str(root), "-profile", profile.group(1).strip(), "-roaming"
        ]
        install = [*common, "-repository", site.resolve().as_uri(),
                   "-installIU", FEATURE + "/" + version]
        result = subprocess.run(install, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=300, check=False)
        print(result.stdout, flush=True)
        if result.returncode:
            raise RuntimeError(f"Native p2 install failed, exit={result.returncode}")
        installed = subprocess.run([*common, "-listInstalledRoots"], text=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   timeout=90, check=True).stdout
        if FEATURE + "/" + version not in installed:
            raise RuntimeError("REW feature is not an installed p2 root")
        print(f"PASS: Eclipse native p2 installed {FEATURE}/{version} in temporary copy")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eclipse", required=True, type=Path)
    parser.add_argument("--site", required=True, type=Path)
    parser.add_argument("--java", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", "/usr")) / "bin/java")
    args = parser.parse_args()
    smoke(args.eclipse.resolve(), args.java.resolve(), args.site.resolve())


if __name__ == "__main__":
    main()
