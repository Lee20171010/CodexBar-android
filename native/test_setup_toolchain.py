"""Offline check: corrupted downloads must never reach extraction."""
import hashlib
from pathlib import Path
import tempfile
import unittest

from setup_toolchain import verify


class ToolchainIntegrityTest(unittest.TestCase):
    def test_rejects_corrupted_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "fixture.tar.gz"
            archive.write_bytes(b"synthetic archive")
            expected = hashlib.sha256(archive.read_bytes()).hexdigest()
            verify(archive, expected)
            archive.write_bytes(b"modified archive")
            with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
                verify(archive, expected)


if __name__ == "__main__":
    unittest.main()
