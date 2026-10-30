# CyberConnect DIU — Initial Technical Assessment and Implementation Plan

| Item | Value |
|---|---|
| Date | 30 October 2026 |
| Status | Awaiting approval. No application code has been written. |
| Inputs | *CyberConnect DIU — Project Proposal & Technical Design* v1.0 (30 Oct 2026); engineering brief; DIU and CSC DIU logos |
| Audience | Project team and supervisor |

This document records the pre-implementation review requested in the engineering brief:
repository inspection, requirement gaps and conflicts, database review, security and
privacy risks, repository structure, Docker architecture, milestone plan and the
decisions that need confirmation before Milestone 1 starts.

---

## 1. Environment and repository state

- `d:\Project\CyberConnect DIU` is empty and is not a git repository. There is nothing to preserve or migrate.
- Local toolchain: Java 21.0.9 (LTS), Maven 3.9.11, Node 24.11.1, pnpm 9.15.4, Docker 29.2.1 with Compose v5.0.2, git 2.52.
- Docker Desktop's daemon was not running during the assessment. Image availability was checked against the registries with `docker manifest inspect`, which does not need the daemon.

Registry verification, 30 October 2026:

| Image | Result |
|---|---|
| `minio/minio:RELEASE.2025-09-07T16-13-09Z` (proposal §15.3) | **not found** |
| `minio/minio:latest`, `quay.io/minio/minio:latest`, `minio/mc:latest`, `minio/minio:RELEASE.2025-04-22T22-12-26Z` | **not found** |
| `postgres:17-alpine` | ok |
| `redis:7.4-alpine` | ok |
| `nginx:1.28-alpine` | ok |
| `clamav/clamav:stable` | ok |
| `axllent/mailpit:latest` | ok |
| `eclipse-temurin:21-jre-alpine` | ok |
| `rustfs/rustfs:latest` | ok |
| `dxflrs/garage:v2.1.0` | ok |
| `chrislusf/seaweedfs:latest` | ok |
| `cgr.dev/chainguard/minio:latest` | ok (third-party rebuild, no further upstream releases) |

---

## 2. Findings: missing, conflicting or technically questionable requirements

Each finding ends with the decision it feeds (D-xx, see §8) or "default" where I will apply the stated choice unless told otherwise.

### Stack and infrastructure

**F-01 — "Spring Boot 3.x" is now an unsupported line.** Spring Boot 3.5 was the last 3.x minor; its open-source support (and that of Spring Framework 6.2 and Spring Security 6.5) ended on 30 June 2026, with 3.5.16 as the final free release. Only Broadcom commercial or third-party paid support continues. Building a security-sensitive platform on a line that no longer receives free CVE fixes contradicts the brief's own security requirements. Spring Boot 4.x (Spring Framework 7, Spring Security 7, Hibernate ORM 7, Jakarta EE 11, Jackson 3) keeps the same programming model and supports Java 21. Current OSS-supported line at the time of writing is 4.1.x. Knock-on effects: use springdoc-openapi 3.x, current Testcontainers, and Jackson 3 package names. → **D-01**

**F-02 — MinIO is no longer obtainable.** MinIO stripped admin features from the community console in May 2025, stopped publishing community binaries and images in October 2025, and archived the GitHub repository on 25 April 2026. All Docker Hub tags now fail (verified above). The proposal's compose file and §10.1 depend on it. The application must depend only on the S3 API (AWS SDK for Java v2) so the backend is swappable. For development and CI the replacement candidates that resolve today are RustFS (Apache-2.0, MinIO-style configuration, web console), Garage (Deuxfleurs, small and stable, needs a layout step on first start) and SeaweedFS (mature, more moving parts). Production can use any S3-compatible service, including institution-hosted RustFS/Garage or a cloud bucket. → **D-02**

**F-03 — Authentication model is left open and the API/DB sections contradict the recommendation.** §11.3 prefers cookie sessions or a BFF, yet §13 lists `POST /auth/refresh` and §12.1 lists `refresh_tokens`. Recommendation: server-side sessions stored in Redis (Spring Session Data Redis), opaque session id in a `Secure; HttpOnly; SameSite=Lax` cookie, Spring Security CSRF token (cookie + header double submit) for all state-changing requests. Benefits: instant revocation on suspension, rejection, role change or password change (a hard requirement for the lifecycle rules), no token-family logic, and the WebSocket handshake reuses the same cookie. `/auth/refresh` and `refresh_tokens` are dropped. → **D-03**

**F-04 — Request routing: proposal's diagram makes Next.js the API gateway.** §10.3 shows nginx → Next.js → Spring Boot. Proxying every API call through Next.js adds latency, a second place to get auth wrong, and complicates WebSockets. Recommendation: nginx path routing on one origin: `/api/*` and `/ws` to the backend, everything else to Next.js. Same origin means no browser CORS for the SPA parts; Next.js server components forward the session cookie when they need user data. Consequence: the frontend must not define routes under `/api` (health goes on `/healthz`). → **D-04**

**F-05 — Malware scanning needs an engine, which is extra infrastructure.** The brief requires "malware scanning integration" but names no scanner. The practical self-hosted option is ClamAV (clamd, INSTREAM over TCP). It costs roughly 1.5–2 GB RAM and a ~300 MB signature download. Recommendation: a `DocumentScanner` port with `ClamAvScanner` and a `NoOpScanner` (dev/CI only, loudly logged); ClamAV runs behind a compose profile so low-RAM dev machines can skip it; production requires it. → **D-15**

**F-06 — Email delivery is unspecified for development.** Email verification and password reset need SMTP from Milestone 2. Recommendation: Mailpit (SMTP sink with web UI) in the dev compose override; production needs a real SMTP account whose owner is undecided. → **D-16**

