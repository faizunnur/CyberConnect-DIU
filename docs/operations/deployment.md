# Deployment guide (single VPS, Docker Compose)

Target: one Linux VPS (2 vCPU, 4 GB RAM minimum; 8 GB recommended with ClamAV), Docker Engine 27+ with the Compose
plugin, a DNS name pointing at it, and an SMTP relay. Nothing is deployed until the institution approves real
identity-document collection (`verification.real_documents_enabled` stays `false` until then).

## 1. Prepare the host
```
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git ufw
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
sudo usermod -aG docker "$USER"   # log out and in again
git clone https://github.com/faizunnur/CyberConnect-DIU.git /opt/cyberconnect && cd /opt/cyberconnect
```

## 2. Configuration
```
sh scripts/gen-secrets.sh            # writes .env with random passwords and the document encryption key
```
Then edit `.env` and set the production values:

| Variable | Value |
|---|---|
| `APP_DOMAIN` | public host name, e.g. `cyberconnect.diu.edu.bd` |
| `APP_VERSION` | git tag or short SHA being deployed (also passed to `scripts/deploy.sh`) |
| `SCANNER_TYPE` | `clamav` (the backend refuses `noop` in production) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `SMTP_AUTH`, `MAIL_FROM` | your relay |
| `E2E_FIXTURES_ENABLED` | `false` (the backend refuses `true` in production) |
| `BOOTSTRAP_SUPER_ADMIN_EMAIL` | the first administrator's email; clear it after that account registered |

Keep `.env` readable by the deploy user only (`chmod 600 .env`). The document encryption key is the only copy:
losing it makes every stored identity document unreadable (see backup-restore.md).

## 3. TLS certificates
Place `fullchain.pem` and `privkey.pem` in `infrastructure/nginx/certs/` (Let's Encrypt via certbot on the host:
`certbot certonly --webroot -w /var/www/certbot -d $APP_DOMAIN`, then copy or symlink the files and reload nginx with
`docker compose ... exec nginx nginx -s reload` after each renewal).

## 4. Buckets
Production does not auto-create buckets by default (`STORAGE_ENSURE_BUCKETS=false`). Create `verification-documents`
and `member-media` once with the S3 API and the access keys (both private), or set `STORAGE_ENSURE_BUCKETS=true` for
the first backend start only and switch it back afterwards. Never expose port 9000/9001 publicly.

## 5. First rollout
```
sh scripts/deploy.sh v1.0.0
```
The script builds both images, takes a backup, starts the stack with the production overrides and waits for health.
Flyway runs the migrations as `cyberconnect_migrator` on backend start; the application connects as
`cyberconnect_app` (least privilege). Verify:
- `https://$APP_DOMAIN/healthz` → 200, `https://$APP_DOMAIN/api/v1/health` → 200
- `curl http://127.0.0.1:8081/actuator/health` from the host network (management port is internal only)
- register the bootstrap administrator, verify the email, enrol MFA, then clear `BOOTSTRAP_SUPER_ADMIN_EMAIL`

## 6. Start-up guards (fail fast)
The backend refuses to start in production when: `SCANNER_TYPE=noop`, `E2E_FIXTURES_ENABLED=true`, the base URL is
not https, the session cookie is not Secure, API docs are enabled, or SMTP points at mailpit/localhost
(`ScannerProductionGuard`, `E2eFixturesGuard`, `ProductionConfigGuard`).

## 7. Updates
```
git fetch && git checkout v1.1.0
sh scripts/deploy.sh v1.1.0
```
Rollback: `APP_VERSION=v1.0.0 docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full --profile scanning up -d`
(migrations are forward-only; restore the database from the pre-deploy backup if a migration must be undone).

## 8. Railway alternative
`docs/operations/railway.md` deploys the same topology on Railway (one service per container, nginx edge as the only
public service, managed PostgreSQL/Redis).

## 9. Not in scope
Kubernetes, Minikube, Kafka, Elasticsearch and multi-host setups are deliberately out of scope (ADR-0001).
