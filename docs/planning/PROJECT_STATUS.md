# Project status

Updated: 7 Nov 2026 (session 2). Read `CLAUDE.md` first.

## Milestones
| Milestone | State |
|---|---|
| M1 Foundation | Done. Commit `16c3229 chore: initialize project foundation`. |
| M2 Identity & verification | Done and verified on 30 Oct 2026: `mvn verify` 49 tests green (1 opt-in ClamAV IT skipped), frontend lint/typecheck/build green, Playwright 12/12 against the nginx stack with the rebuilt backend image. Committed as `feat(identity,verification): …` (hash in `git log`). |
| M3 Profiles & networking | Done and verified on 30 Oct 2026: `mvn verify` 56 tests green (1 opt-in skip), frontend lint/typecheck/build green, Playwright 12/12 against the rebuilt stack (V003 applied). Committed as `feat(profile,networking): …`. |
| M4 Community | Done and verified on 30 Oct 2026: `mvn verify` 62 tests green (1 opt-in skip), frontend lint/typecheck/build green, Playwright 12/12 against the rebuilt stack (V004 applied). Committed as `feat(community): …`. |
| M5 Opportunities, research, mentorship | Done and verified on 30 Oct 2026: `mvn verify` 66 tests green (1 opt-in skip), frontend lint/typecheck/build green, Playwright 14/14 against the rebuilt stack (V005 applied) including publish→apply→close. Committed as `feat(opportunity,research,networking): …`. |
| M6 Communication | Done and verified on 30 Oct 2026: `mvn verify` 72 tests green (1 opt-in skip), frontend lint/typecheck/build green, Playwright 26/26 against the rebuilt stack (V006 applied) including the message-request flow. Committed as `feat(messaging,notification): …`. |
| M7 Impact | Done and verified on 30 Oct 2026 (`ImpactIT`, frontend lint/typecheck/build, Playwright against the rebuilt stack). Committed as `feat(impact): …`. |
| M8 Production readiness | Done on 30 Oct 2026: production Compose overrides + TLS nginx config, start-up guards, management port with Prometheus, request ids, notification retention, backup/restore/deploy scripts, operations docs, security checklist, axe-core accessibility spec, CI e2e job. Final full verification: `mvn verify` 72 tests green (1 opt-in skip), Playwright 26/26 (onboarding, opportunities, messaging, accessibility). Committed as `chore(ops): …`. Not deployed (no VPS yet, per mandate). |

## After M8 (7 Nov 2026)
- Social-style feed UI: "Start a post" composer with Photo / Document / Event / Achievement shortcuts, drag-and-drop
  and paste, immediate uploads with previews; post cards with author headline, audience icon, "⋯" menu (save, copy
  link, edit, delete, report), "see more", photo grid with full-screen viewer, document cards, reaction counts and a
  Like / Comment / Repost / Save action bar; profile card with cover band in the right rail.
- Post attachments (V008, D-47/D-48): `PostAttachmentService`, upload/discard/serve endpoints, moderator serving path,
  hourly purge of unclaimed uploads, `PostAttachmentIT`.
- Mail delivery failures are logged, not thrown (D-49, `SmtpMailSenderTest`).
- Responsive full-width layout, QR code for MFA enrolment, Railway guide, `scripts/share-dev.ps1` public test link.

## What exists (M1–M8)
- M7: `impact` module (records with exactly one on-platform source, proposer-side confirmation, counterpart
  confirm/dispute, proposer withdraw, moderator resolution with audit, CONFIRMED-only statistics, member impact on
  profiles), cross-module source lookups (`ApplicationLink`, `ResearchLink`, `MentorshipLink`), notifications
  IMPACT_PROPOSED/IMPACT_RESOLVED. Frontend: `/impact` (stats, my records with confirm/dispute/withdraw, propose from
  eligible sources), profile "Confirmed impact" card, admin `/admin/impact` dispute queue.
- M8: `docker-compose.prod.yml`, `infrastructure/nginx/prod.conf` (TLS, HSTS, CSP, rate limits), `ProductionConfigGuard`,
  `RequestIdFilter` (MDC + `X-Request-Id`), Prometheus registry on management port 8081 (prod), `NotificationRetentionJob`,
  `scripts/{deploy,backup,restore}.sh`, `docs/operations/*`, `docs/security/checklist.md`, ADR-0009,
  `e2e/accessibility.spec.ts` (axe-core), CI e2e job (manual until 1 Nov 2026).
