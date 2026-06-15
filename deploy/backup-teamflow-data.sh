#!/usr/bin/env bash
set -euo pipefail

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

PROJECT_DIR="${PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
BACKUP_ROOT="${BACKUP_ROOT:-$PROJECT_DIR/backups/data}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-teamflow-ai}"

timestamp="$(date +%Y%m%d-%H%M%S)"
target_dir="$BACKUP_ROOT/$timestamp"
tmp_dir="$target_dir.tmp"

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

require_container() {
  docker inspect "$1" >/dev/null
}

backup_mysql() {
  log "Backing up MySQL databases"
  docker exec teamflow-mysql sh -lc \
    'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --databases teamflow_ai umami' \
    | gzip -9 > "$tmp_dir/mysql.sql.gz"
}

backup_volume() {
  local volume_name="$1"
  local archive_name="$2"
  local owner_uid
  local owner_gid
  owner_uid="$(id -u)"
  owner_gid="$(id -g)"
  log "Backing up Docker volume $volume_name"
  docker volume inspect "$volume_name" >/dev/null
  docker run --rm \
    -v "$volume_name:/source:ro" \
    -v "$tmp_dir:/backup" \
    nginx:alpine \
    sh -lc "cd /source && tar -czf /backup/$archive_name . && chown $owner_uid:$owner_gid /backup/$archive_name"
}

main() {
  cd "$PROJECT_DIR"
  mkdir -p "$tmp_dir"
  chmod 700 "$tmp_dir"

  require_container teamflow-mysql
  require_container teamflow-minio
  require_container teamflow-qdrant
  require_container teamflow-redis

  backup_mysql
  backup_volume "${COMPOSE_PROJECT_NAME}_teamflow_minio" "minio-data.tar.gz"
  backup_volume "${COMPOSE_PROJECT_NAME}_teamflow_qdrant" "qdrant-data.tar.gz"
  backup_volume "${COMPOSE_PROJECT_NAME}_teamflow_redis" "redis-data.tar.gz"

  (cd "$tmp_dir" && sha256sum *.gz > SHA256SUMS)
  mv "$tmp_dir" "$target_dir"
  chmod -R go-rwx "$target_dir"

  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$RETENTION_DAYS" -exec rm -rf {} +
  log "Backup completed: $target_dir"
}

main "$@"
