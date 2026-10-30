#!/usr/bin/env sh
# Nightly backup for the production stack: PostgreSQL dump + object storage volume snapshot + Redis AOF.
# Usage: scripts/backup.sh [backup-dir]   (default ./backups). Keeps the last 14 days. Run from the repo root.
set -eu
BACKUP_DIR="${1:-./backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
PROJECT="cyberconnect-diu"
mkdir -p "$BACKUP_DIR"

echo "[backup] PostgreSQL logical dump"
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner' \
  > "$BACKUP_DIR/postgres-$STAMP.dump"

echo "[backup] Object storage volume (encrypted identity documents, member media)"
docker run --rm -v "${PROJECT}_storage-data:/data:ro" -v "$(pwd)/$BACKUP_DIR:/backup" alpine:3.21 \
  tar czf "/backup/storage-$STAMP.tar.gz" -C /data .

echo "[backup] Redis append-only file (sessions and rate limits; optional)"
docker run --rm -v "${PROJECT}_redis-data:/data:ro" -v "$(pwd)/$BACKUP_DIR:/backup" alpine:3.21 \
  tar czf "/backup/redis-$STAMP.tar.gz" -C /data .

echo "[backup] Pruning backups older than 14 days"
find "$BACKUP_DIR" -type f -mtime +14 -name '*.dump' -delete
find "$BACKUP_DIR" -type f -mtime +14 -name '*.tar.gz' -delete

echo "[backup] Done: $BACKUP_DIR (copy off-host; the dump contains personal data and the storage archive"
echo "         contains AES-256-GCM encrypted documents whose key lives only in the .env of the host)."
