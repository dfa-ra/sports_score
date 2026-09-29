#!/usr/bin/env python3
"""Set GOOGLE_CLIENT_ID and APP_PUBLIC_URL in an existing stand .env.

Other keys are left unchanged. The script does not print their values.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

CLIENT_ID_RE = re.compile(r"^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$")
PUBLIC_URL_RE = re.compile(r"^https?://[A-Za-z0-9._:-]+$")


def upsert(original: str, client_id: str, app_public_url: str) -> str:
    if not CLIENT_ID_RE.fullmatch(client_id):
        raise ValueError("GOOGLE_CLIENT_ID is empty or not a public Google client id")
    if not PUBLIC_URL_RE.fullmatch(app_public_url):
        raise ValueError("APP_PUBLIC_URL must be an http or https origin without a path")

    updates = {
        "GOOGLE_CLIENT_ID": client_id,
        "APP_PUBLIC_URL": app_public_url,
    }
    newline = "\r\n" if "\r\n" in original else "\n"
    lines = original.splitlines()
    seen: set[str] = set()
    out: list[str] = []
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            out.append(line)
            continue
        key, _value = line.split("=", 1)
        if key in updates:
            out.append(f"{key}={updates[key]}")
            seen.add(key)
        else:
            out.append(line)
    missing = [key for key in updates if key not in seen]
    if missing and out and out[-1] != "":
        out.append("")
    for key in missing:
        out.append(f"{key}={updates[key]}")
    return newline.join(out) + newline


def main() -> None:
    if len(sys.argv) != 4:
        print(
            "usage: upsert_public_env.py ENV_PATH CLIENT_ID APP_PUBLIC_URL",
            file=sys.stderr,
        )
        sys.exit(2)
    env_path = Path(sys.argv[1])
    client_id = sys.argv[2].strip()
    app_public_url = sys.argv[3].strip()
    if not env_path.is_file():
        print(f"missing env file: {env_path}", file=sys.stderr)
        sys.exit(1)
    try:
        updated = upsert(env_path.read_text(encoding="utf-8"), client_id, app_public_url)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
    env_path.write_text(updated, encoding="utf-8")
    env_path.chmod(0o600)
    print(f"GOOGLE_CLIENT_ID={client_id}")
    print(f"APP_PUBLIC_URL={app_public_url}")


if __name__ == "__main__":
    main()
