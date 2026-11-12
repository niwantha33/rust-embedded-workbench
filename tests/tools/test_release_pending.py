# SPDX-License-Identifier: Apache-2.0
"""No-network tests for public release reconciliation preflight."""
import sys
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from release_pending import needs_release, last_published_source


class ReleasePendingTests(unittest.TestCase):
    def test_unknown_bootstrap_needs_first_release(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertTrue(needs_release("a" * 40, Path(directory) / "missing"))

    def test_no_duplicate_release_for_published_source(self):
        with tempfile.TemporaryDirectory() as directory:
            record = Path(directory) / "public-channel.properties"
            record.write_text("version=0.1.0.20261112001031\n"
                              + "source=" + "a" * 40 + "\n"
                              + "digest=" + "c" * 64 + "\n", encoding="ascii")
            self.assertEqual(last_published_source(record), "a" * 40)
            self.assertFalse(needs_release("a" * 40, record))
            self.assertTrue(needs_release("b" * 40, record))

    def test_invalid_records_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "metadata"
            for record in ("", "source=" + "a" * 40 + "\n",
                           "version=0.1.0.123\nsource=" + "a"*40
                           + "\ndigest=" + "c"*64 + "\n",
                           "version=0.1.0.20261112001031\nsource=main\n"
                           + "digest=" + "c"*64,
                           "version=0.1.0.20261112001031\nsource=" + "a"*40
                           + "\ndigest=" + "c"*64 + "\nsource=" + "b"*40):
                path.write_text(record, encoding="ascii")
                with self.assertRaises(ValueError):
                    needs_release("a"*40, path)
            with self.assertRaises(ValueError):
                needs_release("main", Path(directory) / "missing")

    def test_preflight_workflow_has_scheduled_idempotent_gate(self):
        workflow = (ROOT / ".github/workflows/publish-updates.yml").read_text()
        self.assertIn("schedule:", workflow)
        self.assertIn("cron: '17 * * * *'", workflow)
        self.assertIn("scripts/release_pending.py", workflow)
        self.assertIn("needs.preflight.outputs.ready == 'true'", workflow)
        self.assertIn("contents: read", workflow)
        self.assertEqual(workflow.count("contents: write"), 1)


if __name__ == "__main__":
    unittest.main()