**F-07 — The compose baseline in §15.3 does not start as written.**
1. nginx is only on `edge`; backend is on `app`/`data`. nginx cannot reach the backend it proxies to.
2. `backend` depends on `redis` with `condition: service_healthy`, but redis has no healthcheck; Compose rejects this.
3. MinIO tag does not exist (F-02).
4. No bucket initialisation, no mail, no scanner, no frontend/backend healthchecks, so `depends_on` conditions cannot be used for them.
Corrected architecture in §6. (default)

**F-08 — MFA mechanism is not specified.** Recommendation: TOTP (RFC 6238) with an authenticator app, encrypted secret at rest, ten hashed single-use recovery codes. Enforcement: any account holding MODERATOR, ADMIN or SUPER_ADMIN must complete enrolment before any privileged endpoint responds; step-up (fresh MFA within 10 minutes) for role changes, document viewing, suspensions and appeal decisions. This belongs in Milestone 2 because the moderator dashboard ships there. No SMS. → **D-17**

**F-09 — Rate limiting implementation.** Redis-backed token bucket executed as an atomic Lua script, keyed by client IP (trusting `X-Forwarded-For` only from nginx) and, where authenticated, by user id. Tight buckets on register, login, verify-email resend, forgot-password, appeal submission and document upload. No extra library. (default)

**F-10 — Real-time scaling.** Spring's STOMP SimpleBroker is single-instance. Acceptable for the modular monolith; if a second backend replica is ever needed, add a Redis pub/sub relay. (default, documented as ADR)

**F-11 — Search engine.** PostgreSQL full-text search (`tsvector` generated columns + GIN) plus `pg_trgm` for fuzzy name/skill matching. No Elasticsearch. Permission filtering happens in the SQL, not after. (default)

**F-12 — Controlled migrations and database roles.** Flyway on backend start-up is fine for dev. For production the compose stack runs a one-shot `migrate` service before the backend. Two PostgreSQL roles from day one: `cyberconnect_migrator` (schema owner, used by Flyway) and `cyberconnect_app` (DML only; **no UPDATE/DELETE on `audit_logs`, `verification_reviews`, `appeal_decisions`**). This makes the audit trail append-only at the database level, not only by convention. (default)

### Verification business rules

**F-13 — Login before email verification is undefined.** FR-02 requires a verified email before the verification submission is finalised, but says nothing about sign-in. Recommendation: sign-in is allowed immediately after registration; the onboarding page shows "verify your email" as step 1; document submission (and the appeal form) stay disabled until `email_verified_at` is set; resend is rate-limited. → **D-05**

**F-14 — Lifecycle gaps.** The brief lists PENDING→VERIFIED, PENDING→REJECTED, REJECTED→appeal→VERIFIED/REJECTED, VERIFIED→SUSPENDED, VERIFIED→DEACTIVATED. Missing: SUSPENDED→VERIFIED (lifting a suspension), REJECTED→PENDING (resubmission with better evidence, which §9.3 mentions as "resubmission"), what happens after an appeal is denied, and who may deactivate. Proposed rules:
- Rejection reason codes carry `allowsResubmission`. For example `DOCUMENT_UNREADABLE`, `DOCUMENT_EXPIRED`, `DETAILS_MISMATCH` allow a new submission; `NOT_ELIGIBLE`, `DUPLICATE_ACCOUNT`, `SUSPECTED_FRAUD` do not (appeal only; the applicant sees a neutral reason text, never the internal code for fraud).
- Limits: at most 3 submissions per affiliation; exactly one appeal per rejected submission.
- Denied appeal: account stays REJECTED and is terminal; only an ADMIN can reopen (audited).
- Suspension: moderator action with reason, optional expiry; lifting is an explicit audited action.
- Deactivation: by the user (soft close, 30-day restore window, then anonymisation under the retention policy) or by an admin (immediate, audited).
→ **D-06, D-07**

**F-15 — Multiple affiliations vs a single `affiliation` field at registration.** §4.1 allows several affiliations; §13.2 registers one. Recommendation: one primary affiliation chosen at registration and verified by the first submission; further affiliations are added later from Settings through an additional submission; each `user_affiliations` row has its own status. → **D-08**

**F-16 — Eligibility evidence rules are undefined** (which fields, which documents, ID format). Recommendation, all configurable in `system_settings` with sensible defaults until the institution supplies the rules:

| Affiliation | Required fields | Accepted documents (any one) |
|---|---|---|
| STUDENT | student id, batch, department | student ID card (front) |
| ALUMNI | student id, graduation year | alumni card, degree certificate, transcript first page |
| CSC_MEMBER | CSC member id | CSC membership card, CSC-issued letter |

Student-id format: configurable regex, permissive default (`^[A-Za-z0-9-]{5,20}$`), because the real DIU format is not stated in the proposal. → **D-09**

**F-17 — Separation of duties.** A moderator must not review their own submission, and an appeal must be decided by a different moderator than the one who rejected. Recommend enforcing both server-side. → **D-19**

**F-18 — Document retention is an institutional decision** (§14.3, §18). Implement a configurable retention window (default 30 days after the final decision, including appeal) with a scheduled purge job that deletes the object, keeps the metadata row with `purged_at`, and writes an audit event. → **D-20**

**F-19 — Accepted document types and sizes are unspecified.** Recommendation: JPEG, PNG, PDF; 8 MB per file; max 3 files per submission; images are re-encoded and EXIF-stripped; PDFs are rejected if they contain JavaScript or embedded files. Images-only is the safer variant if PDF is not needed. → **D-24**

### Product rules

**F-20 — Mentorship is modelled twice.** §5.4 lists "mentorship" as an opportunity type; §5.6 and §12.1 have `mentorship_requests`. Recommendation: remove MENTORSHIP from opportunity types; mentor availability is a profile attribute (`mentor_availability`, topics, capacity); `mentorship_requests` connects a mentee to a mentor. → **D-10**

