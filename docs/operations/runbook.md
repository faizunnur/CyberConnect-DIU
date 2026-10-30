# Runbook

Commands assume `/opt/cyberconnect` and `C="docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full --profile scanning"`.

## Daily checks
- `$C ps` — every service `healthy`.
- `curl -s http://127.0.0.1:8081/actuator/health | jq` — `UP` with db, redis, diskSpace components.
- Disk: `df -h /var/lib/docker`; prune old images with `docker image prune -f` after a successful deploy.
- Backups present in `./backups` (nightly cron: `0 2 * * * cd /opt/cyberconnect && sh scripts/backup.sh >> backups/backup.log 2>&1`).

## Common incidents
| Symptom | Check | Action |
|---|---|---|
| 502 from nginx | `$C logs backend --tail 200` | Backend restarting: look for a start-up guard message (misconfiguration) or Flyway failure (schema). Fix `.env` or restore DB; `$C up -d backend`. |
| Members cannot sign in, `RATE_LIMITED` | nginx `limit_req` log lines, Redis reachable | Rate limits are per address; a NAT campus network may need a higher `limit_req` rate in `prod.conf`. Never disable the Redis limits. |
| Uploads fail with `SCAN_UNAVAILABLE` | `$C ps clamav`, `$C logs clamav` | ClamAV needs ~2 GB RAM and 5 minutes to load signatures after a restart; the backend fails closed by design. |
| Emails not delivered | `$C logs backend \| grep -i mail` | Check relay credentials (`SMTP_*`); notifications are stored in-app regardless and delivery is retried on the next event only, not queued. |
| WebSocket keeps reconnecting | browser console, nginx `/ws` logs | Confirm `APP_BASE_URL` matches the page origin (origin check) and the `XSRF-TOKEN` cookie is present (CSRF on CONNECT). REST polling keeps the UI working meanwhile. |
| Suspected account compromise | admin → Members → suspend | Suspension revokes all sessions immediately (Redis-backed sessions). Audit history shows who did what. |
| Lost `.env` | — | Database and storage are still consistent, but identity documents cannot be decrypted without `DOCUMENT_ENCRYPTION_KEY`. Restore `.env` from the secret store. |

## Operational tasks
- **Promote a moderator/admin**: admin → Members → roles (requires step-up MFA; the last SUPER_ADMIN cannot be removed).
- **Reopen a locked verification**: admin → Members → "Reopen verification" (audited).
- **Delete a member on request**: deactivate (restorable for 30 days, D-16); permanent erasure is a manual SQL
  procedure (documents are purged by the retention job first) and must be recorded in the audit log.
- **Rotate the document encryption key**: generate a new key, set `DOCUMENT_ENCRYPTION_KEY_ID=k2`, keep the old key
  available for decryption until all documents created under `k1` are purged by retention (30 days after decisions).
- **Scheduled jobs** (inside the backend, no cron needed): document retention purge 03:30 UTC, notification purge
  03:45 UTC, opportunity expiry 00:15 UTC, suspension lifting every 15 minutes.

## Logs
Backend logs are ECS-JSON in production (`logging.structured.format.console=ecs`) with `requestId` and never contain
passwords, tokens, document bytes or raw PII; problem responses carry the same `errorId`/request id for correlation.
`docker compose logs -f backend | jq -r 'select(.["log.level"]=="ERROR")'` filters errors.
