#!/usr/bin/env bash
# Read-only backup of the running studentleague-prod stand.
# Does not restart containers, does not write the database, does not touch /media.
set -euo pipefail
umask 077

DEPLOY_ROOT=/opt/studentleague

if TZ=Europe/Kirov date +%Y%m%d-%H%M%S >/dev/null 2>&1; then
  STAMP="$(TZ=Europe/Kirov date +%Y%m%d-%H%M%S)"
  TZ_NAME=Europe/Kirov
else
  STAMP="$(TZ=Europe/Moscow date +%Y%m%d-%H%M%S)"
  TZ_NAME=Europe/Moscow
fi

DEST="${DEPLOY_ROOT}/backups/pre-v0.2.67-${STAMP}-kirov"
case "${DEST}" in
  /media | /media/*)
    echo "refusing to write under /media"
    exit 1
    ;;
esac

mkdir -p "${DEST}"
chmod 700 "${DEST}"

echo "BACKUP_DIR=${DEST}"
echo "KIROV_STAMP=${STAMP}"
echo "TZ_NAME=${TZ_NAME}"
echo "KIROV_NOW=$(TZ="${TZ_NAME}" date '+%Y-%m-%d %H:%M:%S %Z')"

mapfile -t NAMES < <(docker ps --filter 'name=studentleague-prod-' --format '{{.Names}}')
if [[ "${#NAMES[@]}" -eq 0 ]]; then
  echo "no running studentleague-prod containers"
  exit 1
fi
echo "containers:"
printf '  %s\n' "${NAMES[@]}"

pick() {
  local suffix="$1"
  local name
  for name in "${NAMES[@]}"; do
    case "${name}" in
      studentleague-prod-"${suffix}"-*)
        printf '%s' "${name}"
        return 0
        ;;
    esac
  done
  return 1
}

WEB="$(pick web)" || { echo "web container missing"; exit 1; }
BACKEND="$(pick backend)" || { echo "backend container missing"; exit 1; }
POSTGRES="$(pick postgres)" || { echo "postgres container missing"; exit 1; }

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

{
  meta_line "${UPLOAD_SRC}" uploads
  meta_line /media media
} > "${DEST}/storage-stat-before.txt"
cat "${DEST}/storage-stat-before.txt"

if [[ -f "${DEPLOY_ROOT}/release/VERSION" ]]; then
  cp -a "${DEPLOY_ROOT}/release/VERSION" "${DEST}/VERSION"
fi
if [[ -f "${DEPLOY_ROOT}/release/backend.jar" ]]; then
  cp -a "${DEPLOY_ROOT}/release/backend.jar" "${DEST}/backend-release.jar"
fi
if [[ -f "${DEPLOY_ROOT}/release/web.tar.gz" ]]; then
  cp -a "${DEPLOY_ROOT}/release/web.tar.gz" "${DEST}/web-release.tar.gz"
fi
if [[ -d "${DEPLOY_ROOT}/release/web" ]]; then
  tar -C "${DEPLOY_ROOT}/release/web" -czf "${DEST}/web-dist-release.tar.gz" .
fi

docker exec "${WEB}" tar -C /usr/share/nginx/html -czf - . > "${DEST}/web-dist.tar.gz"
docker cp "${BACKEND}:/app/app.jar" "${DEST}/backend-container.jar"

if [[ ! -f "${DEPLOY_ROOT}/deploy/nginx.conf" ]]; then
  echo "missing ${DEPLOY_ROOT}/deploy/nginx.conf"
  exit 1
fi
cp -a "${DEPLOY_ROOT}/deploy/nginx.conf" "${DEST}/nginx.conf"
if [[ -f "${DEPLOY_ROOT}/deploy/nginx-headers.conf" ]]; then
  cp -a "${DEPLOY_ROOT}/deploy/nginx-headers.conf" "${DEST}/nginx-headers.conf"
fi
docker exec "${WEB}" sh -c 'if [ -f /etc/nginx/conf.d/default.conf ]; then cat /etc/nginx/conf.d/default.conf; else cat /etc/nginx/templates/default.conf.template; fi' > "${DEST}/nginx-runtime.conf"

AVAIL="$(df -B1 "${DEST}" | awk 'NR==2 {print $4}')"
echo "disk_avail_bytes=${AVAIL}"
if [[ "${AVAIL}" -gt 2000000000 ]]; then
  BACKEND_IMAGE="$(docker inspect -f '{{.Image}}' "${BACKEND}")"
  WEB_IMAGE="$(docker inspect -f '{{.Image}}' "${WEB}")"
  printf '%s\n' "${BACKEND_IMAGE}" > "${DEST}/backend-image-id.txt"
  printf '%s\n' "${WEB_IMAGE}" > "${DEST}/web-image-id.txt"
  docker save "${BACKEND_IMAGE}" | gzip -1 > "${DEST}/backend-image.tar.gz"
  docker save "${WEB_IMAGE}" | gzip -1 > "${DEST}/web-image.tar.gz"
  echo "image_save=yes"
else
  echo "image_save=skipped reason=low_disk avail=${AVAIL}" | tee "${DEST}/image-save.txt"
fi

docker exec "${POSTGRES}" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "${DEST}/studentleague.dump"
magic="$(head -c 5 "${DEST}/studentleague.dump" || true)"
if [[ "${magic}" != "PGDMP" ]]; then
  echo "pg_dump did not produce a custom-format archive"
  exit 1
fi

docker exec "${POSTGRES}" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select installed_rank || chr(58) || version || chr(58) || description || chr(58) || success from flyway_schema_history order by installed_rank"' > "${DEST}/flyway-schema-history.txt"

for required in web-dist.tar.gz backend-container.jar nginx.conf nginx-runtime.conf studentleague.dump; do
  if [[ ! -s "${DEST}/${required}" ]]; then
    echo "missing or empty backup file: ${required}"
    exit 1
  fi
done

{
  meta_line "${UPLOAD_SRC}" uploads
  meta_line /media media
} > "${DEST}/storage-stat-after.txt"
if ! cmp -s "${DEST}/storage-stat-before.txt" "${DEST}/storage-stat-after.txt"; then
  echo "uploads or /media metadata changed during backup"
  diff -u "${DEST}/storage-stat-before.txt" "${DEST}/storage-stat-after.txt" || true
  exit 1
fi
echo "media_and_uploads_unchanged=yes"

(
  cd "${DEST}"
  sha256sum -- * > SHA256SUMS
)
find "${DEST}" -maxdepth 1 -type f -printf '%s\t%f\n' | sort > "${DEST}/MANIFEST.txt"

echo "---- manifest ----"
cat "${DEST}/MANIFEST.txt"
echo "---- version ----"
if [[ -f "${DEST}/VERSION" ]]; then
  cat "${DEST}/VERSION"
fi
echo "---- flyway ----"
cat "${DEST}/flyway-schema-history.txt"
echo "BACKUP_OK ${DEST}"
