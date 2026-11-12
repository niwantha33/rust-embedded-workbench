# SPDX-License-Identifier: Apache-2.0
"""Verify the Eclipse updater avoids stale p2 cache and other feature updates."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
REW = ROOT / "plugins/io.github.niwantha33.rew.ui/src/io/github/niwantha33/rew/ui"
CORE = ROOT / "src/main/java/io/github/niwantha33/rew/updates"


class PublicP2RefreshContract(unittest.TestCase):
    def test_client_uses_immutable_release_and_forced_p2_refresh(self):
        client = (REW / "PublicP2Updates.java").read_text()
        self.assertIn("PublicP2Channel.fetch()", client)
        self.assertIn("metadata.refreshRepository(release.site(), monitor)", client)
        self.assertIn("artifacts.refreshRepository(release.site(), monitor)", client)
        self.assertIn("artifactManager.refreshRepository(release.site(), monitor)", client)
        self.assertIn("List.of(current), new URI[]{release.site()}", client)
        self.assertIn("ui.openUpdateWizard(false, operation, null)", client)
        self.assertIn("MessageDialog.openQuestion(", client)
        self.assertNotIn("operation.getProvisioningJob(", client)
        self.assertNotIn("ui.schedule(", client)
        self.assertNotIn("installUnsigned", client)

    def test_channel_version_path_is_strict_and_bounded(self):
        channel = (CORE / "PublicP2Channel.java").read_text()
        self.assertIn("HttpClient.Redirect.NEVER", channel)
        self.assertIn("stream.readNBytes(MAX_BYTES + 1)", channel)
        self.assertIn('"Cache-Control", "no-cache"', channel)
        self.assertIn('RELEASES + version + "/"', channel)
        self.assertIn("PublicP2ChannelTests", (ROOT / "scripts/verify.sh").read_text())
        self.assertIn("PublicP2ChannelTests", (ROOT / "scripts/verify.ps1").read_text())


if __name__ == "__main__":
    unittest.main()
