# API conventions

- Base path `/api/v1`; JSON; UUID identifiers; ISO 8601 UTC timestamps.
- Pagination: `page`/`size` query parameters; responses use Spring Data's `via_dto` shape
  `{ content: [...], page: { size, number, totalElements, totalPages } }`.
- Authentication: session cookie `CCSESSION` (HttpOnly, SameSite=Lax, Secure in prod). Bootstrap CSRF with
  `GET /api/v1/auth/csrf` (sets readable `XSRF-TOKEN`); send `X-XSRF-TOKEN` on every non-GET request.
- Errors: RFC 9457 `application/problem+json` with a stable `code`, e.g.
  `{"type":"about:blank","title":"Forbidden","status":403,"detail":"…","instance":"/api/v1/members","code":"ACCOUNT_NOT_VERIFIED"}`.
  Validation errors add `errors: [{field, message}]`; unexpected errors add `errorId`.
- OpenAPI: `GET /api/v1/docs/openapi` and Swagger UI at `/api/v1/docs` in local/docker profiles only.
- Never return JPA entities; request DTOs never accept roles, statuses, counters, reviewer ids or ownership fields.

## Access tiers (enforced by `AccountAccessFilter` + method security)
| Tier | Who | Paths |
|---|---|---|
| Public | anyone | `/auth/csrf`, `/auth/register`, `/auth/login`, `/auth/login/mfa`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password`, `/health`, `/public/**` |
| Onboarding | any authenticated account, any status | `/auth/**`, `/mfa/**`, `/verification/**`, `/me/affiliations` (GET; POST needs VERIFIED), `/account/**` |
| Community | status VERIFIED + email verified | everything else |
| Privileged | VERIFIED + role authority + MFA enrolled | `/moderation/**` (VERIFICATION_REVIEW / APPEAL_DECIDE / MODERATION_ACTION), `/admin/**` (ROLE_ASSIGN / SETTINGS_MANAGE / AUDIT_READ / MEMBER_MANAGE) |

Step-up: approve/reject/decide, document viewing, role changes, suspensions, settings changes require
`POST /api/v1/mfa/step-up` within the last 10 minutes (else `403 MFA_STEP_UP_REQUIRED`).

## Problem codes (M2)
`AUTHENTICATION_REQUIRED`, `CSRF_TOKEN_INVALID`, `ACCESS_DENIED`, `ACCOUNT_NOT_VERIFIED`, `ACCOUNT_REJECTED`,
`ACCOUNT_SUSPENDED`, `ACCOUNT_DEACTIVATED`, `EMAIL_NOT_VERIFIED`, `MFA_ENROLLMENT_REQUIRED`, `MFA_STEP_UP_REQUIRED`,
`MFA_CODE_INVALID`, `MFA_SESSION_MISSING`, `INVALID_CREDENTIALS`, `RATE_LIMITED`, `TOKEN_INVALID`, `TOKEN_EXPIRED`,
`PASSWORD_TOO_SHORT/LONG/WEAK`, `VALIDATION_FAILED`, `NOT_OWNER`, `SELF_REVIEW`, `SAME_REVIEWER`, `SELF_ROLE_CHANGE`,
`LAST_SUPER_ADMIN`, `SUPER_ADMIN_REQUIRED`, `VERIFICATION_LOCKED`, `MAX_ATTEMPTS_REACHED`, `RESUBMISSION_NOT_ALLOWED`,
`APPEAL_EXISTS`, `APPEAL_PENDING`, `DOCUMENT_COLLECTION_DISABLED`, `UNSUPPORTED_FILE_TYPE`, `UPLOAD_TOO_LARGE`,
`IMAGE_TOO_LARGE`, `PDF_ACTIVE_CONTENT`, `DOCUMENT_REJECTED_MALWARE`, `SCAN_UNAVAILABLE`, `MAX_FILES_REACHED`,
`SUBMISSION_NOT_EDITABLE`, `NO_DOCUMENTS`.

## Problem codes (M3)
`MEMBER_NOT_FOUND` (uniform: unknown, non-verified or blocked either way), `ENTRY_NOT_FOUND`, `PHOTO_NOT_FOUND`,
`SELF_CONNECTION`, `SELF_BLOCK`, `ALREADY_CONNECTED`, `REQUEST_PENDING`, `REQUEST_NOT_FOUND`, `NOT_CONNECTED`,
`TOO_MANY_SKILLS`, `SKILL_TOO_LONG`, `TOO_MANY_ENTRIES`, `VALUE_TOO_LONG`, `END_DATE_REQUIRED`, `INVALID_DATE_RANGE`,
`PHOTO_REJECTED_MALWARE`, `FILE_EMPTY`, `IMAGE_UNREADABLE`.

## Problem codes (M4)
`POST_NOT_FOUND` (uniform: unknown, deleted, removed, blocked author or not visible), `COMMENT_NOT_FOUND`,
`BODY_REQUIRED`, `BODY_TOO_LONG`, `POST_NOT_EDITABLE`, `POST_NOT_ACTIVE`, `COMMENT_NOT_EDITABLE`, `INVALID_PARENT`,
`REPOST_NOT_ALLOWED`, `SELF_REPORT`, `ALREADY_REPORTED`, `UNSUPPORTED_TARGET`, `REPORT_NOT_FOUND`, `REPORT_CLOSED`,
`NOT_CONTENT`, `POST_DELETED`, `COMMENT_DELETED`.

## Problem codes (M5)
`OPPORTUNITY_NOT_FOUND`, `OPPORTUNITY_NOT_EDITABLE`, `OPPORTUNITY_NOT_PUBLISHABLE`, `OPPORTUNITY_NOT_OPEN`,
`OPPORTUNITY_NOT_DRAFT`, `APPLICATION_URL_REQUIRED`, `APPLICATION_EMAIL_REQUIRED`, `DEADLINE_PASSED`, `OWN_OPPORTUNITY`,
`EXTERNAL_APPLICATION`, `ALREADY_APPLIED`, `APPLICATION_NOT_FOUND`, `INVALID_DECISION`, `PROJECT_NOT_FOUND`,
`PROJECT_REMOVED`, `ILLEGAL_STATUS`, `ALREADY_MEMBER`, `NOT_RECRUITING`, `NOT_MEMBER`, `LEAD_CANNOT_LEAVE`,
`SELF_MENTORSHIP`, `MENTOR_UNAVAILABLE`, `ALREADY_MENTORING`, `REQUEST_NOT_PENDING`, `NOT_ACTIVE`.

## Problem codes (M6)
`CONVERSATION_NOT_FOUND` (uniform: unknown, not a member, blocked), `MESSAGE_NOT_FOUND`, `SELF_MESSAGE`,
`MESSAGING_NOT_ALLOWED`, `REQUEST_PENDING` (one message until the request is accepted), `REQUEST_DECLINED`,
`NOT_A_REQUEST`, `NOTIFICATION_NOT_FOUND`.

## Problem codes (M7)
`IMPACT_NOT_FOUND`, `SOURCE_NOT_FOUND` (uniform: unknown source or not a party), `SOURCE_NOT_ELIGIBLE`,
`COUNTERPART_REQUIRED`, `IMPACT_EXISTS`, `NOT_AWAITING_YOU`, `NOT_WITHDRAWABLE`, `NOT_DISPUTED`.

## Endpoint map (M2)
- Auth: `POST /auth/register` (202 always), `POST /auth/login` → `{mfaRequired, user}`, `POST /auth/login/mfa`,
  `POST /auth/logout`, `GET /auth/me`, `POST /auth/verify-email`, `POST /auth/resend-verification`,
  `POST /auth/forgot-password` (202 always), `POST /auth/reset-password`, `POST /auth/change-password`.
- MFA: `POST /mfa/enrol`, `POST /mfa/enrol/confirm` → recovery codes, `POST /mfa/step-up`,
  `POST /mfa/recovery-codes/regenerate`, `POST /mfa/disable` (not for privileged roles).
- Affiliations: `GET/POST /me/affiliations`, `PUT /me/affiliations/{id}`.
- Verification (applicant): `GET /verification/status`, `POST /verification/submissions`,
  `POST /verification/submissions/{id}/documents` (multipart `file`, `documentType`),
  `DELETE …/documents/{docId}`, `POST …/{id}/submit`, `POST /verification/appeals`, `GET /verification/appeals/{id}`.
- Moderation: `GET /moderation/verifications` (status, assignee, page, size), `GET …/reason-codes`, `GET …/{id}`,
  `POST …/{id}/assign|approve|reject`, `GET …/{id}/documents/{docId}/content` (inline stream, no-store),
  `GET /moderation/appeals`, `GET …/{id}`, `POST …/{id}/assign|decide`,
  `POST /moderation/members/{id}/suspend|unsuspend`, `GET /moderation/members/{id}/actions`.
- Admin: `GET /admin/members`, `GET …/{id}`, `PUT …/{id}/roles`, `POST …/{id}/deactivate|reactivate|reopen-verification`,
  `GET/PUT /admin/settings[/{key}]`, `GET /admin/audit`.

## Endpoint map (M3, verified members only)
- Own profile: `GET/PUT /me/profile`, `GET/PUT /me/profile/privacy`, `PUT /me/profile/skills` (`{skills: [names]}`),
  `POST /me/profile/experiences`, `PUT/DELETE …/{id}`, `POST /me/profile/educations`, `PUT/DELETE …/{id}`,
  `POST /me/profile/photo` (multipart `file`, JPEG/PNG ≤ 2 MiB → 512 px square JPEG), `DELETE /me/profile/photo`.
- Members: `GET /members` (q, affiliation, skill, organization, experience, mentorAvailable, page, size ≤ 50;
  excludes self, unsearchable and blocked members; privacy applied per viewer), `GET /members/{id}` (privacy-aware;
  `restricted=true` for CONNECTIONS_ONLY profiles seen by non-connections; `email` only when allowed),
  `GET /members/{id}/photo` (private, 10 min cache). `GET /skills?q=` suggestions.
- Connections: `GET /connections`, `GET /connections/summary`, `GET /connections/requests?direction=incoming|outgoing`,
  `POST /connections/requests` (`{userId, message?}`; a pending request in the other direction is accepted instead;
  30/h), `POST …/{id}/accept|decline`, `DELETE …/{id}` (cancel), `DELETE /connections/{userId}` (remove).
- Blocks: `GET /blocks`, `POST /blocks` (`{userId}`; removes any connection/request), `DELETE /blocks/{userId}`.

## Endpoint map (M4, verified members only)
- Feed: `GET /feed` (intent, tag, q, author, page, size ≤ 50; newest first; only posts whose author is verified and not
  blocked either way; CONNECTIONS_ONLY posts need an accepted connection), `GET /bookmarks`, `GET /hashtags/trending`.
- Posts: `POST /posts` (`{intent, body, visibility?, repostOfId?}`; plain text, hashtags extracted; 20/h),
  `GET /posts/{id}` (author sees own REMOVED/DELETED posts with the reason), `PUT /posts/{id}`, `DELETE /posts/{id}`
  (soft), `POST/DELETE /posts/{id}/like`, `POST/DELETE /posts/{id}/bookmark`, `GET /posts/{id}/comments` (top-level
  with nested replies; deleted parents stay as placeholders while replies exist), `POST /posts/{id}/comments`
  (`{body, parentId?}`; one level; 60/h).
- Comments: `PUT /comments/{id}`, `DELETE /comments/{id}` (own only).
- Reports: `POST /reports` (`{targetType: POST|COMMENT|USER, targetId, reason, details?}` → `{id}`; one open report
  per reporter and target; 20/h).
- Moderation (MODERATION_ACTION, MFA): `GET /moderation/reports` (status, page, size), `GET …/summary`, `GET …/{id}`
  (snapshot + current state), `POST …/{id}/resolve` (`{resolution: CONTENT_REMOVED|NO_ACTION|DISMISSED, note?}`;
  removal also settles other open reports on the same item), `GET /moderation/posts/{id}`,
  `POST /moderation/posts/{id}/remove|restore`, `POST /moderation/comments/{id}/remove|restore` (audited).

## Endpoint map (M5, verified members only)
- Opportunities: `GET /opportunities` (q, type, locationType, skill, author; OPEN, deadline not past, author verified
  and not blocked), `GET /opportunities/mine`, `GET /opportunities/applications` (own), `POST /opportunities`
  (`{type, title, organization, locationType, location?, description, compensation?, deadline?, applicationMethod,
  applicationUrl?, applicationEmail?, skills[], publish}`; 10/day), `GET/PUT /opportunities/{id}`,
  `DELETE /opportunities/{id}` (drafts only), `POST …/publish|close`, `POST …/apply` (`{coverNote?}`, INTERNAL only,
  30/day), `DELETE …/apply` (withdraw), `GET …/applicants` (author), `POST …/applicants/{applicationId}/decide`
  (`{status: REVIEWED|SHORTLISTED|ACCEPTED|REJECTED}`). Moderation: `GET/POST /moderation/opportunities/{id}[/remove|/restore]`.
- Research: `GET /research` (q, status, skill, lookingForMembers, mine), `GET /research/applications`,
  `POST /research` (`{title, summary, lookingForMembers, startDate?, endDate?, skills[]}`), `GET/PUT /research/{id}`,
  `POST …/status` (`{status}`), `POST …/apply` (`{message?}`), `DELETE …/apply`, `POST …/applications/{id}/accept|decline`
  (lead), `DELETE …/members/{userId}` (lead), `POST …/leave`. Moderation: `GET/POST /moderation/research/{id}[/remove|/restore]`.
- Mentorship: `GET /mentorship/requests?role=mentee|mentor&status`, `GET …/summary`, `POST /mentorship/requests`
  (`{mentorId, topic, message}`; mentor must advertise availability; 10/day), `GET …/{id}`, `POST …/{id}/accept|decline`
  (`{note?}`, mentor), `POST …/{id}/complete` (either party), `DELETE …/{id}` (mentee, pending only).
- Dev only (`E2E_FIXTURES_ENABLED=true`, never in production): `POST /dev/fixtures/verified-member`
  (`{prefix?, affiliationType?, fullName?}` → `{userId, email, password}`) for Playwright.

## Endpoint map (M6, verified members only)
- Conversations: `GET /conversations?box=inbox|requests|archived`, `GET /conversations/summary` (`{unread}` conversations
  with unread messages), `POST /conversations` (`{userId, body}`; reuses the pair's conversation; the recipient's
  messaging policy decides ACTIVE vs REQUEST, NOBODY → 403), `GET /conversations/{id}`,
  `GET /conversations/{id}/messages?before=<messageId>&size` (newest first, keyset), `POST …/messages` (`{body}`),
  `POST …/read`, `POST …/accept|decline|archive|unarchive`, `DELETE /messages/{id}` (own, soft).
  Reports: `POST /reports` with `targetType: MESSAGE` (reporter must be the other member; snapshot only, D-14);
  moderators resolve with CONTENT_REMOVED without ever reading the thread.
- Notifications: `GET /notifications?unread=true|false`, `GET /notifications/summary` (`{unread}`),
  `POST /notifications/{id}/read`, `POST /notifications/read-all`, `GET/PUT /notifications/preferences`
  (`[{type, inApp, email}]`; VERIFICATION_DECISION, CONTENT_REMOVED and ACCOUNT_NOTICE cannot be silenced in-app).
- Realtime: STOMP over WebSocket at `/ws` (same origin through nginx). Handshake needs the session cookie, a
  verified account and an allowed Origin; the CONNECT frame must carry `X-XSRF-TOKEN` equal to the `XSRF-TOKEN`
  cookie. Clients may only SUBSCRIBE to `/user/queue/notifications` and `/user/queue/messages`; every other frame
  is denied. Payloads: notification `{id, type, title, body, link}`, message = the `MessageView` of the new message.

## Endpoint map (M7, verified members only)
- Impact: `GET /impact?status` (records where the caller is beneficiary or contributor), `GET /impact/sources`
  (eligible on-platform sources with the counterpart resolved), `GET /impact/stats` (community aggregate, CONFIRMED
  only), `POST /impact` (`{type, sourceId, counterpartId?, title, description?}`; the proposer's side is confirmed),
  `GET /impact/{id}` (parties only), `POST /impact/{id}/confirm|dispute|withdraw` (`dispute` takes `{reason}`),
  `GET /members/{id}/impact` (confirmed totals and recent records, subject to profile visibility).
- Moderation (MODERATION_ACTION, MFA): `GET /moderation/impact/disputes`, `GET /moderation/impact/{id}`,
  `POST /moderation/impact/{id}/resolve` (`{confirmed, note?}`; audited, both parties notified).
