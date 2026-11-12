# SPDX-License-Identifier: Apache-2.0
"""Fail-closed tests for the public p2 release channel; never contact GitHub."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import rew_update_site

spec = importlib.util.spec_from_file_location(
    "public_p2_publisher", ROOT / "scripts/publish_public_p2.py")
public = importlib.util.module_from_spec(spec)
spec.loader.exec_module(public)


class PublicP2Tests(unittest.TestCase):
    VERSION = "0.1.0.20261112010000"

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.site = self.root / "site"
        (self.site / "features").mkdir(parents=True)
        (self.site / "plugins").mkdir()
        (self.site / "p2.index").write_text("version=1\n", encoding="ascii")
        (self.site / "content.jar").write_bytes(b"metadata")
        (self.site / "artifacts.jar").write_bytes(b"artifacts")
        feature = self.site / "features" / (
            rew_update_site.FEATURE + "_" + self.VERSION + ".jar")
        with zipfile.ZipFile(feature, "w") as jar:
            jar.writestr("feature.xml", '<feature id="' + rew_update_site.FEATURE
                         + '" version="' + self.VERSION + '"/>')
        (self.site / "plugins" / "io.github.niwantha33.rew.ui_0.1.0.jar").write_bytes(
            b"compiled-plugin")

    def test_no_access_token_means_no_release_mutation(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "CI write token"):
                public.publish(type("Options", (), {
                    "site": self.site, "source_sha": "a"*40,
                    "expected_digest": rew_update_site.site_digest(self.site),
                    "expected_version": self.VERSION})())

    def test_known_empty_bootstrap_only(self):
        root = self.root / "updates"
        root.mkdir()
        (root / "README.md").write_text("BOOTSTRAP_ONLY — NO PUBLISHED RELEASE",
                                        encoding="utf-8")
        with patch.object(public, "git", return_value="README.md"):
            self.assertIsNone(public.previous_version(root))
        (root / "README.md").write_text("somebody's untrusted content", encoding="utf-8")
        with patch.object(public, "git", return_value="README.md"):
            with self.assertRaisesRegex(ValueError, "not the known empty bootstrap"):
                public.previous_version(root)

    def test_complete_previous_release_integrity(self):
        root = self.root / "published"
        root.mkdir()
        (root / "site").mkdir()
        for path in self.site.rglob("*"):
            if path.is_file():
                copy = root / "site" / path.relative_to(self.site)
                copy.parent.mkdir(parents=True, exist_ok=True)
                copy.write_bytes(path.read_bytes())
        (root / "releases" / self.VERSION).mkdir(parents=True)
        record = root / "public-channel.properties"
        record.write_text("version=" + self.VERSION + "\nsource=" + "a"*40
                          + "\ndigest=" + rew_update_site.site_digest(self.site) + "\n")
        with patch.object(public, "git", return_value="README.md\npublic-channel.properties\nsite\nreleases"):
            self.assertEqual(public.previous_version(root), self.VERSION)
            (root / "site" / "content.jar").write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "no longer matches"):
                public.previous_version(root)

    def test_public_client_always_asks_eclipse_for_approval(self):
        client = (ROOT / "plugins/io.github.niwantha33.rew.ui/src"
                  "/io/github/niwantha33/rew/ui/PublicP2Updates.java").read_text()
        self.assertIn("PublicP2Channel.fetch()", client)
        self.assertIn("metadata.refreshRepository(release.site(), monitor)", client)
        self.assertIn("getUpdateOperation(", client)
        self.assertIn("List.of(current), new URI[]{release.site()}", client)
        self.assertNotIn("metadata.loadRepository(SITE", client)
        self.assertNotIn("static final URI SITE", client)
        self.assertIn("MessageDialog.openQuestion", client)
        self.assertIn("openUpdateWizard(", client)
        self.assertNotIn("operation.getProvisioningJob(", client)
        self.assertNotIn("schedule(ProvisioningJob", client)
        self.assertNotIn("allowUnsigned", client)
        self.assertIn("isFirmwareBusy()", client)

    def test_public_release_script_rejects_overwrite_and_stale_main(self):
        script = (ROOT / "scripts/publish_public_p2.py").read_text(encoding="utf-8")
        self.assertIn("require_monotonic(version, previous)", script)
        self.assertIn("Immutable release directory already exists", script)
        self.assertIn("main_sha(args.source_sha)", script)
        self.assertIn("HEAD:refs/heads/updates", script)
        self.assertIn("site_digest(archive)", script)
        self.assertIn("GIT_ASKPASS", script)
        self.assertNotIn("REW_UPDATE_SIGNING_KEY_PEM", script)


if __name__ == "__main__":
    unittest.main()
