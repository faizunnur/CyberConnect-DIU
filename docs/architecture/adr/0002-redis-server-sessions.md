# ADR-0002: Redis-backed server-side sessions

Status: Accepted (30 Oct 2026)

## Context
The proposal allowed either cookie sessions or JWT access/refresh tokens. Suspension, rejection, deactivation and
privilege changes must revoke access immediately; JWTs cannot be revoked without a denylist that recreates server
state anyway.

## Decision
Spring Session Data Redis with an opaque session id in the `CCSESSION` cookie (HttpOnly, SameSite=Lax, Secure in
production), 4-hour inactivity timeout, session fixation protection (new session on login). CSRF protection with the
`XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header (SPA pattern). `/auth/refresh` and `refresh_tokens` are dropped.
WebSocket handshakes reuse the session cookie. All of a user's sessions are deleted on rejection, suspension,
deactivation, password change and privilege changes; approval and a self-initiated resubmission keep the current session.
Spring Boot 4 does not apply `server.servlet.session.cookie.*` to Spring Session, so `SessionCookieConfig` declares the
`DefaultCookieSerializer` explicitly (name, path, HttpOnly, Secure, SameSite, base64 ids); the indexed Redis repository is
selected with `spring.session.data.redis.repository-type=indexed` so sessions can be found by principal and revoked.

## Consequences
Instant revocation and simple clients. Redis becomes a required runtime dependency (already needed for rate limits).
Horizontal scaling of the backend remains possible because session state is external.
