#!/usr/bin/env bash
# Read-only: confirm Flyway V46 on studentleague-prod. No restarts, no /media.
set -euo pipefail
umask 077

POSTGRES="$(docker ps --filter 'name=studentleague-prod-postgres-' --format '{{.Names}}' | head -n 1)"
BACKEND="$(docker ps --filter 'name=studentleague-prod-backend-' --format '{{.Names}}' | head -n 1)"
if [[ -z "${POSTGRES}" || -z "${BACKEND}" ]]; then
  echo "prod postgres or backend container is not running"
  exit 1
fi

echo "KIROV_NOW=$(TZ=Europe/Kirov date '+%Y-%m-%d %H:%M:%S %Z')"
echo "postgres=${POSTGRES}"
echo "backend=${BACKEND}"

row="$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select version || chr(58) || description || chr(58) || success from flyway_schema_history where version = '\''46'\''"')"
printf 'v46_row=%s\n' "${row}"
case "${row}" in
  46:*:true) ;;
  *)
    echo "V46 is not applied"
    exit 1
    ;;
esac

echo "---- flyway log lines ----"
docker logs --since 30m "${BACKEND}" 2>&1 | grep -E 'Flyway|Migrating schema|Successfully applied|is up to date|version "46' | grep -v -i -E 'password|jdbc:' || true

UPLOAD_SRC="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/data/uploads"}}{{.Source}}{{end}}{{end}}' "${BACKEND}")"
meta_line() {
  local path="$1"
  local label="$2"
  if [[ -z "${path}" ]]; then
    echo "${label}=absent"
    return 0
  fi
  case "${path}" in
    /media | /media/*)
      echo "${label}=under-/media-not-stat"
      return 0
      ;;
  esac
  if [[ ! -d "${path}" ]]; then
    echo "${label}=missing"
    return 0
  fi
  stat -c "${label} inode=%i mtime=%Y ctime=%Z" "${path}"
}
BEFORE_FILE="$(ls -d /opt/studentleague/backups/pre-v0.2.65-*-kirov | head -n 1)/storage-stat-before.txt"
{
  meta_line "${UPLOAD_SRC}" uploads
  meta_line /media media
} > /tmp/storage-stat-after.txt
echo "---- storage stat before ----"
cat "${BEFORE_FILE}"
echo "---- storage stat after ----"
cat /tmp/storage-stat-after.txt
if ! cmp -s "${BEFORE_FILE}" /tmp/storage-stat-after.txt; then
  echo "uploads or /media metadata changed since the pre-release backup"
  exit 1
fi
rm -f /tmp/storage-stat-after.txt
echo "media_and_uploads_unchanged_since_backup=yes"
echo "FLYWAY_CHECK_OK"