**F-21 — "Share" is undefined in a private network.** Recommendation: share = repost into your own feed with optional commentary (quote), stored as `repost_of_post_id`; no external sharing. → **D-11**

**F-22 — Post visibility.** Not specified. Default for MVP: every post is visible to all VERIFIED members; a "connections only" option can be added later without schema change (`visibility` column present from the start). (default)

**F-23 — Impact records: initiator, counterpart and source.** Recommendation: either party may propose a record; it references exactly one on-platform source (opportunity, research project or mentorship request) the two parties are both linked to; the counterpart confirms or disputes; a disputed record goes to a moderator; only CONFIRMED records count anywhere; one record per (source, beneficiary, contributor). Off-platform contributors are out of scope. → **D-12**

**F-24 — Privacy settings are not enumerated.** Recommendation (per profile): `profile_visibility` (ALL_MEMBERS | CONNECTIONS_ONLY), `show_email` (default off), `show_organization`, `show_country`, `searchable` (default on), `messaging_policy` (ALL_MEMBERS_VIA_REQUEST | CONNECTIONS_ONLY), `show_mentor_availability`. Student id and identity documents are never shown to other members under any setting. → **D-13**

**F-25 — Moderator access to private messages is not addressed.** Recommendation: moderators never browse conversations. Reporting a message snapshots the reported message (content, sender, timestamp) into the report; nothing else from the conversation is visible to moderators. → **D-14**

### Governance and information needed

**F-26 — Deployment target is unknown** (DIU-hosted server, VPS, or cloud). It determines TLS, object storage, SMTP, backups and cost ownership (§18.1 asks the same). → **D-21**

**F-27 — No git remote or CI host exists yet.** GitHub Actions is assumed by the proposal; an organisation/repository is needed before the CI milestone can be verified end-to-end. → **D-22**

**F-28 — Logo use.** DIU and CSC DIU logos were supplied. §4.1 and §18 say institutional branding needs written permission. I will place them under `frontend/public/brand/` and use them in header/footer/landing, but launch should wait for the permission. The CSC logo's red/charcoal palette stays as-is on the logo only; the UI keeps the navy/blue system. → **D-23**

**F-29 — Minor document issues.** Appendix C "Suggested first pilot" is numbered 10–15 instead of 1–6. `edu.diu.cyberconnect` is kept as the Java package even though DIU's domain is daffodilvarsity.edu.bd; package names are a convention, not DNS. "Hibernate" in the mandatory stack is simply the JPA provider under Spring Data JPA.

---

## 3. Database architecture review

The proposal's entity inventory (§12.1) is sound. Below are the corrections and additions per domain. Conventions: UUID primary keys (v4 via `gen_random_uuid()`), `timestamptz` in UTC, `created_at`/`updated_at` on mutable tables, enumerations stored as `text` with CHECK constraints (easier to evolve than native enums), foreign keys on every reference, Flyway versioned migrations only (no `ddl-auto`).

### Identity
- `users`: `email` (unique, lowercased), `email_verified_at`, `password_hash` (Argon2id), `full_name`, `account_status` CHECK (PENDING | VERIFIED | REJECTED | SUSPENDED | DEACTIVATED), `status_changed_at`, `deactivated_at`, `last_login_at`.
- `roles` (seed: MEMBER, MODERATOR, ADMIN, SUPER_ADMIN) and `user_roles` (user_id, role_id, granted_by, granted_at, PK on the pair). Every user receives MEMBER at registration; community access is gated by `account_status`, never by role.
- `email_verification_tokens`, `password_reset_tokens`: store `token_hash` (SHA-256), `expires_at`, `used_at`; raw tokens are only ever in the email.
- `mfa_credentials` (user_id unique, `totp_secret_enc`, `enabled_at`, `last_used_step`) and `mfa_recovery_codes` (`code_hash`, `used_at`). **Missing from the proposal.**
- `refresh_tokens`: **dropped** (sessions live in Redis via Spring Session; D-03).

### Affiliation
- `user_affiliations`: user_id, `affiliation_type` CHECK (STUDENT | ALUMNI | CSC_MEMBER), `status` (PENDING | VERIFIED | REVOKED), `student_id`, `batch`, `department`, `graduation_year`, `csc_member_id`, `verified_by_submission_id`, unique (user_id, affiliation_type). The proposal's registration payload fields live here, not on `users`.

### Verification
- `verification_submissions`: user_id, affiliation_id, `status` (DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | WITHDRAWN), `submitted_at`, `assigned_reviewer_id`, `assigned_at`, `decided_at`, `attempt_number`. Partial unique index on `(user_id, affiliation_id) WHERE status IN ('DRAFT','SUBMITTED','UNDER_REVIEW')` enforces "one active submission".
- `verification_documents`: submission_id, `document_type`, `storage_key` (random, opaque), `content_type`, `size_bytes`, `sha256`, `scan_status` (PENDING | CLEAN | INFECTED | ERROR), `scanned_at`, `encryption_key_id`, `purged_at`. No URL is ever stored.
- `verification_reviews` (append-only): submission_id, reviewer_id, `decision` (APPROVE | REJECT | REQUEST_RESUBMISSION), `reason_code`, `reason_text_applicant` (shown), `internal_notes` (never shown), `created_at`.
- `rejection_reason_codes`: code, applicant_text, `allows_resubmission`, `visible_to_applicant`.
- `appeals`: submission_id (unique), `status` (SUBMITTED | UNDER_REVIEW | APPROVED | DENIED), `statement`, `evidence_document_id` (nullable), `decided_by`, `decided_at`, `decision_text_applicant`, `internal_notes`. The rule that `decided_by` differs from the rejecting reviewer is enforced in the application layer (needs a join) plus an integration test.
- `document_access_log`: document_id, viewer_id, `purpose`, `ip`, `created_at`. Every document view is recorded.

