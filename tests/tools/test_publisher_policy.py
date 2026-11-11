# SPDX-License-Identifier: Apache-2.0
"""Guard the trust boundaries of the GitHub-hosted release workflow."""
import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("rew_publish_update", SCRIPTS / "publish_update.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublisherPolicyTests(unittest.TestCase):
    def test_no_private_key_or_token_fails_before_git_access(self):
        options = SimpleNamespace(source_sha="a" * 40, expected_digest="b" * 64,
                                  expected_version="0.1.0.20261111220000",
                                  public_key=Path("not-present"), site=Path("not-present"))
        for env in ({}, {"GH_TOKEN": "test-token"}, {"REW_UPDATE_SIGNING_KEY_PEM": "not-a-key"}):
            with patch.dict(os.environ, env, clear=True):
                with self.assertRaisesRegex(ValueError, "signing identity"):
                    publisher.publisher(options)

    def test_release_workflow_never_publishes_cli_only_bootstrap(self):
        release = (ROOT / ".github/workflows/publish-updates.yml").read_text()
        self.assertIn("features/io.github.niwantha33.rew.feature/feature.xml", release)
        self.assertIn("releng/rew-update-public-key.pem", release)
        self.assertIn("scripts/p2_smoke.py", release)
        self.assertIn("needs.preflight.outputs.ready == 'true'", release)
        self.assertIn("scripts/publish_update.py", release)
        self.assertIn("permissions:\n  contents: read", release)
        self.assertEqual(release.count("contents: write"), 1)
        self.assertIn("persist-credentials: false", release)
        self.assertIn("environment: rew-update-publisher", release)

    def test_private_key_is_not_a_repository_file(self):
        tracked = list(ROOT.rglob("*.pem"))
        for path in tracked:
            self.assertNotIn("PRIVATE KEY", path.read_text(errors="replace"))
        self.assertFalse((ROOT / "releng" / "rew-update-private-key.pem").exists())

    def test_feature_and_signer_separation(self):
        policy = (ROOT / "docs/SECURE_UPDATES.md").read_text()
        self.assertIn("Ed25519", policy)
        self.assertIn("not today's CLI prototype", policy)
        self.assertIn("never overwrite", policy.lower() if "never overwrite" in policy.lower() else
                      "never overwrite" + policy.lower())


if __name__ == "__main__":
    unittest.main()
