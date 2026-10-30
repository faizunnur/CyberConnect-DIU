# Monitoring and logging

## Health
- Liveness/readiness: `GET /actuator/health/liveness` and `/readiness` on the management port (8081 in production,
  internal only; 8080 in development). Compose health checks use readiness.
- Edge: `GET /healthz` (frontend) and `GET /api/v1/health` (backend) through nginx — use them for external uptime
  monitoring (UptimeRobot, Healthchecks.io, …).

## Metrics
`GET http://127.0.0.1:8081/actuator/prometheus` exposes JVM, HTTP server (`http_server_requests_seconds`), HikariCP,
Redis/Lettuce, Tomcat threads and logback counters. Minimal host-side scrape with Prometheus:
```yaml
scrape_configs:
  - job_name: cyberconnect-backend
    static_configs: [{ targets: ["127.0.0.1:8081"] }]
    metrics_path: /actuator/prometheus
```
Alerts worth having: `up == 0`, readiness != UP for 2 minutes, p95 of `http_server_requests_seconds` > 1.5 s for
10 minutes, 5xx rate > 1 %, HikariCP pending connections > 0 for 5 minutes, disk < 15 % free.

## Logs
- Backend: ECS JSON on stdout (Docker `json-file` driver, 10 MB × 3 per container). Fields include
  `log.level`, `message`, `requestId` (from `RequestIdFilter`, echoed as `X-Request-Id`), `error.*`.
- nginx: access/error logs on stdout with client address; rate-limit rejections appear as `limiting requests`.
- Audit trail: `audit_logs` table (append-only) — the source of truth for privileged actions; query it through
  admin → Audit history rather than from logs.
- Ship logs with the Docker log driver of your choice (e.g. `loki` plugin) if central retention is required; redact
  nothing further — the application never logs secrets, tokens, document bytes or raw PII.

## Capacity (measured in development, i5-12450HX)
Backend ~600 MB RSS idle (limit 1.5 GB in production), frontend ~150 MB, PostgreSQL ~100 MB, Redis ~20 MB,
ClamAV ~1.3 GB after signatures load. A 4 GB VPS runs the stack with ClamAV; 2 GB does not.