### Profile
- `user_profiles` (1:1 with users): headline, bio, `specializations text[]`, `organization_name`, `job_title`, `country`, `city`, `mentor_availability`, `mentor_topics`, avatar `storage_key`, plus the privacy columns from D-13, `search_vector tsvector` generated column.
- `skills` (normalised name, unique), `user_skills` (PK user_id+skill_id, `level`).
- `profile_experiences`, `profile_educations`: **added** (the UI in §20 lists experience and education; the schema did not).
- `organizations`: deferred; free text on the profile for MVP, canonical table later.

### Networking
- `connections`: requester_id, addressee_id, `status` (PENDING | ACCEPTED | DECLINED | REMOVED), CHECK requester ≠ addressee, unique index on `(LEAST(requester_id, addressee_id), GREATEST(...))` to block reversed duplicates.
- `mentorship_requests`: mentee_id, mentor_id, `status`, message, CHECK mentee ≠ mentor, partial unique on active pairs.

### Community
- `posts`: author_id, `intent` (JOB_OPPORTUNITY | RESEARCH_COLLABORATION | SEEKING_HELP | MENTORSHIP | TECHNICAL_DISCUSSION | ACHIEVEMENT | EVENT), `body` (plain text, server-sanitised, max 5,000 chars), `visibility`, `repost_of_post_id` (nullable, implements shares), `deleted_at`, `deleted_by`, `search_vector`. Counters (`like_count`, `comment_count`) are denormalised columns updated inside the same transaction as the like/comment and never writable through any API.
- `post_attachments` (images only in MVP), `comments` (`parent_comment_id`, one reply level, soft delete), `post_likes` (PK post_id+user_id), `bookmarks` (PK user_id+post_id), `hashtags`, `post_hashtags`.
- `post_shares` is replaced by `repost_of_post_id` on `posts` (a repost is a post); keeps queries simple.
- `reports`: reporter_id, `target_type` (POST | COMMENT | MESSAGE | USER | OPPORTUNITY | RESEARCH_PROJECT), `target_id`, `reason_code`, `details`, `snapshot jsonb` (content at report time), `status`, `resolved_by`, `resolution`. Polymorphic target is acceptable here; integrity is enforced by the application and a CHECK on `target_type`.

### Opportunities and research
- `opportunities`: author_id, `type` (FULL_TIME | PART_TIME | INTERNSHIP | RESEARCH_ASSISTANT | FREELANCE | SCHOLARSHIP), title, `organization_name`, `location_type`, location, description, `deadline`, `status` (DRAFT | OPEN | CLOSED | EXPIRED | REMOVED), `application_method` (INTERNAL | EXTERNAL_URL | EMAIL), `application_url`, `application_email`, `search_vector`. MENTORSHIP removed (D-10).
- `opportunity_skills`, `applications` (unique opportunity_id+applicant_id, `status`, cover note, `cv_storage_key` optional).
- `research_projects` (lead_id, title, summary, `status`, `needed_skills`, `search_vector`), `research_members` (PK project_id+user_id, `role` LEAD | MEMBER), `research_applications` (unique project_id+applicant_id).

### Messaging
- `conversations`: `type` (DIRECT | GROUP), `direct_key` (sorted "uuidA:uuidB", unique, nullable for groups) prevents duplicate direct conversations.
- `conversation_members`: PK conversation_id+user_id, `state` (ACTIVE | REQUEST | ARCHIVED | LEFT), `last_read_message_id`. "Message request" is the recipient's member state, not a separate table.
- `messages`: conversation_id, sender_id, body, `created_at`, `deleted_at`; keyset pagination on (conversation_id, created_at, id).
- `message_attachments` (deferred), `blocks` (PK blocker_id+blocked_id, CHECK blocker ≠ blocked).

### Impact
- `impact_records`: `impact_type` (JOB_REFERRAL | RESEARCH | MENTORSHIP), `opportunity_id`, `research_project_id`, `mentorship_request_id` with CHECK that exactly one is non-null and matches the type; beneficiary_id, contributor_id, CHECK beneficiary ≠ contributor, `proposed_by`, `status` (PENDING_CONFIRMATION | CONFIRMED | DISPUTED | REJECTED), `beneficiary_confirmed_at`, `contributor_confirmed_at`, `verified_at`, `resolved_by`. Unique on (source id, beneficiary_id, contributor_id).
- `impact_confirmations`: PK impact_id+user_id, `role` (BENEFICIARY | CONTRIBUTOR), `decision` (CONFIRM | DISPUTE), note. Public and profile statistics are computed with `WHERE status='CONFIRMED'`; nothing user-editable.

### Notifications, governance
- `notifications`: user_id, `type`, `payload jsonb`, `read_at`, index on (user_id, read_at, created_at). `notification_preferences`: per-type in-app/email flags.
- `moderation_actions`: actor_id, target_user_id, `action` (SUSPEND | UNSUSPEND | REMOVE_CONTENT | RESTORE_CONTENT | DEACTIVATE | WARN), reason, `expires_at`.
- `audit_logs` (append-only; app role cannot UPDATE/DELETE): actor_id (nullable for system), `actor_roles_snapshot`, `action`, `target_type`, `target_id`, `outcome`, `metadata jsonb` (never secrets or document bytes), `ip`, `user_agent`, `request_id`, `created_at`. Indexed on (target_type, target_id) and (actor_id, created_at).
- `system_settings`: key, value jsonb, `updated_by`.

### Integrity rules confirmed from §12.3
All eight are covered above. Additional: CHECK constraints on every status column; partial unique indexes for "one active" rules; no public counters as editable columns.

