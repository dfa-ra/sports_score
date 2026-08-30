#!/usr/bin/env python3
"""Normalize a GitHub Actions SSH private-key secret into an OpenSSH file.

Does not print key material. Exit codes:
  0  wrote a file that looks like a private key
  2  input looks like a public key
  3  input is empty / not a private key
"""
from __future__ import annotations

import argparse
import base64
import os
import re
import sys
from pathlib import Path


def _decode_base64(value: str) -> str:
    compact = re.sub(r"\s+", "", value)
    return base64.b64decode(compact, validate=False).decode("utf-8", "replace")


def normalize_private_key(raw: str, raw_b64: str = "") -> str:
    text = (raw or "").lstrip("\ufeff").strip()
    b64 = (raw_b64 or "").lstrip("\ufeff").strip()

    if b64 and ("BEGIN" not in text or "PRIVATE KEY" not in text):
        text = _decode_base64(b64).lstrip("\ufeff").strip()

    if (text.startswith('"') and text.endswith('"')) or (text.startswith("'") and text.endswith("'")):
        text = text[1:-1].strip()

    if "\n" not in text and "\\n" in text:
        text = text.replace("\\r\\n", "\n").replace("\\n", "\n")

    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\u2014", "-").replace("\u2013", "-")
    lines = [line.strip() for line in text.split("\n") if line.strip()]
    text = "\n".join(lines)

    if not text:
        raise ValueError("empty")

    first = lines[0]
    if first.startswith(("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-", "ssh-dss")):
        raise ValueError("public")

    if "BEGIN" not in text or "PRIVATE KEY" not in text:
        raise ValueError("not-private")

    return text + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("output")
    args = parser.parse_args()

    raw = os.environ.get("DEV_SSH_KEY", "")
    raw_b64 = os.environ.get("DEV_SSH_KEY_BASE64", "")
    try:
        key = normalize_private_key(raw, raw_b64)
    except ValueError as exc:
        code = str(exc)
        if code == "public":
            print(
                "DEV_SSH_KEY looks like a PUBLIC key (.pub). "
                "Paste the private file (-----BEGIN … PRIVATE KEY-----).",
                file=sys.stderr,
            )
            return 2
        print(
            "DEV_SSH_KEY is empty or is not a PEM/OpenSSH private key "
            "(need a block with BEGIN / END PRIVATE KEY).",
            file=sys.stderr,
        )
        return 3

    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(key, encoding="utf-8")
    os.chmod(path, 0o600)
    print(f"wrote {path} ({len(key)} bytes, {key.count(chr(10))} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
