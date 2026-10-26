"""Conformance tests for the Python SDK against a real Saar CLI.

Run with SAAR_BIN set to the CLI, or with ``saar`` on PATH::

    SAAR_BIN=/path/to/saar python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

from saar_sdk import SaarClient, SaarCommandError

SAMPLE = b"the quick brown fox jumps over the lazy dog\n" * 64


class SaarClientConformance(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.client = SaarClient()

    def test_version_is_reported(self) -> None:
        version = self.client.version()
        self.assertRegex(version, r"^\d+\.\d+\.\d+")

    def test_round_trip_is_byte_exact(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "sample.txt"
            source.write_bytes(SAMPLE)

            compressed = self.client.compress(source)
            self.assertIsNotNone(compressed.output)
            artifact = Path(compressed.output)
            self.assertTrue(artifact.is_file(), "compression produced no artifact")

            restored = self.client.decompress(artifact, Path(tmp) / "restored.txt")
            self.assertEqual(Path(restored.output).read_bytes(), SAMPLE)

    def test_verify_reports_ok(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "v.txt"
            source.write_bytes(SAMPLE)
            artifact = Path(self.client.compress(source).output)
            self.assertTrue(self.client.verify(artifact).ok)

    def test_default_output_paths(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "plain.bin"
            source.write_bytes(b"\x00\x01\x02" * 32)
            compressed = self.client.compress(source)
            self.assertTrue(str(compressed.output).endswith("plain.bin.saar"))

    def test_missing_input_raises(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SaarCommandError):
                self.client.compress(Path(tmp) / "does-not-exist.txt")

    def test_corrupt_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "corrupt.saar"
            fake.write_bytes(b"not a saar artifact")
            with self.assertRaises(SaarCommandError):
                self.client.decompress(fake, Path(tmp) / "out.bin")

    def test_refuses_to_overwrite_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "a.txt"
            source.write_bytes(SAMPLE)
            artifact = Path(self.client.compress(source).output)
            occupied = Path(tmp) / "occupied.txt"
            occupied.write_bytes(b"keep me")
            with self.assertRaises(SaarCommandError):
                self.client.decompress(artifact, occupied)
            self.assertEqual(occupied.read_bytes(), b"keep me")


if __name__ == "__main__":
    unittest.main()