---

## 4. Security and privacy risk register

Beyond the proposal's §14, these are the risks that shape the design:

| # | Risk | Treatment |
|---|---|---|
| R-01 | Identity document exposure through reviewer tooling | Backend streaming endpoint only (no presigned URLs, storage never reachable from the browser), `Cache-Control: no-store`, `Content-Disposition: inline`, per-view `document_access_log` entry, step-up MFA before first view in a session, no download button by default. |
| R-02 | Insider misuse by privileged users | Fine-grained authorities (`VERIFICATION_REVIEW`, `DOCUMENT_VIEW`, `APPEAL_DECIDE`, `ROLE_ASSIGN`, …) mapped to roles; ADMIN does not implicitly hold `DOCUMENT_VIEW`; SUPER_ADMIN cannot be self-assigned and the last SUPER_ADMIN cannot be removed; every grant audited. |
| R-03 | Stale sessions after suspension/rejection/role change | Server-side sessions in Redis; all sessions of the user are destroyed on these events and on password change. |
| R-04 | Account enumeration and credential stuffing | Uniform responses for register/forgot-password, constant-time comparisons, Redis rate limits per IP and per account, progressive lockout, Argon2id with tuned parameters (target ~250 ms). |
| R-05 | Malicious uploads (polyglots, PDF JavaScript, oversized files, path tricks) | Allowlist by magic bytes not extension, size cap enforced before buffering, random storage keys, image re-encode + EXIF strip, PDF structural checks, ClamAV scan before the document becomes reviewable, quarantine state on failure. |
| R-06 | IDOR across every resource | Authorization is always `principal + ownership/membership + account status`; UUID unguessability is never relied upon; an integration test matrix (pending/rejected/verified/suspended × endpoint) runs in CI. |
| R-07 | Privilege escalation via mass assignment | Separate request DTOs per endpoint; `role`, `account_status`, counters and verification fields never bindable; entities never serialised. |
| R-08 | XSS in user content | Posts/comments/messages are plain text with server-side autolinking only; no HTML accepted; CSP with per-request nonce from Next.js; external links rendered with `rel="noopener noreferrer nofollow"`. |
| R-09 | CSRF with cookie auth | `SameSite=Lax` + Spring Security CSRF token (cookie, read by the frontend, sent as header) on every non-GET; WebSocket handshake checks `Origin`. |
| R-10 | Secrets and PII in logs/backups | Structured JSON logs with a deny-list filter (passwords, tokens, cookies, document bytes); emails masked in logs; encrypted backups; backup restore drill in M8. |
| R-11 | Data minimisation and retention | Only the fields in D-09 are collected; documents purged on a configurable schedule; deactivated accounts anonymised after the restore window; retention periods surfaced in the privacy notice. |
| R-12 | SSRF via external application URLs | The server never fetches `application_url`; scheme restricted to https/http; validated as absolute URL. |
| R-13 | Admin surfaces exposed | Swagger UI and Actuator (except health) disabled in the `prod` profile; database, Redis, storage, ClamAV only on the internal `data` network. |
| R-14 | Supply chain | Lockfiles committed, Dependabot, Trivy on images, OWASP Dependency-Check weekly, Semgrep in CI, pinned base images. |
| R-15 | Message privacy vs moderation | Moderators cannot read conversations; reports carry a snapshot of the reported message only (D-14). |
| R-16 | Legal basis for identity processing | Blocked on institutional approval (§4.1, §14.4). Development uses fictional data only; a feature flag keeps real-document upload disabled until approval is recorded in `system_settings`. |

---

## 5. Proposed repository structure

```
cyberconnect-diu/
├── .github/workflows/
│   ├── ci.yml                     # backend verify, frontend lint/typecheck/test/build, image build, Trivy
│   ├── e2e.yml                    # Playwright against the compose stack (main + nightly)
│   └── security.yml               # Semgrep, OWASP Dependency-Check (weekly)
├── backend/
│   ├── pom.xml
│   ├── Dockerfile                 # multi-stage: maven build -> temurin 21 JRE, non-root, layered jar
│   └── src/
│       ├── main/java/edu/diu/cyberconnect/
│       │   ├── CyberConnectApplication.java
│       │   ├── common/            # config, security (filters, CSRF, session), exception (ProblemDetail),
│       │   │                      # validation, events, storage (S3 port), mail (port), ratelimit, scanning (port)
│       │   ├── identity/          # api | application | domain | infrastructure
│       │   ├── affiliation/
│       │   ├── verification/
│       │   ├── profile/
│       │   ├── networking/
│       │   ├── community/
│       │   ├── opportunity/
│       │   ├── research/
│       │   ├── messaging/
│       │   ├── notification/
│       │   ├── impact/
│       │   ├── moderation/
│       │   ├── audit/
│       │   └── admin/             # role management, system settings, overview metrics
│       ├── main/resources/
│       │   ├── application.yml, application-local.yml, application-docker.yml, application-prod.yml
│       │   └── db/migration/V001__baseline.sql ...
│       └── test/java/...          # unit, integration (Testcontainers), ArchUnit boundary tests
├── frontend/
│   ├── package.json, pnpm-lock.yaml, next.config.ts, tsconfig.json, components.json
│   ├── Dockerfile                 # multi-stage, output: standalone, non-root
│   ├── public/brand/              # diu-logo.png, csc-diu-logo.png
│   ├── e2e/                       # Playwright
│   └── src/
│       ├── app/
│       │   ├── (public)/          # landing, about, eligibility, sign-in, register, password, privacy, terms
│       │   ├── (onboarding)/      # status, upload, rejected, appeal
│       │   ├── (community)/       # feed, members, profile, opportunities, research, mentorship, messages, notifications, saved, settings
│       │   ├── admin/             # moderation, verification queue, appeals, reports, audit, roles
│       │   └── healthz/route.ts
│       ├── components/{ui,layout,navigation,shared}/
│       ├── features/{auth,verification,profile,members,feed,opportunities,research,messaging,notifications,impact,admin}/
│       ├── lib/{api,auth,validation}/
│       ├── hooks/ , types/ , styles/tokens.css
├── infrastructure/
│   ├── nginx/{default.conf, prod.conf.template}
│   ├── postgres/init/01-roles.sql # creates cyberconnect_app role with restricted grants
│   ├── storage/init.sh            # creates private bucket(s)
│   ├── clamav/
│   ├── monitoring/                # M8
│   └── backup/                    # M8
├── docs/
│   ├── proposal/                  # the PDF
│   ├── planning/                  # this document, milestone reports
│   ├── architecture/adr/          # ADR-0001 modular monolith, 0002 sessions, 0003 routing, 0004 storage ...
│   ├── api/ , database/ , security/ , ux/
├── scripts/                       # dev.ps1 / dev.sh helpers
├── docker-compose.yml             # full stack
├── docker-compose.override.yml    # dev: published ports, mailpit, storage console
├── docker-compose.prod.yml        # TLS, migrate job, secrets files, resource limits
├── .env.example, .gitignore, .editorconfig, README.md, CLAUDE.md
```

