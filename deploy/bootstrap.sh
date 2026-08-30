#!/usr/bin/env bash
# One-time prep on the stand host. Run as the Linux user that GitHub Actions will SSH as.
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/studentleague}"
SSH_DIR="${HOME}/.ssh"
PUBKEY="${1:-}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    MISSING=1
  fi
}

MISSING=0
need docker
need curl
need python3
need tar
need rsync
if ! docker compose version >/dev/null 2>&1; then
  echo "Missing: docker compose (plugin v2)" >&2
  MISSING=1
fi
if [[ "${MISSING}" -ne 0 ]]; then
  echo "Install the missing tools, then re-run." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but this user cannot talk to the daemon." >&2
  echo "Add the user to the docker group:  sudo usermod -aG docker \"${USER}\"  && re-login" >&2
  exit 1
fi

mkdir -p "${DEPLOY_ROOT}/deploy" "${DEPLOY_ROOT}/release" "${SSH_DIR}"
chmod 700 "${SSH_DIR}"
chmod 755 "${DEPLOY_ROOT}"

if [[ -n "${PUBKEY}" ]]; then
  touch "${SSH_DIR}/authorized_keys"
  chmod 600 "${SSH_DIR}/authorized_keys"
  if ! grep -qxF "${PUBKEY}" "${SSH_DIR}/authorized_keys"; then
    printf '%s\n' "${PUBKEY}" >> "${SSH_DIR}/authorized_keys"
    echo "Appended the deploy public key to ${SSH_DIR}/authorized_keys"
  fi
fi

if [[ ! -f "${DEPLOY_ROOT}/.env" ]]; then
  if [[ -f "${DEPLOY_ROOT}/deploy/.env.example" ]]; then
    cp "${DEPLOY_ROOT}/deploy/.env.example" "${DEPLOY_ROOT}/.env"
    chmod 600 "${DEPLOY_ROOT}/.env"
    echo "Created ${DEPLOY_ROOT}/.env from example — edit JWT_SECRET, ADMIN_*, DATABASE_PASSWORD, CORS_ORIGINS."
  else
    echo "Create ${DEPLOY_ROOT}/.env before the first deploy (see deploy/.env.example)."
  fi
else
  echo "${DEPLOY_ROOT}/.env already exists — left untouched."
fi

echo "Bootstrap OK. Deploy user=${USER}  path=${DEPLOY_ROOT}"
