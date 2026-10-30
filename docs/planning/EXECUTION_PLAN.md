# Execution plan

Autonomous build, milestones M1→M8, commit after each. Source of requirements: `docs/requirements/product-requirements.md`.

## Pinned versions (verified 30 Oct 2026 against Maven Central, npm and Docker Hub)
| Component | Version | Compatibility note |
|---|---|---|
| Spring Boot | 4.1.2 | Current OSS line; 3.5 OSS support ended 30 Jun 2026 |
| Spring Framework / Security / Session | 7.0.10 / 7.1.2 / 4.1.1 | managed by Boot BOM |
| Hibernate ORM / Flyway / PostgreSQL driver | 7.4.13 / 12.4.0 / 42.7.14 | managed by Boot BOM |
| Jackson | 3.1.7 (`tools.jackson`) | Boot 4 default; no Jackson 2 deps added |
| Testcontainers | 2.0.5 (`testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer`) | managed by Boot BOM |
| JUnit Jupiter / Mockito | 6.0.3 / 5.23.0 | managed by Boot BOM |
| springdoc-openapi | 3.2.0 | Boot 4 line |
| ArchUnit | 1.5.1 | core API in plain JUnit tests |
| AWS SDK v2 (S3) | 2.57.7 | checksum calculation set to WHEN_REQUIRED for third-party stores |
| Bouncy Castle | 1.86 | Argon2id provider |
| Next.js / React | 16.4.3 / 19.3.1 | Turbopack default |
| TypeScript | 5.9.3 | TS 7 is outside typescript-eslint's supported range |
| Tailwind CSS | 4.3.3 (`@tailwindcss/turbopack`) | |
| shadcn CLI | 4.23.0 (style base-nova, `@base-ui/react`) | runtime deps `shadcn`, `cn` |
| TanStack Query / RHF / resolvers / Zod | 5.105.0 / 7.91.0 / 6.0.2 / 4.6.7 | |
| lucide-react / next-themes | 1.65.0 / 0.4.6 | |
| Playwright | 1.64.1 | |
| postgres / redis / nginx | 17.11-alpine / 7.4.12-alpine / 1.28.3-alpine | |
| rustfs/rustfs | 1.0.4 | MinIO images are no longer published (repo archived Apr 2026) |
| clamav/clamav / axllent/mailpit | 1.4.6 (LTS) / v1.32.0 | |
| eclipse-temurin / node / maven images | 21.0.13_9-jre-alpine / 24.22.2-alpine / 3.9.16-eclipse-temurin-21-alpine | |

## Milestones
| # | Scope | Acceptance |
|---|---|---|
| M1 Foundation | repo, backend + frontend skeletons, compose, Flyway baseline, role split, sessions, health, ArchUnit, ITs, CI, landing, tokens | stack starts; nginx serves frontend; health OK; Flyway applied; app role cannot alter audit tables; RustFS tests pass; lint/typecheck/build green |
| M2 Identity & verification | registration, login/logout, email verification, password reset, Argon2id, MFA, affiliations, submissions, upload pipeline, scanning, moderator dashboard, approve/reject, resubmission, appeals, audit, revocation, role management | authorization matrix passes for all five statuses; decisions audited; unauthorised reviewers blocked; MFA step-up enforced |
| M3 Profiles & networking | profiles, skills, experience, education, directory, search/filters, privacy, connections, blocking | privacy matrix tests pass |
| M4 Community | posts, comments, replies, likes, reposts, bookmarks, hashtags, feed, reporting, moderation, responsive shell | sanitisation + moderation tests pass |
| M5 Opportunities, research, mentorship | opportunity lifecycle + applications, research projects/members/applications, mentor availability + requests | publish→apply→close E2E |
| M6 Communication | conversations, message requests, blocks, STOMP WebSocket, notifications + preferences, message reporting | membership/IDOR + WebSocket auth tests |
| M7 Impact | proposals, dual confirmation, disputes, moderator resolution, statistics | confirmed-only statistics test |
| M8 Production readiness | security/perf/a11y testing, monitoring, logging, backup/restore, retention purge, prod compose, VPS guide, runbook | checks completed and documented |