Module boundary rule: a module exposes only its `api` (REST) and `application` (service interfaces + DTOs + events) packages; `domain` and `infrastructure` are package-private to the module. An ArchUnit test fails the build on cross-module repository or entity access.

---

## 6. Docker architecture

### Services

| Service | Image | Networks | Purpose | Health |
|---|---|---|---|---|
| nginx | nginx:1.28-alpine | edge, app | Only published port (80; 443 in prod). Routes `/api/*`, `/ws` → backend; `/` → frontend. Security headers, body size limit, request id. | `/healthz` via frontend |
| frontend | built, node:24-alpine | app | Next.js standalone server | `GET /healthz` |
| backend | built, eclipse-temurin:21-jre-alpine | app, data | Spring Boot API + WebSocket | `GET /actuator/health/readiness` |
| postgres | postgres:17-alpine | data | Database; init script creates the restricted app role | `pg_isready` |
| redis | redis:7.4-alpine | data | Sessions, rate limits, cache, pub/sub | `redis-cli ping` |
| storage | rustfs/rustfs (pinned at M1) | data | S3-compatible private object storage (D-02) | HTTP health |
| storage-init | amazon/aws-cli (pinned) | data | One-shot: create private buckets | exits 0 |
| clamav | clamav/clamav (pinned) | data | clamd scanning, `profiles: [scanning]` | `clamdcheck.sh` |
| migrate (prod only) | backend image | data | Runs Flyway then exits, before backend starts | exits 0 |
| mailpit (dev only) | axllent/mailpit | data | SMTP sink + UI on 8025 | HTTP |

Networks: `edge` (default bridge, only nginx), `app` (internal), `data` (internal). Volumes: `postgres-data`, `redis-data`, `storage-data`, `clamav-db`.

### Compose draft (development baseline; prod overrides add TLS, secrets files, limits)

```yaml
name: cyberconnect-diu
services:
  nginx:
    image: nginx:1.28-alpine
    ports: ["80:80"]
    volumes:
      - ./infrastructure/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      frontend: { condition: service_healthy }
      backend:  { condition: service_healthy }
    networks: [edge, app]

  frontend:
    build: ./frontend
    environment:
      NODE_ENV: production
      INTERNAL_API_URL: http://backend:8080
    expose: ["3000"]
    networks: [app]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1:3000/healthz"]
      interval: 10s
      timeout: 3s
      retries: 10

  backend:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_URL: jdbc:postgresql://postgres:5432/cyberconnect
      DB_MIGRATOR_USER: ${DB_MIGRATOR_USER}
      DB_MIGRATOR_PASSWORD: ${DB_MIGRATOR_PASSWORD}
      DB_APP_USER: ${DB_APP_USER}
      DB_APP_PASSWORD: ${DB_APP_PASSWORD}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      STORAGE_ENDPOINT: http://storage:9000
      STORAGE_REGION: us-east-1
      STORAGE_ACCESS_KEY: ${STORAGE_ACCESS_KEY}
      STORAGE_SECRET_KEY: ${STORAGE_SECRET_KEY}
      STORAGE_BUCKET_DOCUMENTS: verification-documents
      STORAGE_BUCKET_MEDIA: member-media
      DOCUMENT_ENCRYPTION_KEY: ${DOCUMENT_ENCRYPTION_KEY}
      CLAMAV_HOST: clamav
      CLAMAV_PORT: "3310"
      SMTP_HOST: ${SMTP_HOST:-mailpit}
      SMTP_PORT: ${SMTP_PORT:-1025}
      APP_BASE_URL: ${APP_BASE_URL:-http://localhost}
    expose: ["8080"]
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
      storage:  { condition: service_healthy }
      storage-init: { condition: service_completed_successfully }
    networks: [app, data]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1:8080/actuator/health/readiness"]
      interval: 10s
      timeout: 3s
      retries: 20
      start_period: 40s

  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: cyberconnect
      POSTGRES_USER: ${DB_MIGRATOR_USER}
      POSTGRES_PASSWORD: ${DB_MIGRATOR_PASSWORD}
      APP_DB_USER: ${DB_APP_USER}
      APP_DB_PASSWORD: ${DB_APP_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init:/docker-entrypoint-initdb.d:ro
    networks: [data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.4-alpine
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}", "--appendonly", "yes"]
    volumes: [redis-data:/data]
    networks: [data]
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a $${REDIS_PASSWORD} ping | grep -q PONG"]
      interval: 10s
      timeout: 3s
      retries: 5

  storage:
    image: rustfs/rustfs:<pin-at-M1>
    environment:
      RUSTFS_ACCESS_KEY: ${STORAGE_ACCESS_KEY}
      RUSTFS_SECRET_KEY: ${STORAGE_SECRET_KEY}
    volumes: [storage-data:/data]
    expose: ["9000"]
    networks: [data]
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:9000/health || wget -qO- http://127.0.0.1:9000/health"]
      interval: 10s
      timeout: 3s
      retries: 10

  storage-init:
    image: amazon/aws-cli:<pin-at-M1>
    entrypoint: ["/bin/sh", "/init.sh"]
    environment:
      AWS_ACCESS_KEY_ID: ${STORAGE_ACCESS_KEY}
      AWS_SECRET_ACCESS_KEY: ${STORAGE_SECRET_KEY}
      AWS_EC2_METADATA_DISABLED: "true"
    volumes: [./infrastructure/storage/init.sh:/init.sh:ro]
    depends_on:
      storage: { condition: service_healthy }
    networks: [data]

  clamav:
    image: clamav/clamav:<pin-at-M1>
    profiles: [scanning]
    volumes: [clamav-db:/var/lib/clamav]
    networks: [data]
    healthcheck:
      test: ["CMD", "clamdcheck.sh"]
      interval: 30s
      timeout: 10s
      retries: 10
      start_period: 180s

volumes:
  postgres-data:
  redis-data:
  storage-data:
  clamav-db:

networks:
  edge:
  app:
    internal: true
  data:
    internal: true
```

