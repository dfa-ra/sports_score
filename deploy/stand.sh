#!/usr/bin/env bash
# Download a GitHub Release (backend JAR + web tarball) and recreate stand containers.
# Expected layout (DEPLOY_ROOT):
#   .env
#   deploy/          (this folder, uploaded by CI)
#   release/         (filled by this script)
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/studentleague}"
RELEASE_DIR="${DEPLOY_ROOT}/release"
COMPOSE_FILE="${DEPLOY_ROOT}/deploy/docker-compose.yml"
TOKEN_FILE="${DEPLOY_ROOT}/.deploy-token"

RELEASE_TAG="${RELEASE_TAG:-}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

if [[ -z "${GITHUB_TOKEN}" && -f "${TOKEN_FILE}" ]]; then
  GITHUB_TOKEN="$(tr -d '\r\n' < "${TOKEN_FILE}")"
fi
rm -f "${TOKEN_FILE}"

if [[ -z "${RELEASE_TAG}" || -z "${GITHUB_REPOSITORY}" ]]; then
  echo "RELEASE_TAG and GITHUB_REPOSITORY are required" >&2
  exit 1
fi

if [[ ! -f "${DEPLOY_ROOT}/.env" ]]; then
  echo "Missing ${DEPLOY_ROOT}/.env — copy deploy/.env.example and fill secrets first." >&2
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing ${COMPOSE_FILE} — CI should rsync the deploy/ folder first." >&2
  exit 1
fi

WEB_PORT="$(grep -E '^WEB_PORT=' "${DEPLOY_ROOT}/.env" | tail -n 1 | cut -d= -f2- | tr -d '[:space:]' | tr -d "\"'" || true)"
WEB_PORT="${WEB_PORT:-80}"

mkdir -p "${RELEASE_DIR}"
rm -rf "${RELEASE_DIR}/web"
rm -f "${RELEASE_DIR}/backend.jar" "${RELEASE_DIR}/web.tar.gz"

export RELEASE_TAG GITHUB_REPOSITORY GITHUB_TOKEN RELEASE_DIR
python3 - <<'PY'
import json
import os
import sys
import urllib.error
import urllib.request

repo = os.environ["GITHUB_REPOSITORY"]
tag = os.environ["RELEASE_TAG"]
token = os.environ.get("GITHUB_TOKEN", "")
out_dir = os.environ["RELEASE_DIR"]

headers = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "studentleague-stand",
}
if token:
    headers["Authorization"] = f"Bearer {token}"

url = f"https://api.github.com/repos/{repo}/releases/tags/{tag}"
try:
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers)) as resp:
        release = json.load(resp)
except urllib.error.HTTPError as exc:
    body = exc.read().decode("utf-8", "replace")
    print(f"Failed to read release {tag}: HTTP {exc.code}\n{body}", file=sys.stderr)
    sys.exit(1)

assets = release.get("assets") or []
backend = None
web = None
for asset in assets:
    name = asset.get("name") or ""
    if name.startswith("student-league-backend-") and name.endswith(".jar"):
        backend = asset
    elif name.startswith("student-league-web-") and name.endswith(".tar.gz"):
        web = asset

if backend is None or web is None:
    names = [a.get("name") for a in assets]
    print(f"Release {tag} is missing backend JAR or web tarball. Assets: {names}", file=sys.stderr)
    sys.exit(1)


def download(asset, dest):
    req_headers = {
        "Accept": "application/octet-stream",
        "User-Agent": "studentleague-stand",
    }
    if token:
        req_headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(asset["url"], headers=req_headers)
    with urllib.request.urlopen(request) as resp, open(dest, "wb") as fh:
        while True:
            chunk = resp.read(256 * 1024)
            if not chunk:
                break
            fh.write(chunk)
    print(f"downloaded {asset['name']} -> {dest} ({os.path.getsize(dest)} bytes)")


os.makedirs(out_dir, exist_ok=True)
download(backend, os.path.join(out_dir, "backend.jar"))
download(web, os.path.join(out_dir, "web.tar.gz"))
print(tag, file=open(os.path.join(out_dir, "VERSION"), "w"))
PY

mkdir -p "${RELEASE_DIR}/web"
tar -xzf "${RELEASE_DIR}/web.tar.gz" -C "${RELEASE_DIR}/web"

echo "Starting containers from ${RELEASE_TAG}..."
docker compose \
  --project-directory "${DEPLOY_ROOT}" \
  -f "${COMPOSE_FILE}" \
  --project-name studentleague-dev \
  up -d --build --remove-orphans

HEALTH_URL="http://127.0.0.1:${WEB_PORT}/api/v1/health"
echo "Waiting for ${HEALTH_URL}"
ok=0
for _ in $(seq 1 60); do
  if curl -fsS "${HEALTH_URL}" | grep -q UP; then
    ok=1
    break
  fi
  sleep 5
done

if [[ "${ok}" -ne 1 ]]; then
  echo "Stand did not become healthy in time." >&2
  docker compose --project-directory "${DEPLOY_ROOT}" -f "${COMPOSE_FILE}" --project-name studentleague-dev ps >&2 || true
  docker compose --project-directory "${DEPLOY_ROOT}" -f "${COMPOSE_FILE}" --project-name studentleague-dev logs --tail=80 backend >&2 || true
  exit 1
fi

echo "Stand is up: ${HEALTH_URL}  (release ${RELEASE_TAG})"
