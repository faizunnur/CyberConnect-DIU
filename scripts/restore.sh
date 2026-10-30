#!/usr/bin/env sh
# Restore a backup produced by scripts/backup.sh. Stops the application containers, restores PostgreSQL and the
# storage volume, then starts everything again. Usage: scripts/restore.sh <postgres-*.dump> <storage-*.tar.gz>
set -eu
DUMP="${1:?postgres dump path required}"
STORAGE_ARCHIVE="${2:?storage archive path required}"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full --profile scanning"
PROJECT="cyberconnect-diu"

echo "[restore] Stopping application containers (database and storage stay up)"
$COMPOSE stop nginx frontend backend

echo "[restore] Restoring PostgreSQL from $DUMP (drops and recreates objects owned by the migrator)"
$COMPOSE exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$DUMP"

echo "[restore] Restoring object storage volume from $STORAGE_ARCHIVE"
$COMPOSE stop storage
docker run --rm -v "${PROJECT}_storage-data:/data" -v "$(cd "$(dirname "$STORAGE_ARCHIVE")" && pwd):/backup:ro" alpine:3.21 \
  sh -c "rm -rf /data/* && tar xzf /backup/$(basename "$STORAGE_ARCHIVE") -C /data"

echo "[restore] Starting the stack (Flyway validates the schema on start)"
$COMPOSE up -d
$COMPOSE ps
echo "[restore] Done. Sessions were not restored: members sign in again."