`docker-compose.override.yml` (dev only) adds Mailpit (`8025` UI, `1025` SMTP), publishes `5432`, `6379`, `9000` on localhost so the apps can run natively, and mounts nothing from the source tree into runtime images.

### Dockerfiles
- Backend: `maven:3.9-eclipse-temurin-21` build stage with dependency cache layer → `eclipse-temurin:21-jre-alpine`, non-root user, Spring Boot layered jar, `JAVA_TOOL_OPTIONS` for container memory.
- Frontend: `node:24-alpine` with corepack/pnpm, `next build` with `output: 'standalone'` → runtime stage copying `.next/standalone` and `.next/static`, non-root.

### Daily development workflow (proposed default)
Infrastructure in Docker (`docker compose up postgres redis storage mailpit`), backend and frontend run natively for fast reload. The full compose stack is used for integration verification at the end of each milestone and in CI's E2E job. On Windows this avoids slow bind-mount file watching.

---

## 7. Milestone plan

Each milestone follows the brief's loop: explain → list files → plan → implement → test → fix → verify Docker → update docs → summarise → list remaining work. Durations are indicative for a small team.

### M1 — Foundation (1–2 weeks)
Scope: git init with `.gitignore`/`.editorconfig`/`README`/`CLAUDE.md`; backend skeleton (Spring Boot 4.1, Java 21, Maven; module packages; ProblemDetail exception handling; security baseline denying everything except health and public routes; Spring Session Redis wiring; Actuator; springdoc; Flyway `V001__baseline.sql` with extensions, roles seed and `system_settings`; S3, mail and scanner ports with dev adapters; ArchUnit boundary test; Testcontainers smoke test); frontend skeleton (Next.js 16, TypeScript strict, Tailwind v4, shadcn/ui, Lucide, TanStack Query provider, design tokens for the §9.2 palette and dark mode, app shells for the four route groups, landing page placeholder, `/healthz`, brand assets); Docker (both Dockerfiles, compose + override, nginx config, postgres role init, storage init); CI (backend `mvn verify`, frontend lint/typecheck/build, image builds, Trivy).
Exit criteria: `docker compose up --build` serves the landing page through nginx; `GET /api/v1/health` and readiness pass; Flyway baseline applied under the migrator role and the app role cannot alter audit tables; CI green on the remote.

### M2 — Identity and verification (2–3 weeks)
Backend: registration, Argon2id, login/logout, session management, email verification, password reset, password policy, rate limiting, account-status authorization filter, roles/authorities, TOTP MFA + recovery codes + step-up; affiliation rules; submissions, document pipeline (validate → re-encode/strip → scan → encrypt → store), reviewer queue, assignment, approve/reject with reason codes, resubmission, appeals, applicant status, reviewer document streaming with access log; audit module; suspend/unsuspend; role assignment with constraints.
Frontend: landing, registration wizard, sign-in, verify email, forgot/reset, onboarding status, document upload, rejected + appeal, moderator queue/detail/document viewer/decision panel, appeal review, audit timeline, MFA enrolment, admin role management.
Tests: state-machine unit tests; authorization matrix integration tests; IDOR tests; upload abuse tests (wrong magic bytes, oversize, EICAR with ClamAV profile); Playwright scenarios 1–5 and 9 from §16.2.
Exit: a pending or rejected account receives 403 `ACCOUNT_NOT_VERIFIED` on every community endpoint; every decision has an audit row; reviewer without MFA cannot open a document.

### M3 — Profiles, directory, connections (2 weeks)
Profile CRUD, skills, experience, education, avatar upload (media bucket), privacy settings, directory search with full-text + filters + pagination, member view honouring privacy, connection requests, settings page. Tests: privacy filtering matrix, search authorization.

### M4 — Community (2–3 weeks)
Posts with intents and image attachments, comments and one-level replies, likes, reposts, bookmarks, hashtags, keyset-paginated feed, reports, content moderation actions, soft delete, three-column desktop shell and mobile bottom navigation, saved posts. Tests: sanitisation, counter consistency, moderation visibility.

