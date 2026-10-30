#!/usr/bin/env sh
# Build and roll out a tagged version on the VPS. Usage: scripts/deploy.sh <APP_VERSION>  (e.g. a git tag or short SHA)
# Expects .env with production values (see docs/operations/deployment.md) and TLS certificates in
# infrastructure/nginx/certs. Migrations run automatically on backend start (Flyway, migrator role).
set -eu
VERSION="${1:?APP_VERSION required}"
export APP_VERSION="$VERSION"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full --profile scanning"

echo "[deploy] Building images cyberconnect/backend:$VERSION and cyberconnect/frontend:$VERSION"
docker build -t "cyberconnect/backend:$VERSION" ./backend
docker build -t "cyberconnect/frontend:$VERSION" ./frontend

echo "[deploy] Backup before rollout"
sh scripts/backup.sh ./backups

echo "[deploy] Rolling out"
$COMPOSE up -d --remove-orphans

echo "[deploy] Waiting for health"
for i in $(seq 1 60); do
  if $COMPOSE ps --format '{{.Service}} {{.Health}}' | grep -q 'backend healthy' \
     && $COMPOSE ps --format '{{.Service}} {{.Health}}' | grep -q 'nginx healthy'; then
    echo "[deploy] Healthy after ${i}x5s"
    $COMPOSE ps
    exit 0
  fi
  sleep 5
done
echo "[deploy] Backend or nginx did not become healthy; inspect: $COMPOSE logs backend nginx" >&2
exit 1
