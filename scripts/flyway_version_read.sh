#!/usr/bin/env bash
# Read-only Flyway version on studentleague-prod. Does not restart containers or touch /media.
set -euo pipefail
umask 077

POSTGRES="$(docker ps --filter 'name=studentleague-prod-postgres-' --format '{{.Names}}' | head -n 1)"
BACKEND="$(docker ps --filter 'name=studentleague-prod-backend-' --format '{{.Names}}' | head -n 1)"
if [[ -z "${POSTGRES}" || -z "${BACKEND}" ]]; then
  echo "prod postgres or backend container is not running"
  exit 1
fi

echo "MSK_NOW=$(TZ=Europe/Moscow date '+%Y-%m-%d %H:%M:%S %Z')"
echo "postgres=${POSTGRES}"
echo "backend=${BACKEND}"

docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select installed_rank || chr(58) || version || chr(58) || description || chr(58) || success || chr(58) || installed_on from flyway_schema_history order by installed_rank"'

echo "MAX_VERSION=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select version from flyway_schema_history order by installed_rank desc limit 1"')"
echo "ROW_COUNT=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from flyway_schema_history"')"
echo "V47_ROW=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select installed_rank || chr(58) || version || chr(58) || description || chr(58) || success || chr(58) || installed_on from flyway_schema_history where version = '\''47'\''"')"
echo "RULES_TABLE=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select to_regclass('\''public.playoff_qualification_rules'\'') is not null"')"
echo "RULES_COUNT=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from playoff_qualification_rules"')"
echo "BYE_COLUMN=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select column_name || chr(58) || data_type || chr(58) || is_nullable || chr(58) || column_default from information_schema.columns where table_schema = '\''public'\'' and table_name = '\''playoff_slots'\'' and column_name = '\''bye'\''"')"

UPLOAD_SRC="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/app/data/uploads"}}{{.Source}}{{end}}{{end}}' "${BACKEND}")"
case "${UPLOAD_SRC}" in
  /media | /media/*)
    echo "uploads=under-/media-not-stat"
    ;;
  "")
    echo "uploads=absent"
    ;;
  *)
    stat -c "uploads inode=%i mtime=%Y ctime=%Z" "${UPLOAD_SRC}"
    ;;
esac
echo "media=not-touched"

if [[ -f /opt/studentleague/release/VERSION ]]; then
  echo "RELEASE_VERSION=$(tr -d '\r\n' < /opt/studentleague/release/VERSION)"
fi
DUMP=/opt/studentleague/backups/pre-v0.2.68-20261110-203058-kirov/studentleague.dump
if [[ -f "${DUMP}" ]]; then
  echo "DUMP_BYTES=$(stat -c %s "${DUMP}")"
  echo "DUMP_PATH=${DUMP}"
fi

echo "---- flyway log lines since deploy window ----"
docker logs --since 30m "${BACKEND}" 2>&1 | grep -E 'Flyway|Migrating schema|Successfully applied|is up to date|playoff qualification' | grep -v -i -E 'password|jdbc:' || true
echo "FLYWAY_READ_OK"