### M5 — Opportunities, research, mentorship (2 weeks)
Opportunity lifecycle and applications (internal/external/email), research projects, members and applications, mentor availability and mentorship requests, right-sidebar recommendations. Playwright: publish → apply → close.

### M6 — Communication (2–3 weeks)
Direct conversations, message requests, blocks, STOMP WebSocket with session auth and origin check, in-app notifications with real-time push and preferences, message reporting with snapshot. Tests: conversation-membership authorization (scenario 7), WebSocket auth rejection.

### M7 — Impact tracking (1–2 weeks)
Impact records, dual confirmation, disputes with moderator resolution, profile and admin statistics computed from CONFIRMED only, public aggregate counters. Tests: scenario 8.

### M8 — Production readiness (1–2 weeks)
OWASP ZAP baseline, Semgrep, Dependency-Check, manual IDOR/session/upload review; k6 load test on feed, directory, login and upload; axe accessibility checks in Playwright; prod compose with TLS, secrets files, migrate job, resource limits; monitoring (Actuator metrics + log shipping, uptime checks); backup job for PostgreSQL and object storage with a documented and rehearsed restore; retention purge verified; runbook and handover documentation.

Indicative total: 13–19 weeks for a small team; longer for a solo developer.

---

## 8. Decisions requiring confirmation

| # | Decision | Recommendation |
|---|---|---|
| D-01 | Spring Boot line | **Spring Boot 4.1.x** (3.x OSS support ended 30 June 2026). |
| D-02 | Object storage for dev/CI | **RustFS** behind the S3 port; Garage as fallback; production bucket chosen with D-21. |
| D-03 | Authentication model | **Redis-backed server sessions** + CSRF token; drop `/auth/refresh` and `refresh_tokens`. |
| D-04 | Request routing | **nginx path routing** to backend and Next.js on one origin; Next.js is not a BFF. |
| D-05 | Login before email verification | **Allowed**; document submission and appeals require a verified email. |
| D-06 | Resubmission vs appeal | **Both**: reason codes flag resubmittable rejections; max 3 submissions per affiliation; one appeal per rejected submission. |
| D-07 | Terminal states | Denied appeal is final (admin reopen only); suspension liftable with audit; user deactivation has a 30-day restore window. |
| D-08 | Multiple affiliations | One primary at registration; extra affiliations via additional submissions. |
| D-09 | Eligibility fields and documents | Table in F-16; configurable; permissive student-id regex until DIU format is confirmed. |
| D-10 | Mentorship model | Profile availability + `mentorship_requests`; **not** an opportunity type. |
| D-11 | Share semantics | Repost with optional commentary; no external share. |
| D-12 | Impact record rules | Either party proposes; counterpart confirms/disputes; on-platform source required; moderator resolves disputes. |
| D-13 | Privacy settings | Set listed in F-24. |
| D-14 | Moderator access to messages | Never browse; reported-message snapshot only. |
| D-15 | Malware scanning | ClamAV via compose profile; stub in CI; mandatory in production. |
| D-16 | Email | Mailpit in dev; production SMTP account owner to be named. |
| D-17 | MFA | TOTP + recovery codes; mandatory for privileged roles; step-up for sensitive actions; delivered in M2. |
| D-18 | Document access and encryption | Backend streaming with per-view audit; AES-256-GCM application-level encryption before storage. |
| D-19 | Separation of duties | Enforce: no self-review; appeal reviewer ≠ rejecting reviewer. |
| D-20 | Document retention | Configurable, default 30 days after final decision, scheduled purge; institution to confirm. |
| D-21 | Deployment target | **Information needed**: DIU-hosted, VPS or cloud. |
| D-22 | Git hosting / CI | **Information needed**: GitHub organisation and repository name. |
| D-23 | Logo use | Use supplied DIU and CSC DIU logos now; written permission before launch. |
| D-24 | Accepted document types | JPEG, PNG, PDF; 8 MB per file; max 3 per submission. |

Defaults I will apply without a decision unless objected to: PostgreSQL full-text search, Lua token-bucket rate limiting in Redis, two database roles with append-only audit tables, community-wide post visibility, soft deletes for user content, pnpm as the frontend package manager, UUID v4 keys, text + CHECK enumerations, STOMP SimpleBroker, native app development against Dockerised infrastructure.

---

## 9. Sources consulted

- Spring Boot 3.5 end of OSS support: HeroDevs — [Spring Boot 3.5 EOL: what the migration really takes](https://www.herodevs.com/blog-posts/spring-boot-3-5-eol-what-the-migration-really-takes); Dan Vega — [Spring Boot end of life](https://www.danvega.dev/blog/spring-boot-end-of-life); endoflife.ai — [June 30 EOL wave](https://endoflife.ai/article-spring-june-30-eol-wave); InfoQ — [Spring Boot 3.5](https://www.infoq.com/news/2025/05/spring-boot-3-5).
- MinIO community edition status: It's FOSS — [MinIO moves away from open source](https://itsfoss.com/news/minio-moves-away-from-open-source/); Pinggy — [MinIO archived: self-hosted S3 alternatives](https://pinggy.io/blog/minio_archived_self_hosted_s3_alternatives/); Chainguard — [Secure and free MinIO containers](https://www.chainguard.dev/de-DE/unchained/secure-and-free-minio-chainguard-containers); glukhov.org — [MinIO CE in 2026](https://glukhov.org/data-infrastructure/object-storage/minio-dead/); madewithlove — [Thanks for all the buckets](https://madewithlove.com/blog/thanks-for-all-the-buckets/).
- Next.js release line: versio.io — [Next.js releases and EOL](https://www.versio.io/de/product-release-end-of-life-eol-vercel-nextjs.html); releases.sh — [vercel/nextjs](https://releases.sh/vercel/nextjs.md).
- Registry checks performed locally with `docker manifest inspect` on 30 October 2026.
