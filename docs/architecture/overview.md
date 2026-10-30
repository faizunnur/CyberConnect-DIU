# Architecture overview

## Request flow
```
Browser ──HTTP──▶ nginx (:80/:443, only published port)
                   ├── /api/*  ──▶ Spring Boot backend (:8080)  ──▶ PostgreSQL, Redis, S3 storage, ClamAV, SMTP
                   ├── /ws     ──▶ Spring Boot STOMP WebSocket
                   └── /*      ──▶ Next.js (:3000)
```
Single origin, so the session cookie and CSRF cookie are first-party. In native development `next dev` rewrites
`/api` to `http://localhost:8080` (dev-only); production never does.

## Backend (modular monolith)
`edu.diu.cyberconnect.<module>.{api,application,domain,infrastructure}`. Modules: identity, affiliation, verification,
profile, networking, community, opportunity, research, messaging, notification, impact, moderation, audit, admin.
`common` holds cross-cutting ports and config: security (sessions, CSRF, problem+json handlers), storage (S3 port),
crypto (AES-GCM), mail (SMTP port), scanning (malware scanner port), exception handling, OpenAPI.

Cross-module rules (ArchUnit `ModuleBoundariesTest`): only `application` is shared; `domain`/`infrastructure` are
module-private; `api` is never imported; controllers never touch entities or repositories.

Inverse dependencies use ports: `profile.application.RelationshipLookup` is implemented by the networking module so
profile visibility can honour connections and blocks without profile depending on networking (injected `@Lazy`).
The member directory (`profile.infrastructure.DirectorySearchRepository`) is a read-only native query that joins
`users`, `user_affiliations`, `connections` and `blocks` for filtering; it never writes another module's tables.
Shared value enums (`AccountStatus`, `AffiliationType`, `ProfileVisibility`, `FieldVisibility`, `MessagingPolicy`,
`ConnectionStatus`, …) live in `common.model`.

## Profiles
| Profile | Use | Cookie Secure | Buckets auto-created | API docs |
|---|---|---|---|---|
| `local` (default) | native dev, infra in Docker | no | yes | yes |
| `docker` | full stack via nginx on http://localhost | no | yes | yes |
| `test` | Testcontainers | no | yes | no |
| `prod` | VPS | yes | no | no |

Configuration comes from environment variables with defaults in `application.yml`; native runs import the repository
`.env` through `spring.config.import`.

## Data
PostgreSQL 17 with two roles. Flyway runs as `cyberconnect_migrator`; the application connects as `cyberconnect_app`,
which receives DML on all tables via default privileges but has UPDATE/DELETE revoked on `audit_logs` and all
rights except SELECT revoked on `flyway_schema_history`. Sessions live in Redis (`cyberconnect:session:*`).

## Frontend
Next.js App Router with route groups `(public)`, `(onboarding)`, `(community)`, `admin`. Design tokens in
`globals.css`; shadcn/ui components; TanStack Query for server state; one API client (`lib/api/client.ts`).
