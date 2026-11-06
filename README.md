# CyberConnect DIU

A private, verified professional network for Daffodil International University's cybersecurity students, alumni and
Cyber Security Center (CSC) members: opportunities, research collaboration, mentorship, discussion, messaging and
confirmed impact tracking. Registration is public; membership requires moderator verification of an identity document.

Stack: Next.js 16 · Spring Boot 4.1 (Java 21) · PostgreSQL 17 · Redis 7.4 · RustFS (S3 API) · nginx · Docker Compose.

## Prerequisites (Windows laptop)
- Docker Desktop (WSL 2 backend), Java 21, Maven 3.9, Node 24, pnpm 9 (`corepack enable` or `npm i -g pnpm@9.15.4`).
- Recommended `%USERPROFILE%\.wslconfig` on a 16 GB laptop so Docker does not starve the host:
  ```
  [wsl2]
  memory=6GB
  processors=4
  ```

## Quick start
```powershell
scripts\gen-secrets.ps1              # creates .env with random secrets (never commit it)
docker compose up -d                 # postgres, redis, storage (RustFS), mailpit on 127.0.0.1
cd backend;  mvn spring-boot:run     # http://localhost:8080  (profile "local", reads ..\.env)
cd frontend; pnpm install; pnpm dev  # http://localhost:3000  (/api proxied to :8080 in dev only)
```
Mailpit inbox: http://localhost:8025 · RustFS console: http://localhost:9001 · API docs (local): http://localhost:8080/api/v1/docs

The dev Compose override publishes PostgreSQL on **127.0.0.1:5433** (not 5432) so it never collides with a locally installed PostgreSQL service. First sign-in bootstrap: set `BOOTSTRAP_SUPER_ADMIN_EMAIL=you@example.org` (env `app.bootstrap.super-admin-email`) and register with that address; the account becomes SUPER_ADMIN+ADMIN+MODERATOR and must enrol MFA before using admin tools.

## Workflows
| Goal | Command |
|---|---|
| Infrastructure only (daily development) | `docker compose up -d` |
| Full stack through nginx | `docker compose --profile full up -d --build` → http://localhost:8088 |
| Full stack + ClamAV scanning | `docker compose --profile full --profile scanning up -d` and `SCANNER_TYPE=clamav` in `.env` |
| Production (TLS, hardened, ClamAV mandatory) | `sh scripts/deploy.sh <version>` → see `docs/operations/deployment.md` |
| Stop | `docker compose --profile full down` (keeps volumes; `-v` destroys data, ask first) |

## Tests
```
cd backend  && mvn -B verify                       # unit + ArchUnit + Testcontainers (needs Docker)
cd backend  && mvn -B test -DexcludedGroups=integration
cd frontend && pnpm lint && pnpm typecheck && pnpm build
cd frontend && pnpm e2e:install && pnpm e2e        # Playwright; E2E_BASE_URL=http://localhost:8088 to target nginx
cd frontend && E2E_BASE_URL=http://localhost:8088 E2E_MAILPIT_URL=http://localhost:8025 pnpm e2e   # full-stack flows incl. onboarding
cd backend  && CLAMAV_IT=true mvn -B verify           # adds the real ClamAV + EICAR check (~2 GB RAM, slow first pull)
```
Full-stack Playwright flows (onboarding, opportunities, messaging) need `E2E_FIXTURES_ENABLED=true` on the backend
(dev only; refused in production). The accessibility spec (axe-core, WCAG 2.2 AA) runs against the public pages.

## Operations
`docs/operations/` holds the VPS deployment guide, the Railway deployment guide (`railway.md`), runbook,
backup/restore procedure and monitoring notes;
`docs/security/checklist.md` tracks the security controls and the go-live prerequisites.

## Resource requirements (measured on an i5-12450HX, 16 GB)
| Mode | RAM (approx.) | Notes |
|---|---|---|
| Infrastructure only | ~0.6 GB containers + WSL overhead | postgres ~100 MB, redis ~20 MB, rustfs ~100 MB, mailpit ~30 MB |
| + native backend | +0.7–1.0 GB | JVM; `-Xmx` not needed locally |
| + native frontend (`pnpm dev`) | +0.5–1.0 GB | Turbopack |
| Full stack in Docker | ~2.0 GB containers | backend limited to 1 GB, frontend 512 MB |
| ClamAV profile | +1.5–2 GB | signature database ~300 MB on disk, first start 3–5 min |
Disk: images ≈ 2.5 GB, Maven repo ≈ 1 GB, `node_modules` ≈ 0.6 GB.

## Repository layout
```
backend/        Spring Boot modular monolith (package-by-feature, see CLAUDE.md §5)
frontend/       Next.js App Router application
infrastructure/ nginx configs (dev, VPS prod), PostgreSQL role init, Railway service images (railway/)
docs/           requirements, planning status, architecture + ADRs, database, API
scripts/        secret generation helpers
docker-compose.yml / docker-compose.override.yml (dev) / docker-compose.prod.yml (M8)
```
See `CLAUDE.md` for engineering rules and `docs/planning/PROJECT_STATUS.md` for progress.
