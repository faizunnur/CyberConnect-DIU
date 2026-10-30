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
