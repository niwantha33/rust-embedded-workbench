# SPDX-License-Identifier: Apache-2.0
"""Guard Eclipse packaging identity and source/catalog synchronisation."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PKG = "io.github.niwantha33.rew"
BASE = ROOT / "plugins" / (PKG + ".core")


class EclipseBootstrapTests(unittest.TestCase):
    def test_plugin_sources_match_java_core(self):
        for group, files in {
            "core": ("Board.java", "BoardRegistry.java", "BuildPlan.java", "ToolDetector.java"),
            "cargo": ("CargoProject.java", "CargoPlan.java", "CargoExecutor.java",
                      "CargoToolResolver.java"),
            "setup": ("SetupCatalog.java", "SetupExecutor.java", "ProbeScanner.java",
                      "HostToolchain.java"),
            "updates": ("UpdateManifest.java", "UpdateVersion.java", "SignedChannel.java",
                        "GitHubUpdateChannel.java", "BoundedUpdateHttp.java",
                        "VerifiedRelease.java", "VerifiedUpdateStager.java", "PublicP2Channel.java"),
        }.items():
            for name in files:
                relative = Path("io/github/niwantha33/rew") / group / name
                original = (ROOT / "src/main/java" / relative).read_bytes()
                shipped = (BASE / "src" / relative).read_bytes()
                self.assertEqual(original, shipped, str(relative) + " has drifted")

    def test_catalog_bytes_match(self):
        self.assertEqual((ROOT / "catalog/boards.tsv").read_bytes(),
                         (BASE / "catalog/boards.tsv").read_bytes())

    def test_extension_ids_are_not_zephyr(self):
        plugin = ROOT / "plugins" / (PKG + ".ui") / "plugin.xml"
        doc = ET.parse(plugin).getroot()
        self.assertEqual(doc.tag, "plugin")
        ids = [element.attrib["id"] for element in doc.iter() if "id" in element.attrib]
        self.assertIn(PKG + ".ui.boardExplorer", ids)
        self.assertIn(PKG + ".ui.setupCenter", ids)
        self.assertIn(PKG + ".ui.cargoWorkbench", ids)
        self.assertIn(PKG + ".ui.openCargo", ids)
        self.assertIn(PKG + ".ui.openSetup", ids)
        self.assertIn(PKG + ".ui.perspective", ids)
        self.assertFalse(any(".zew." in identifier for identifier in ids))
        self.assertEqual(len(ids), len(set(ids)), "duplicate Eclipse extension IDs")

    def test_installable_feature_and_category(self):
        f = ET.parse(ROOT / "features" / (PKG + ".feature") / "feature.xml").getroot()
        self.assertEqual(f.attrib["id"], PKG + ".feature")
        self.assertEqual(f.attrib["version"], "0.1.0.qualifier")
        self.assertEqual({p.attrib["id"] for p in f.findall("plugin")},
                         {PKG + ".core", PKG + ".ui"})
        site = ET.parse(ROOT / "releng/repository/category.xml").getroot()
        self.assertEqual(site.find("feature").attrib["id"], PKG + ".feature")

    def test_no_unverified_trust_key(self):
        # Absent in the preview; when provisioned, both keys must agree.
        import sys
        sys.path.insert(0, str(ROOT / "scripts"))
        from verify_signing_public_key import PEM, BUNDLED, verify
        self.assertEqual(PEM.exists(), BUNDLED.exists())
        if PEM.exists():
            self.assertEqual(len(verify()), 44)
        self.assertTrue((ROOT / "scripts/p2_smoke.py").is_file())


if __name__ == "__main__":
    unittest.main()
