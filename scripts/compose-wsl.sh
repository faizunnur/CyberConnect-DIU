#!/usr/bin/env sh
# Runs Docker Compose for this repository inside the WSL distro (used by scripts/share-dev.ps1).
# Usage: compose-wsl.sh [VAR=value ...] -- <compose arguments>
# The variables are exported for this Compose call only (e.g. APP_BASE_URL for a shared session).
#
# Docker Desktop's WSL integration can drop a broken Compose plugin into /usr/local/lib/docker/cli-plugins that shadows
# the distro's own (docker: "unknown flag: --profile"). Pick the first plugin binary that actually runs and call it
# directly; it behaves exactly like `docker compose`.
set -eu
cd "$(dirname "$0")/.."
# The standalone plugin binary panics in Compose's "bake" build path; the classic builder works.
export COMPOSE_BAKE="${COMPOSE_BAKE:-false}"
while [ $# -gt 0 ] && [ "$1" != "--" ]; do
  export "$1"
  shift
done
[ "${1:-}" = "--" ] && shift
for c in /usr/libexec/docker/cli-plugins/docker-compose /usr/lib/docker/cli-plugins/docker-compose \
         /usr/local/lib/docker/cli-plugins/docker-compose; do
  if [ -x "$c" ] && "$c" version >/dev/null 2>&1; then
    exec "$c" "$@"
  fi
done
exec docker compose "$@"
