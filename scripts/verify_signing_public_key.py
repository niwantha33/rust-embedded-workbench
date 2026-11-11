#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Verify that the installed product pins exactly the release publisher's Ed25519 key.

Use the *public* key only. The private key must remain in an approved secure
GitHub Actions environment secret, never in source control or an Actions log.
"""
from __future__ import annotations
import base64
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PEM = ROOT / "releng/rew-update-public-key.pem"
BUNDLED = ROOT / "plugins/io.github.niwantha33.rew.ui/keys/publisher-key.b64"
ED25519_SPKI_PREFIX = bytes.fromhex("302a300506032b6570032100")

def verify() -> bytes:
    if not PEM.is_file() or not BUNDLED.is_file():
        raise ValueError("Missing reviewed publisher public key and/or installed trust anchor")
    if PEM.is_symlink() or BUNDLED.is_symlink():
        raise ValueError("Publisher trust anchor cannot be a symlink")
    pem = PEM.read_text(encoding="ascii")
    matched = re.fullmatch(
        r"-----BEGIN PUBLIC KEY-----\s+([A-Za-z0-9+/=\s]+?)\s+-----END PUBLIC KEY-----\s*",
        pem
    )
    if matched is None:
        raise ValueError("Expected a PUBLIC KEY PEM, not a certificate or private key")
    der = base64.b64decode(re.sub(r"\s+", "", matched.group(1)), validate=True)
    installed = base64.b64decode(BUNDLED.read_text(encoding="ascii").strip(), validate=True)
    if len(der) != 44 or not der.startswith(ED25519_SPKI_PREFIX):
        raise ValueError("Only a raw Ed25519 public key is supported")
    if der != installed:
        raise ValueError("Product's pinned key does not match release-publisher identity")
    return der

if __name__ == "__main__":
    verify()
    print("PASS Ed25519 public key matches installed update trust anchor")
