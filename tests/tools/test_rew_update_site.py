# SPDX-License-Identifier: Apache-2.0
"""Offline publisher tests; no GitHub token, network or Eclipse runtime required."""
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[2] / "scripts" / "rew_update_site.py"
spec = importlib.util.spec_from_file_location("rew_update_site", SOURCE)
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)


class UpdateSiteTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = "0.1.0.20261111220000"
        (self.root / "features").mkdir()
        (self.root / "plugins").mkdir()
        (self.root / "p2.index").write_text("version=1\n")
        (self.root / "content.jar").write_bytes(b"content")
        (self.root / "artifacts.jar").write_bytes(b"artifacts")
        feature = self.root / "features" / (site.FEATURE + "_" + self.version + ".jar")
        with zipfile.ZipFile(feature, "w") as archive:
            archive.writestr("feature.xml", f'<feature id="{site.FEATURE}" version="{self.version}"/>')
        (self.root / "plugins" / "io.github.niwantha33.rew.ui_0.1.0.jar").write_bytes(b"plugin")

    def test_reproducible_manifest(self):
        self.assertEqual(site.site_version(self.root), self.version)
        a = site.manifest(self.root, "a" * 40)
        self.assertIn("feature=" + site.FEATURE_GROUP, a)
        self.assertIn("revision=" + "a" * 40, a)
        self.assertIn("directory=releases/" + self.version, a)
        self.assertEqual(site.site_digest(self.root), site.site_digest(self.root))

    def test_byte_tampering_changes_digest(self):
        before = site.site_digest(self.root)
        (self.root / "plugins" / "io.github.niwantha33.rew.ui_0.1.0.jar").write_bytes(b"tampered")
        self.assertNotEqual(before, site.site_digest(self.root))

    def test_rename_changes_digest(self):
        before = site.site_digest(self.root)
        file = self.root / "plugins" / "io.github.niwantha33.rew.ui_0.1.0.jar"
        file.rename(file.with_name("io.github.niwantha33.rew.core_0.1.0.jar"))
        self.assertNotEqual(before, site.site_digest(self.root))

    def test_missing_metadata_rejected(self):
        (self.root / "content.jar").unlink()
        with self.assertRaisesRegex(ValueError, "Missing p2 content"):
            site.site_version(self.root)

    def test_foreign_feature_rejected(self):
        feature = next((self.root / "features").iterdir())
        with zipfile.ZipFile(feature, "w") as archive:
            archive.writestr("feature.xml", '<feature id="other.feature" version="0.1.0.123"/>')
        with self.assertRaisesRegex(ValueError, "Unexpected feature"):
            site.site_version(self.root)

    def test_extra_executable_rejected(self):
        (self.root / "run.exe").write_bytes(b"not allowed")
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            site.site_digest(self.root)

    def test_unsigned_manifest_cannot_use_moving_branch(self):
        with self.assertRaisesRegex(ValueError, "40-digit"):
            site.manifest(self.root, "main")

    def test_manifest_escape_rejected(self):
        with self.assertRaisesRegex(ValueError, "single line"):
            site.manifest(self.root, "a" * 40, "good\nfeature=evil")

    def test_rollback_rejected(self):
        site.require_monotonic("0.1.0.21", "0.1.0.20")
        with self.assertRaisesRegex(ValueError, "same or lower"):
            site.require_monotonic("0.1.0.20", "0.1.0.20")
        with self.assertRaisesRegex(ValueError, "same or lower"):
            site.require_monotonic("0.1.0.19", "0.1.0.20")

    def test_symlink_rejected(self):
        alias = self.root / "plugins" / "io.github.niwantha33.rew.alias.jar"
        try:
            alias.symlink_to(self.root / "p2.index")
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks unavailable")
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            site.site_digest(self.root)


if __name__ == "__main__":
    unittest.main()
