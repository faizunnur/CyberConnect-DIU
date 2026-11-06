# ADR-0009: Single-VPS production topology

Status: Accepted (30 Oct 2026)

## Context
The platform serves one university community. Operating budget and staff are small; the proposal explicitly rules
out Kubernetes. The stack must still be reproducible, backed up and observable.

## Decision
One Linux VPS runs Docker Compose with the base file plus `docker-compose.prod.yml`:
- nginx is the only published service (80/443, TLS, HSTS, rate limits, CSP); PostgreSQL, Redis, RustFS, ClamAV,
  backend and frontend stay on internal networks.
- Images are built on the host from a git tag (`scripts/deploy.sh <version>`), never pulled as `latest`.
- Flyway migrates on backend start with the migrator role; the application uses the least-privilege role.
- Three start-up guards refuse development settings in production (scanner, fixtures, URLs/cookies/docs/mail).
- Management endpoints (health, Prometheus) listen on an internal port (8081) and are never proxied.
- Backups are logical PostgreSQL dumps plus volume archives (`scripts/backup.sh`), copied off-host; the document
  encryption key lives only in `.env` and the institution's secret manager.
- Logs are ECS JSON on stdout with request ids; the audit table is the source of truth for privileged actions.

## Addendum (7 Nov 2026): Railway
The same topology can run on Railway with one service per container (`docs/operations/railway.md`): managed
PostgreSQL/Redis, RustFS and ClamAV as image services with volumes, backend and frontend built from the repository,
and a dedicated nginx image (`infrastructure/railway/edge/`) as the only public service. TLS terminates at Railway's
edge; nginx resolves the private hostnames per request because their addresses change on every redeploy. The
single-origin contract, the guards and the private management port are unchanged.

## Consequences
Vertical scaling only (a 4 GB VPS with ClamAV, 8 GB comfortable). No zero-downtime deploys: a rollout restarts the
backend for a few seconds, which the frontend tolerates. Moving to multiple hosts later would mean an external
PostgreSQL/Redis and a shared S3 bucket; the application already keeps all state outside the containers.
