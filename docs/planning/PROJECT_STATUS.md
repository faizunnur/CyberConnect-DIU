# Project status

Updated: 30 Oct 2026 (session 1). Read `CLAUDE.md` first.

## Current milestone: M1 — Foundation (implementation complete, Docker verification pending)

### Completed
- Repository initialised (`main`), `.gitignore`, `.editorconfig`, `.env.example`, secret generators (`scripts/`).
- Backend skeleton on Spring Boot 4.1.2 / Java 21: modules scaffolded (identity, audit, admin + common ports),
  security baseline (Redis sessions, SPA CSRF, deny-by-default, problem+json 401/403), Argon2id encoder,
  `ObjectStorage` S3 port + RustFS-compatible client, `AesGcmEncryptor`, mail port, malware-scanner port with
  production guard, health + CSRF endpoints, Flyway `V001__baseline.sql` (users, roles, user_roles,
  system_settings, audit_logs; least-privilege grants, append-only audit), profiles local/docker/prod/test.
- Backend tests: `AesGcmEncryptorTest`, `ModuleBoundariesTest` (ArchUnit), `FoundationIT`, `S3ObjectStorageIT`
  (Testcontainers: postgres 17.11, redis 7.4.12, rustfs 1.0.4). `mvn test-compile` passes.
- Frontend on Next.js 16.4.3: tokens (`globals.css`), providers (theme + TanStack Query), API client, header/footer,
  landing page, `/healthz`, Playwright config + landing spec. `pnpm lint`, `pnpm typecheck`, `pnpm build` pass.
- Docker: backend/frontend Dockerfiles, `docker-compose.yml` (profiles `full`, `scanning`),
  `docker-compose.override.yml` (dev ports + Mailpit), nginx config, PostgreSQL role init script.
- CI workflows (`ci.yml`, `security.yml`) written as `workflow_dispatch`-only until 1 Nov 2026.
- Docs: README, CLAUDE.md, EXECUTION_PLAN, product requirements, architecture overview, ADR-0001..0008,
  database schema, API conventions.

### In progress / blocked
- Docker Desktop engine on the laptop is stuck in "starting" (VM init never logs a boot; host has ~1.4 GB free RAM
  with a 1.7 GB Java language-server process). Until it starts: `mvn verify` integration tests, compose
  verification and Playwright-against-nginx cannot run.

### Next exact task
1. Get the Docker engine running; run `cd backend && mvn -B verify`; fix anything the ITs reveal.
2. `docker compose up -d` then `docker compose --profile full up -d --build`; verify http://localhost:8088 serves the
   landing page, `/api/v1/health` responds through nginx, Flyway applied, app role restrictions hold.
3. `E2E_BASE_URL=http://localhost:8088 pnpm e2e`.
4. Commit `chore: initialize project foundation`, add remote `origin`, verify remote is still empty, push `main`.
5. Start M2 (identity & verification): migrations V002+ (tokens, MFA, affiliations, submissions, documents,
   reviews, reason codes, appeals, document access log), services, controllers, authorization filter, rate limiting,
   ClamAV adapter, moderator UI, onboarding UI, authorization matrix tests.

### Known issues
- None in code. `cn` and `shadcn` are runtime dependencies by design of shadcn 4 (base-nova style).

### Decisions made this session
- Spring Boot 4.1.2 (3.x EOL), RustFS replaces MinIO, Redis sessions, nginx same-origin routing, dev-only Next
  rewrite via `API_PROXY_TARGET`, compose profiles instead of separate files for full stack and scanning,
  app-side bucket creation outside production, `data` network hardened (`internal: true`) only in the prod override,
  nginx published on 8088 locally to avoid port-80 conflicts, CI manual-trigger until 1 Nov 2026.