- M6: `messaging` module (one direct conversation per pair, policy-based message requests with accept/decline,
  membership checks on every call, keyset history, read state, archive, own-message deletion, blocking, MESSAGE
  reports with snapshot and moderator removal without browsing), `notification` module (in-app records with
  per-type preferences and mandatory types, after-commit STOMP push and email; producers in networking, mentorship,
  community, opportunity, research, verification, moderation), STOMP WebSocket at `/ws` (session cookie, origin
  check, verified accounts, CSRF double submit on CONNECT, read-only user queues). Frontend: `/messages` two-pane UI
  with inbox/requests/archived, `/notifications`, header bell, `/settings/notifications`, "Message" on profiles,
  realtime provider that refreshes queries on push. Tests: `MessagingIT`, `NotificationIT`, `WebSocketIT`,
  authorization matrix extended, Playwright `messaging.spec.ts`.
- M5: `opportunity` module (DRAFT→OPEN→CLOSED/EXPIRED/REMOVED, INTERNAL/EXTERNAL_URL/EMAIL methods, applications
  with author decisions, skills from the shared catalogue, FTS search with block/suspension rules, nightly expiry,
  moderator remove/restore), `research` module (projects, needed skills, applications → members, lead management,
  statuses, moderation), mentorship requests in `networking` (availability from the profile, accept/decline/
  complete/cancel), dev-only fixtures endpoint for Playwright (`E2E_FIXTURES_ENABLED`, refused in prod).
  Frontend: `/opportunities` (browse/mine/applications, form, detail with apply/applicants/manage/moderation),
  `/research` (browse/mine/applications, form, detail with members/applications), Mentorship tab under
  `/connections`, "Request mentorship" on profiles. Tests: `OpportunityIT`, `ResearchIT`, `MentorshipIT`,
  authorization matrix extended, Playwright `opportunities.spec.ts`.
- M4: `community` module (plain-text posts with intents/visibility, hashtags, one-level comments with placeholders,
  likes, reposts with commentary, bookmarks, trending tags, feed with visibility/blocking/suspension rules in SQL,
  reports with snapshots, moderator queue + resolve/remove/restore, audited). Frontend: feed at `/home` (composer,
  intent chips, search, hashtag filter), `/posts/[postId]` with comments, `/saved`, report dialog, posts on member
  profiles, trending rail, admin `/admin/reports` queue and detail. Tests: `CommunityIT`, `PlainTextTest`,
  authorization matrix extended.
