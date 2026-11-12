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
