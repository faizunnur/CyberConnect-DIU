# CLAUDE.md — CyberConnect DIU

Read this file first in every session. Then read `docs/planning/PROJECT_STATUS.md` for where work stands.
Do not ask the user to restate requirements; they live in `docs/requirements/product-requirements.md`.

## 1. Project overview
CyberConnect DIU is a private, verified professional network for Daffodil International University's cybersecurity
students, alumni and Cyber Security Center (CSC) members. Registration is public; membership is granted only after a
moderator verifies an identity document. It is not a generic social network.

## 2. Product goals
Verified membership, professional profiles and directory, community feed, jobs/internships/scholarships/research
assistantships, research collaboration, mentorship, direct messaging, notifications, confirmed impact tracking,
moderation, administration and audit. Security and privacy outrank everything else.

## 3. Technology stack (pinned; see docs/planning/EXECUTION_PLAN.md for versions)
- Backend: Java 21, Spring Boot 4.1.x, Spring Security 7, Spring Data JPA (Hibernate 7), Flyway, Spring Session Data
  Redis, Spring WebSocket/STOMP, Actuator, springdoc-openapi 3, AWS SDK v2 (S3), Bouncy Castle (Argon2id), Maven.
- Frontend: Next.js 16 App Router, TypeScript strict, Tailwind CSS v4, shadcn/ui (base-nova, `@base-ui/react`),
  Lucide, TanStack Query, React Hook Form, Zod, next-themes, pnpm.
- Infrastructure: Docker Compose, nginx, PostgreSQL 17, Redis 7.4, RustFS (S3 API), ClamAV, Mailpit, GitHub Actions.
- Tests: JUnit 5 (Jupiter 6), Mockito, Testcontainers 2, ArchUnit, Playwright, Trivy, Semgrep, OWASP Dependency-Check.

## 4. Architecture rules
- Modular monolith. No microservices, Kubernetes, Kafka or Elasticsearch.
- nginx is the single origin: `/api/*` and `/ws` → backend, everything else → Next.js. Next.js is not a BFF.
  (Native dev uses a dev-only Next rewrite controlled by `API_PROXY_TARGET`; production never sets it.)