- M3: `profile` module (profiles with per-field privacy, skills catalogue, experience/education, sanitised photos in
  the media bucket streamed via `/members/{id}/photo`, FTS + trigram directory with filters), `networking` module
  (connection requests with auto-accept of reverse requests, accept/decline/cancel/remove, blocks; implements the
  profile module's `RelationshipLookup` port). Frontend: `/members`, `/members/[userId]`, `/connections`,
  `/settings/profile`, `/settings/privacy` (+ settings tab nav), rail with profile/network summary.
  Tests: `ProfileAndNetworkingIT` (ownership, mass assignment, privacy matrix, directory filters, blocking,
  connection lifecycle, photo), `ImageSanitizerTest`, authorization matrix extended to the new endpoints.
- Backend modules: common (security, storage, crypto, mail, scanning, rate limit, exception handling), identity
  (registration, login + MFA second step, email verification, password reset, sessions in Redis with revocation,
  TOTP MFA + recovery codes, step-up, super-admin bootstrap), affiliation (eligibility rules, primary/additional
  affiliations), verification (drafts, document pipeline, submissions, review queue, approve/reject with reason codes,
  resubmission limits, appeals with separation of duties, document streaming with access log, retention purge),
  moderation (suspend/unsuspend/deactivate/reactivate/reopen, scheduled lifting), admin (member search, roles with
  SUPER_ADMIN safeguards, settings, audit query), audit (append-only + query).
- Migrations: `V001__baseline.sql`, `V002__identity_and_verification.sql` (see `docs/database/schema.md`).
- Frontend: landing, register wizard, sign-in (+ MFA step), verify-email, forgot/reset password, privacy, terms,
  onboarding hub (email step, evidence upload, decision, appeal), community shell (`/home`, `/settings/security`),
  admin shell with step-up banner: verification queue, submission review with document viewer and decision panel,
  appeals queue/review, members list/detail (roles, lifecycle actions), settings editor, audit log.
- Tests: unit (AES-GCM, TOTP vectors, password policy, state machine, document pipeline, ClamAV protocol),
  ArchUnit boundaries, ITs (foundation, S3/RustFS, authentication flow, verification flow incl. purge,
  authorization matrix, ownership/IDOR, mass assignment), opt-in `ClamAvEicarIT` (`CLAMAV_IT=true`),
  Playwright (landing; applicant onboarding against the full stack with Mailpit).

## Environment facts (this laptop)
- Docker Desktop cannot start its engine until the Windows service `com.docker.service` is started once with
  elevation (`Start-Service com.docker.service; Set-Service com.docker.service -StartupType Automatic`), because its
  data-disk VHDX is attached through that service. Until then, Docker work runs inside the Ubuntu WSL distro
  (Docker 29.1.3, Compose 2.40, JDK 21, Maven 3.9.12 installed as root; unix socket only). Helper scripts live in the
  session scratchpad (`wsl-setup.sh`, `wsl-backend-verify.sh`, `wsl-compose.sh`); recreate them if the scratchpad is gone:
  they are one-liners around `mvn verify` and `docker compose` executed from `/mnt/d/Project/CyberConnect DIU`.
- Do not run a Windows-side Maven build while a WSL build is writing `backend/target` (9p write clash).
- A local PostgreSQL 18 service occupies 5432, so the dev Compose override publishes PostgreSQL on 5433.
- GitHub Actions workflows are `workflow_dispatch`-only until 1 Nov 2026 (minutes exhausted); push/PR triggers are
  commented in `.github/workflows/*.yml`.
- `gh` and the git credential manager are logged in as `faizunRBAI`, which has no push permission on
  `faizunnur/CyberConnect-DIU` (remote still empty on 30 Oct 2026). Pushing needs the owner to grant `faizunRBAI` write
  access or to store a credential for `faizunnur`. Never force-push.
- The Ubuntu WSL distro shuts down a few seconds after the last `wsl` session ends, which stops the Docker engine and
  the Compose stack. Keep a hidden session open while working:
  `Start-Process wsl.exe -ArgumentList "-d Ubuntu -u root -- sleep 86400" -WindowStyle Hidden`.

## Git
`main` holds the M1–M8 commits. `origin` is configured but the first push is blocked by permissions (see above);
retry `git push -u origin main` once access is granted.

## Next exact task (all milestones delivered; remaining items need the owner)
1. `main` is pushed to `faizunnur/CyberConnect-DIU` (7 Nov 2026). Deploy on Railway with `docs/operations/railway.md`
   or on a VPS with `docs/operations/deployment.md`. For a temporary public test link from the laptop run
   `scripts/share-dev.ps1` (`docs/operations/share-dev.md`); fill the SMTP password in `.env` first so testers get
   their verification emails.
2. Re-enable `push`/`pull_request` triggers in `.github/workflows/*.yml` and run the Security
   workflow; fix high/critical findings.
3. Go-live prerequisites in `docs/security/checklist.md` (institutional approval for document collection, restore
   drill, penetration test, legal review of privacy notice/terms), then follow `docs/operations/deployment.md`.
4. Hardening backlog: nonce-based CSP for Next.js, CV upload on internal applications (document pipeline reuse),
   admin UI for impact/opportunity statistics, Playwright coverage for moderation (TOTP in e2e).

## Known issues / decisions this session
- Spring Boot 4.1: use `spring.session.data.redis.repository-type=indexed` (old `spring.session.redis.*` keys are ignored).
- `ResponseEntityExceptionHandler` already handles `MaxUploadSizeExceededException`; override instead of re-declaring.
- Testcontainers 2.0: `org.testcontainers.postgresql.PostgreSQLContainer`, non-generic containers.
- Session revocation happens on rejection, suspension, deactivation, password change and privilege change only.
  Approval and a self-initiated resubmission (REJECTED → PENDING) keep the current session (ADR-0002); integration
  tests sign the applicant in again after each rejection.
- Surefire must include `*IT.java`; Mockito inline needs `-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading`.
- nginx `proxy_set_header` in a location disables inherited headers: each location sets `Host` explicitly.
- shadcn 4 (`base-nova`, `@base-ui/react`) ships `cn` and `shadcn` as runtime dependencies.
