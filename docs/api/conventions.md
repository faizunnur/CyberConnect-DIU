# API conventions

- Base path `/api/v1`; JSON; UUID identifiers; ISO 8601 UTC timestamps; `page`/`size` pagination with
  `{ content, page, size, totalElements, totalPages }` responses (keyset pagination for feeds and messages).
- Authentication: session cookie `CCSESSION`. Bootstrap CSRF with `GET /api/v1/auth/csrf` (sets `XSRF-TOKEN`);
  send `X-XSRF-TOKEN` on every non-GET request.
- Errors: RFC 9457 `application/problem+json` with a stable `code` property, e.g.
  `{"type":"about:blank","title":"Forbidden","status":403,"detail":"…","instance":"/api/v1/members","code":"ACCOUNT_NOT_VERIFIED"}`.
  Validation errors add `errors: [{field, message}]`. Unexpected errors add `errorId` for log correlation.
- Public endpoints: `/api/v1/health`, `/api/v1/auth/csrf`, `/api/v1/public/**`, `/api/v1/docs/**` (non-prod),
  `/actuator/health/**` (internal network only). Everything else requires an authenticated session, and community
  endpoints additionally require account status VERIFIED.
- OpenAPI: `GET /api/v1/docs/openapi` (JSON) and Swagger UI at `/api/v1/docs` in local/docker profiles only.
- Never return JPA entities; request DTOs never accept roles, statuses, counters, reviewer ids or ownership fields.
