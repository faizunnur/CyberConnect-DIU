# ADR-0003: nginx routing and same-origin architecture

Status: Accepted (30 Oct 2026)

## Context
The proposal's diagram placed Next.js between nginx and the API (BFF). That adds latency, a second place to get
authentication wrong, and complicates WebSockets and streamed document responses.

## Decision
nginx is the only published entry point. `/api/*` and `/ws` are proxied to Spring Boot; everything else to Next.js.
Browser and API share one origin, so no CORS configuration is needed and cookies are first-party. The frontend must
not define routes under `/api` (its health endpoint is `/healthz`). For native development only, `next dev` applies
an equivalent rewrite when `API_PROXY_TARGET` is set; production never sets it.

## Consequences
Server components that need user data forward the session cookie to the backend explicitly. Document streaming goes
nginx → backend with `proxy_buffering off`. TLS terminates at nginx in production.

## Addendum (30 Oct 2026, M6)
`/ws` is the STOMP WebSocket endpoint (`common.realtime.WebSocketConfig`), proxied by nginx with the Upgrade headers.
The handshake is authenticated by the session cookie, restricted to the application origin and to verified accounts;
the CONNECT frame carries the CSRF cookie value in `X-XSRF-TOKEN` (double submit, `WebSocketCsrfInterceptor`).
Browsers only subscribe to their own user queues; all other STOMP frames are denied (`WebSocketSecurityConfig`).
In native development the browser connects to the backend directly (`NEXT_PUBLIC_WS_URL`) because the Next dev
server does not proxy WebSockets.
