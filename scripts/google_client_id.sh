#!/usr/bin/env bash
# Print the public Google OAuth client id for web builds.
# VITE_GOOGLE_CLIENT_ID in the environment wins (GitHub variable).
# Otherwise the committed id in deploy/google-client-id is used.
set -euo pipefail
if [[ -n "${VITE_GOOGLE_CLIENT_ID:-}" ]]; then
  printf '%s\n' "${VITE_GOOGLE_CLIENT_ID}"
  exit 0
fi
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
id="$(grep -E '^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$' "${root}/deploy/google-client-id" | head -n 1 || true)"
if [[ -z "${id}" ]]; then
  echo "deploy/google-client-id has no public Google client id" >&2
  exit 1
fi
printf '%s\n' "${id}"
