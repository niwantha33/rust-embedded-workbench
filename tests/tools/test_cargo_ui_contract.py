# SPDX-License-Identifier: Apache-2.0
"""Guard the Cargo UI integration against accidental stub or unsafe behaviour."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "plugins/io.github.niwantha33.rew.ui/src/io/github/niwantha33/rew/ui"
CORE = ROOT / "src/main/java/io/github/niwantha33/rew/cargo"


class CargoUIContractTests(unittest.TestCase):
    def test_extension_is_discoverable(self):
        tree = ET.parse(ROOT / "plugins/io.github.niwantha33.rew.ui/plugin.xml")
        ids = [e.get("id") for e in tree.iter() if e.get("id")]
        self.assertIn("io.github.niwantha33.rew.ui.cargoWorkbench", ids)
        self.assertIn("io.github.niwantha33.rew.ui.openCargo", ids)
        view = (UI / "CargoWorkbenchView.java").read_text()
        perspective = (UI / "RustPerspective.java").read_text()
        self.assertIn("CargoWorkbenchView.ID", perspective)
        self.assertIn('Cargo Workbench...', tree.getroot().find(
            ".//menu[@id='io.github.niwantha33.rew.ui.mainMenu']/command"
            "[@commandId='io.github.niwantha33.rew.ui.openCargo']").get("label"))

    def test_import_links_existing_source_without_copy(self):
        view = (UI / "CargoWorkbenchView.java").read_text()
        self.assertIn("source.createLink(", view)
        self.assertIn("project.create(null)", view)
        self.assertIn("CargoProject.open(", view)
        self.assertNotIn("shutil", view)
        self.assertNotIn("FileUtils.copy", view)

    def test_user_approval_and_no_shell(self):
        view = (UI / "CargoWorkbenchView.java").read_text()
        executor = (CORE / "CargoExecutor.java").read_text()
        self.assertIn("MessageDialog.openConfirm(", view)
        self.assertIn("new CargoExecutor(new CargoExecutor.LocalRunner())", view)
        self.assertIn("monitor::isCanceled", view)
        self.assertIn("CargoToolResolver.current()", executor)
        self.assertIn("new ProcessBuilder(launch)", executor)
        self.assertNotIn("cmd.exe", executor)
        self.assertNotIn("sh -c", executor)
        self.assertNotIn("Runtime.getRuntime().exec", executor)

    def test_console_and_evidence_only_markers(self):
        view = (UI / "CargoWorkbenchView.java").read_text()
        markers = (UI / "CargoProblems.java").read_text()
        self.assertIn("MessageConsoleStream", view)
        self.assertIn("CargoProblems.publish(", view)
        self.assertIn("CargoDiagnostics.parse(", view)
        self.assertIn("IMarker.SOURCE_ID", markers)
        self.assertIn("IMarker.PROBLEM", markers)
        self.assertIn("project.findMarkers(", markers)
        self.assertNotIn("IMarker.SEVERITY_INFO", markers)


if __name__ == "__main__":
    unittest.main()
