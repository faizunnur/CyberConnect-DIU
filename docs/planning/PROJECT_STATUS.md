# Project status

Updated: 30 Oct 2026 (session 1, autonomous mode). Read `CLAUDE.md` first.

## Milestones
| Milestone | State |
|---|---|
| M1 Foundation | Done. Commit `16c3229 chore: initialize project foundation`. |
| M2 Identity & verification | Done and verified on 30 Oct 2026: `mvn verify` 49 tests green (1 opt-in ClamAV IT skipped), frontend lint/typecheck/build green, Playwright 12/12 against the nginx stack with the rebuilt backend image. Committed as `feat(identity,verification): …` (hash in `git log`). |
| M3 Profiles & networking | Done and verified on 30 Oct 2026: `mvn verify` 56 tests green (1 opt-in skip), frontend lint/typecheck/build green, Playwright 12/12 against the rebuilt stack (V003 applied). Committed as `feat(profile,networking): …`. |
| M4 Community | Next. |
| M5–M8 | Not started. |

## What exists (M1 + M2 + M3)
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
`main` holds the M1, M2 and M3 commits. `origin` is configured but the first push is blocked by permissions (see above);
retry `git push -u origin main` once access is granted.

## Next exact task
1. M4 Community: migration V004 (posts with intent + visibility, comments with one-level replies, likes, reposts
   with commentary, bookmarks, hashtags/post_hashtags, reports), `community` module (plain-text sanitisation, feed
   with pagination, ownership edit/delete, report → moderation queue, moderator remove/restore), frontend feed
   (`/home` becomes the feed, composer, post page, bookmarks `/saved`, hashtag pages) and moderation reports UI;
   ITs for sanitisation, ownership, blocking in feed, moderation. Commit at the end.
2. M5–M8 per `docs/planning/EXECUTION_PLAN.md`.

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
