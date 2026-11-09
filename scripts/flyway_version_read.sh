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

docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select installed_rank || chr(58) || version || chr(58) || description || chr(58) || success from flyway_schema_history order by installed_rank"'

echo "MAX_VERSION=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select version from flyway_schema_history order by installed_rank desc limit 1"')"
echo "ROW_COUNT=$(docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from flyway_schema_history"')"

echo "---- flyway log lines since deploy window ----"
docker logs --since 20m "${BACKEND}" 2>&1 | grep -E 'Flyway|Migrating schema|Successfully applied|is up to date' | grep -v -i -E 'password|jdbc:' || true

if [[ -d /opt/studentleague/release ]]; then
  echo "---- release version ----"
  if [[ -f /opt/studentleague/release/VERSION ]]; then
    cat /opt/studentleague/release/VERSION
  fi
fi
echo "FLYWAY_READ_OK"