- Redis-backed server-side sessions (cookie `CCSESSION`, HttpOnly, SameSite=Lax, Secure in prod). No JWT.
- CSRF: cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` on every non-GET. Never disable CSRF.
- Object storage only through `common.storage.ObjectStorage` (S3 API). Never expose storage URLs or presigned URLs.
- Identity documents: validated by magic bytes → re-encoded/EXIF-stripped → scanned → AES-256-GCM encrypted →
  private bucket → streamed through the backend to authorised reviewers with step-up MFA and per-view audit.
- PostgreSQL full-text search + pg_trgm. Flyway owns the schema (`ddl-auto=validate` only).
- Two DB roles: `cyberconnect_migrator` (Flyway, owner) and `cyberconnect_app` (runtime, no UPDATE/DELETE on
  append-only audit tables).

## 5. Backend module boundaries (ArchUnit-enforced, `ModuleBoundariesTest`)
Modules under `edu.diu.cyberconnect`: identity, affiliation, verification, profile, networking, community,
opportunity, research, messaging, notification, impact, moderation, audit, admin, plus `common`.
Each module has `api` (controllers + request/response DTOs), `application` (service interfaces, DTOs, events),
`domain` (entities, enums, invariants), `infrastructure` (JPA repositories, adapters).
- Other modules may use only a module's `application` package. `domain` and `infrastructure` are module-private.
- Cross-module references are UUIDs, never JPA associations to another module's entity.
- `common` never depends on feature modules. Controllers never expose entities.

## 6. Frontend conventions
- Route groups: `(public)`, `(onboarding)`, `(community)`, `admin`. Feature code in `src/features/<feature>`.
- Shared UI in `src/components/ui` (shadcn), layout/navigation in `src/components/layout`.
- All API calls go through `src/lib/api/client.ts` (same-origin, CSRF header, `ApiError` from problem+json).
- Forms: React Hook Form + Zod. Server state: TanStack Query. Never store tokens in localStorage.
- Design tokens live in `src/app/globals.css`; components use semantic classes (`bg-primary`, `text-muted-foreground`,
  `bg-navy`, `text-success`, `text-warning`), never hex values. Light default, dark via `.dark` class.
- Every screen needs loading, empty, error and success states, and must work at mobile, tablet and desktop widths.
- Never define routes under `/api`; that path belongs to the backend. Frontend health is `/healthz`.

## 7. Database conventions
UUID primary keys (`UUID.randomUUID()` app-side), `timestamptz` UTC, `text` + CHECK for enumerations, foreign keys
everywhere, partial unique indexes for "one active" rules, `search_vector` generated columns for FTS. One Flyway
migration per change (`V00N__description.sql`); migrations use the `${app_role}` placeholder for grants and must
REVOKE UPDATE/DELETE on new append-only tables.

## 8. Authentication and authorization rules
- Affiliation (STUDENT, ALUMNI, CSC_MEMBER), role (MEMBER, MODERATOR, ADMIN, SUPER_ADMIN) and account status
  (PENDING, VERIFIED, REJECTED, SUSPENDED, DEACTIVATED) are separate concepts.
- Community access requires status VERIFIED and a verified email. Pending/rejected/suspended/deactivated accounts reach
  only their own onboarding, status and appeal endpoints.
- Every protected endpoint checks principal + account status + ownership/membership on the server. UUIDs are not
  authorization. Clients never supply roles, statuses, counters, reviewer ids or ownership.
- Fine-grained authorities (VERIFICATION_REVIEW, DOCUMENT_VIEW, APPEAL_DECIDE, ROLE_ASSIGN, MODERATION_ACTION) map
  to roles; ADMIN does not implicitly get DOCUMENT_VIEW. The last SUPER_ADMIN cannot be removed.
- MFA (TOTP + hashed single-use recovery codes) is mandatory for privileged roles; step-up required for document
  viewing, role assignment, suspension, appeal decisions.
- Sessions are destroyed on suspension, rejection, deactivation, password change and privilege changes.

## 9. Security requirements
Argon2id (19 MiB, t=2, p=1), rate limiting in Redis, uniform responses for enumeration-prone endpoints, magic-byte
upload validation, ClamAV in production (NoOp scanner refuses to start in prod), problem+json errors with stable
`code`, no secrets/PII/document bytes in logs, Swagger and Actuator (except health) off in prod.

## 10. Document handling requirements
Real identity-document collection stays disabled (`verification.real_documents_enabled=false`) until institutional
approval and retention policy are recorded. Development uses fictional documents only. Retention default 30 days after
the final decision, then purge objects and keep metadata/audit.

## 11. Docker development commands
```
scripts/gen-secrets.ps1 (or .sh)                 # creates .env once
docker compose up -d                             # infrastructure only: postgres, redis, storage, mailpit
docker compose --profile full up -d --build      # full stack via nginx on http://localhost:8088
docker compose --profile full --profile scanning up -d   # + ClamAV (2 GB RAM)
docker compose ps / logs -f backend / down       # never `down -v` without explicit user confirmation
cd backend && mvn spring-boot:run                # native backend (profile local, reads ../.env)
cd frontend && pnpm dev                          # native frontend on :3000 with /api proxied to :8080
```

## 12. Testing commands
```
cd backend && mvn -B verify                      # unit + ArchUnit + Testcontainers ITs (Docker required)
cd backend && mvn -B test -DexcludedGroups=integration   # fast unit/architecture tests only
cd frontend && pnpm lint && pnpm typecheck && pnpm build
cd frontend && pnpm e2e                          # Playwright (starts `pnpm dev`), or E2E_BASE_URL=http://localhost:8088 pnpm e2e
```

## 13. Git conventions
Branch `main`, remote `origin` = https://github.com/faizunnur/CyberConnect-DIU.git. Conventional commits
(`feat(scope):`, `fix(scope):`, `test(scope):`, `chore:`, `docs:`). Commit at milestone boundaries and after
meaningful units. Never force-push. Never commit `.env`, secrets or real documents. GitHub Actions workflows are
`workflow_dispatch`-only until 1 Nov 2026 (Actions minutes exhausted); re-enable push/PR triggers afterwards.

## 14. Current milestone
See `docs/planning/PROJECT_STATUS.md` (kept current at the end of every working session).

Implementation notes that are easy to get wrong:
- Session revocation needs the indexed Redis repository: `spring.session.data.redis.repository-type=indexed` (Boot 4 key).
- Session cookie attributes come from `SessionCookieConfig`, not `server.servlet.session.cookie.*` (Boot 4 ignores those for Spring Session).
- Surefire includes `*IT.java`; its argLine keeps JaCoCo via `@{argLine}` and enables Mockito agent attach on JDK 21.
- Pages serialise as `{content, page:{…}}` (`spring.data.web.pageable.serialization-mode=via_dto`).
- nginx: a location with any `proxy_set_header` must repeat `Host` etc.; otherwise Tomcat sees an invalid upstream host.
- `BOOTSTRAP_SUPER_ADMIN_EMAIL` promotes the first matching account to SUPER_ADMIN/ADMIN/MODERATOR (audited).
- Spring Data derived queries do not support `NullsLast`; use `@Query` with `order by … nulls last`.
- Record components named like a static factory (`self()`) clash; name factories `ofSelf()` etc.
- A bulk `@Modifying(clearAutomatically = true)` query detaches entities loaded before it: do entity mutations and
  `saveAndFlush` first, run the bulk statement last.
- The Ubuntu WSL distro (fallback Docker engine) stops seconds after the last `wsl` session ends; keep a hidden
  `wsl -d Ubuntu -u root -- sleep 86400` process running while using the Compose stack.

## 15. Approved architectural decisions
ADR-0001 modular monolith (ports for inverse module dependencies, see ADR-0008 addendum) · ADR-0002 Redis sessions · ADR-0003 nginx same-origin routing · ADR-0004 S3 storage
abstraction (RustFS locally; MinIO is discontinued) · ADR-0005 PostgreSQL · ADR-0006 verification/affiliation state
machine · ADR-0007 document encryption and private access · ADR-0008 ArchUnit module boundaries.
Business-rule decisions D-01..D-39 are recorded in `docs/requirements/product-requirements.md`.

## 16. Prohibited shortcuts
No plaintext passwords; no tokens in localStorage; no frontend-only authorization; no public/presigned document URLs;
no entity exposure; no self-assigned roles or statuses; no trusting UUIDs; no secrets in git; no CSRF disabling;
no `ddl-auto=update`; no `latest` image tags in committed config; no real identity documents in development; no
skipped tests; no claiming untested features work; no `docker compose down -v` without confirmation.
