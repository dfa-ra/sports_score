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

echo "FLYWAY_CHECK_OK"
