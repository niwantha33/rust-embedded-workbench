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
        self.assertIn("scripts/verify_signing_public_key.py", release)
        self.assertIn("--eclipse \"$RUNNER_TEMP/rew-platform/eclipse\"", release)
        self.assertIn("sha512sum --check", release)
        self.assertIn("permissions:\n  contents: read", release)
        self.assertEqual(release.count("contents: write"), 1)
        self.assertIn("persist-credentials: false", release)
        self.assertIn("environment: rew-update-publisher", release)

    def test_first_update_branch_must_be_clean_bootstrap(self):
        source = (SCRIPTS / "publish_update.py").read_text(encoding="utf-8")
        self.assertIn("BOOTSTRAP_ONLY — NO PUBLISHED RELEASE", source)
        self.assertIn('existing_files == ["README.md"]', source)
        self.assertIn("Existing channel signature is not Ed25519", source)
        self.assertIn("Update branch contains partial, foreign or unsigned metadata", source)

    def test_verify_embedded_key_and_reject_mismatch(self):
        import base64
        import zipfile
        import verify_signing_public_key as keyguard
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            source = directory / "rew-update-public-key.pem"
            shipped = directory / "publisher-key.b64"
            site = directory / "site"
            (site / "plugins").mkdir(parents=True)
            fake_der = keyguard.ED25519_SPKI_PREFIX + bytes(range(32))
            source.write_text("-----BEGIN PUBLIC KEY-----\\n"
                    + base64.b64encode(fake_der).decode("ascii")
                    + "\\n-----END PUBLIC KEY-----\\n", encoding="ascii")
            shipped.write_text(base64.b64encode(fake_der).decode("ascii"), encoding="ascii")
            jar = site / "plugins" / "io.github.niwantha33.rew.ui_0.1.0.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("keys/publisher-key.b64", shipped.read_bytes())
            with patch.object(keyguard, "PEM", source), patch.object(
                    keyguard, "BUNDLED", shipped):
                self.assertEqual(keyguard.verify(), fake_der)
                keyguard.verify_packaged(site)
                shipped.write_text(base64.b64encode(fake_der[:-1] + b"x").decode("ascii"))
                with self.assertRaisesRegex(ValueError, "does not match"):
                    keyguard.verify()
                shipped.write_text(base64.b64encode(fake_der).decode("ascii"))
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr("keys/publisher-key.b64", "bad")
                with self.assertRaises(Exception):
                    keyguard.verify_packaged(site)

    def test_private_key_is_not_a_repository_file(self):
        tracked = list(ROOT.rglob("*.pem"))
        for path in tracked:
            self.assertNotIn("PRIVATE KEY", path.read_text(errors="replace"))
        self.assertFalse((ROOT / "releng" / "rew-update-private-key.pem").exists())

    def test_feature_and_signer_separation(self):
        policy = (ROOT / "docs/SECURE_UPDATES.md").read_text()
        self.assertIn("Ed25519", policy)
        self.assertIn("not today's CLI prototype", policy)
        self.assertIn("not today's CLI prototype", policy)


if __name__ == "__main__":
    unittest.main()
