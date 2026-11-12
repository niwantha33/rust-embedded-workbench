# SPDX-License-Identifier: Apache-2.0
"""Guard the beginner landing page and host-only starter project."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "plugins/io.github.niwantha33.rew.ui"
PLUGIN = UI / "plugin.xml"
JAVA = UI / "src/io/github/niwantha33/rew/ui"


class GuidedOnboardingTests(unittest.TestCase):
    def test_menu_perspective_handler_wired(self):
        root = ET.parse(PLUGIN).getroot()
        views = {node.attrib.get("id") for node in root.findall(".//view")}
        commands = {node.attrib.get("id") for node in root.findall(".//command")}
        self.assertIn("io.github.niwantha33.rew.ui.startHere", views)
        self.assertIn("io.github.niwantha33.rew.ui.openStartHere", commands)
        self.assertIn('label="Start Here — Guided Learning..."', PLUGIN.read_text())
        perspective = (JAVA / "RustPerspective.java").read_text()
        self.assertIn("left.addView(StartHereView.ID)", perspective)
        self.assertIn("showView(StartHereView.ID)", (JAVA / "OpenStartHereHandler.java").read_text())

    def test_guided_mode_does_not_claim_hardware(self):
        source = (JAVA / "StartHereView.java").read_text()
        self.assertIn("report.readyForCargo()", source)
        self.assertIn("HostToolchain.inspect(monitor::isCanceled)", source)
        self.assertIn("You do NOT need WinGet or espflash for the first lesson", source)
        self.assertIn("documentation candidates, NOT verified builds or debuggers", source)
        self.assertIn("getPreferenceStore().setValue(MODE_PREFERENCE, expert)", source)
        self.assertNotIn("ProcessBuilder(", source)

    def test_safe_first_project_host_only(self):
        starter = (ROOT / "src/main/java/io/github/niwantha33/rew/core/StarterProject.java").read_text()
        cargo = (JAVA / "CargoWorkbenchView.java").read_text()
        self.assertIn("Files.createDirectory(directory)", starter)
        self.assertIn("StandardOpenOption.CREATE_NEW", starter)
        self.assertIn("src/main.rs", starter)
        self.assertIn("targetBox.select(0)", cargo)
        self.assertIn("StarterProject.create(", cargo)
        self.assertIn("MessageDialog.openConfirm(", cargo)
        self.assertIn("public void createFirstProject()", cargo)
        self.assertNotIn("cargo new", cargo)


if __name__ == "__main__":
    unittest.main()
