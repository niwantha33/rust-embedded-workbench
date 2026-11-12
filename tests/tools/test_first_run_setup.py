# SPDX-License-Identifier: Apache-2.0
"""Source-level regression contracts for the first-run toolchain experience."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "plugins/io.github.niwantha33.rew.ui/src/io/github/niwantha33/rew/ui"
CORE = ROOT / "src/main/java/io/github/niwantha33/rew/setup"


class HostSetupUiTests(unittest.TestCase):
    def test_cargo_gate_and_verification(self):
        source = (UI / "CargoWorkbenchView.java").read_text()
        self.assertIn("Setup Rust & Cargo...", source)
        self.assertIn("Verify Tools", source)
        self.assertIn("HostToolchain.inspect(monitor::isCanceled)", source)
        self.assertIn("runButton.setEnabled(false)", source)
        self.assertIn("tools.readyForCargo()", source)

    def test_batch_approval_cancellation_and_failure_gating(self):
        source = (UI / "SetupCenterView.java").read_text()
        self.assertIn("Install Rust Essentials...", source)
        self.assertIn("Verify Rust Tools", source)
        self.assertIn("Cancel Setup", source)
        self.assertIn("HostToolchain.essentials(", source)
        self.assertIn("MessageDialog.openConfirm(", source)
        self.assertIn("installHost(id, true, monitor::isCanceled)", source)
        self.assertIn("Stopped on failed step", source)
        self.assertIn("result.succeeded()", source)

    def test_first_run_menu_is_direct_and_official_fallback_is_always_visible(self):
        plugin = (ROOT / "plugins/io.github.niwantha33.rew.ui/plugin.xml").read_text()
        source = (UI / "SetupCenterView.java").read_text()
        self.assertIn('id="io.github.niwantha33.rew.ui.openRustSetup"', plugin)
        self.assertIn('commandId="io.github.niwantha33.rew.ui.openRustSetup"', plugin)
        self.assertIn('label="Rust &amp; Cargo Setup..."', plugin)
        self.assertIn("Open Official Rust Installer...", source)
        self.assertIn('https://rust-lang.org/tools/install/', source)
        self.assertIn("managedAvailable", source)
        self.assertIn("!running.get() && managedAvailable", source)

    def test_setup_center_uses_verified_observations_not_path_hints(self):
        source = (UI / "SetupCenterView.java").read_text(encoding="utf-8")
        self.assertIn("SetupReadiness.evaluate(step, report)", source)
        self.assertIn("boardChoice.add(\"Choose a board", source)
        self.assertIn("boardChoice.select(i + 1)", source)
        self.assertIn("SetupCatalog.hostEssentials(SetupExecutor.isWindows())", source)
        self.assertIn("Recheck Installed Tools", source)
        self.assertNotIn("On PATH (version unverified)", source)
        self.assertNotIn("ToolDetector.isOnCurrentPath", source)

    def test_compact_ready_screen_and_separate_driver_guidance(self):
        source = (UI / "SetupCenterView.java").read_text(encoding="utf-8")
        readiness = (CORE / "SetupReadiness.java").read_text(encoding="utf-8")
        self.assertIn("Setup complete for this PC", source)
        self.assertIn("Continue — Start Here...", source)
        self.assertIn("showView(StartHereView.ID)", source)
        self.assertIn("Show Advanced Setup Details...", source)
        self.assertIn("setAdvancedVisible(!latest.readyForCargo())", source)
        self.assertIn("advancedLayout.exclude = !visible", source)
        self.assertIn("SetupReadiness::displayName", source)
        self.assertIn("OPTIONAL — BOARD SETUP", source)
        self.assertIn("Open Board Driver Guide...", source)
        self.assertIn("Scan Connected USB (read only)", source)
        self.assertIn("SWT.READ_ONLY | SWT.V_SCROLL", source)
        self.assertIn("driverStep()", source)
        self.assertIn("updateBoardGuide()", source)
        self.assertIn("getSite().getPage().showView(StartHereView.ID)", source)
        self.assertIn('case "install-rustup" -> "Rustup (toolchain manager)"', readiness)
        self.assertIn('case "install-stable" -> "Rust compiler and Cargo"', readiness)
        self.assertIn("default -> step.label()", readiness)
        self.assertNotIn('col("Setup component"', source)
        self.assertNotIn("col(\"Component\", 255, s -> s.label())", source)

    def test_allowlisted_installers_not_kernel_driver_replacement(self):
        catalog = (CORE / "SetupCatalog.java").read_text()
        runner = (CORE / "SetupExecutor.java").read_text()
        self.assertIn("hostEssentials(boolean windows)", catalog)
        self.assertIn("Rustlang.Rustup", catalog)
        self.assertIn('"rustup", "component", "add", "rust-src"', catalog)
        self.assertIn("HostToolchain.current(program)", runner)
        self.assertNotIn('ProcessBuilder("cmd"', runner)


if __name__ == "__main__":
    unittest.main()
