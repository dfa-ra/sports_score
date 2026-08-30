#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path

from normalize_ssh_key import normalize_private_key


class NormalizeSshKeyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workdir = Path(tempfile.mkdtemp(prefix="ssh-key-"))
        cls.private = cls.workdir / "id_ed25519"
        subprocess.run(
            ["ssh-keygen", "-t", "ed25519", "-f", str(cls.private), "-N", "", "-q"],
            check=True,
        )
        cls.pem = cls.private.read_text(encoding="utf-8")
        cls.pub = Path(str(cls.private) + ".pub").read_text(encoding="utf-8")

    def test_passthrough(self) -> None:
        self.assertTrue(normalize_private_key(self.pem).startswith("-----BEGIN"))

    def test_literal_escaped_newlines(self) -> None:
        escaped = self.pem.replace("\n", "\\n").strip()
        got = normalize_private_key(escaped)
        self.assertIn("BEGIN OPENSSH PRIVATE KEY", got)
        self.assertTrue(got.endswith("\n"))

    def test_crlf(self) -> None:
        got = normalize_private_key(self.pem.replace("\n", "\r\n"))
        self.assertNotIn("\r", got)

    def test_rejects_public_key(self) -> None:
        with self.assertRaises(ValueError) as ctx:
            normalize_private_key(self.pub)
        self.assertEqual(str(ctx.exception), "public")

    def test_base64_file(self) -> None:
        import base64

        encoded = base64.b64encode(self.pem.encode()).decode()
        got = normalize_private_key("", encoded)
        self.assertEqual(got, normalize_private_key(self.pem))


if __name__ == "__main__":
    unittest.main()
