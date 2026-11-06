# Railway deployment guide

Railway runs the same topology as the VPS (ADR-0009) with one Railway service per container. Only the nginx edge gets a
public domain; PostgreSQL, Redis, object storage, ClamAV, the backend and the frontend stay on Railway's private
network (`<service>.railway.internal`). Session cookies, CSRF and the WebSocket origin check all rely on that single
origin, so do **not** give the backend or the frontend a public domain.

```
browser ──https──> Railway edge ──> edge (nginx, public)
                                      ├── /api/*, /ws ──> backend.railway.internal:8080 (Spring Boot)
                                      └── /*          ──> frontend.railway.internal:3000 (Next.js)
backend ──> Postgres, Redis (Railway managed) · storage (RustFS) · clamav
```

The start-up guards still apply: the backend refuses to start in production without `SCANNER_TYPE=clamav`, an https
base URL, a Secure cookie, API docs off, a real SMTP host and `E2E_FIXTURES_ENABLED=false`.

## 0. Before you start
- A Railway account on the **Hobby plan or higher**. ClamAV alone needs about 3 GB of RAM, which is more than the
  free tier allows; expect ClamAV to be the largest line on the bill.
- The GitHub repository connected to Railway (Railway → New → GitHub repo → authorise `faizunnur/CyberConnect-DIU`).
- An SMTP relay with credentials (Resend, Brevo, Amazon SES, Mailgun, …). Mailpit is development-only.
- Secrets generated locally once, then pasted into Railway. On the laptop:
  ```
  sh scripts/gen-secrets.sh      # or: powershell scripts/gen-secrets.ps1
  ```
  The generated `.env` is only a source of random values here; copy `DB_APP_PASSWORD`, `REDIS_PASSWORD`,
  `STORAGE_SECRET_KEY` and `DOCUMENT_ENCRYPTION_KEY` from it. Keep a copy of `DOCUMENT_ENCRYPTION_KEY` in a password
  manager: it is the only key that can decrypt stored identity documents.
- Optional: the Railway CLI (`npm i -g @railway/cli`, `railway login`, `railway link`) for `railway connect Postgres`
  and `railway logs`.

Create one project (for example `cyberconnect-diu`) and work in its `production` environment. Use the service names
below exactly; the private hostnames and the `${{service.VAR}}` references depend on them.

## 1. Postgres and Redis (Railway managed)
1. Project canvas → **Create** → **Database** → **PostgreSQL**. Keep the service name `Postgres`.
2. **Create** → **Database** → **Redis**. Keep the service name `Redis`.
3. Leave both private (no TCP proxy). They expose `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` and
   `REDISHOST`, `REDISPORT`, `REDISPASSWORD`, which the backend references.

Create the least-privilege runtime role once (the managed instance only has the `postgres` superuser, which the
migrations use as the owner). Open the Postgres service → **Data** → query editor, or `railway connect Postgres`, and
run with your own password:
```sql
CREATE ROLE cyberconnect_app LOGIN PASSWORD 'paste-DB_APP_PASSWORD-here';
GRANT CONNECT ON DATABASE railway TO cyberconnect_app;
GRANT USAGE ON SCHEMA public TO cyberconnect_app;
```
Flyway grants the table privileges to that role during the first backend start (`${app_role}` placeholder).

## 2. storage (RustFS, S3 API)
The application uses two private buckets through one key pair with path-style addressing, which is why it runs its own
S3 server instead of Railway Buckets (per-bucket credentials, virtual-hosted URLs, public network only).

1. **Create** → **Docker Image** → `rustfs/rustfs:1.0.4`. Name the service `storage`.
2. **Settings → Volumes**: add a volume mounted at `/data` (the objects live there; size it for documents + photos).
3. **Variables**:

| Variable | Value |
|---|---|
| `RUSTFS_ACCESS_KEY` | `cyberconnect` |
| `RUSTFS_SECRET_KEY` | the generated `STORAGE_SECRET_KEY` (mark it *sealed*) |
| `RUSTFS_VOLUMES` | `/data` |
| `RUSTFS_ADDRESS` | `:9000` |
| `RUSTFS_CONSOLE_ENABLE` | `false` |

No public domain, no TCP proxy. The buckets are created by the backend on its first start (step 5).

## 3. clamav
1. **Create** → **Docker Image** → `clamav/clamav:1.4.6`. Name the service `clamav`.
2. **Settings → Volumes**: mount a volume at `/var/lib/clamav` so the signature database survives redeploys.
3. **Variables**: `CLAMAV_NO_MILTERD=true`.
4. The first start downloads the signatures and takes several minutes; the backend's scanner health check waits for
   port 3310. Expect about 2.5–3 GB of resident memory.

## 4. backend
1. **Create** → **GitHub Repo** → `faizunnur/CyberConnect-DIU`, branch `main`. Name the service `backend`.
2. **Settings → Source**: Root Directory `/backend`. Railway builds `backend/Dockerfile` automatically.
3. **Settings → Deploy**: Healthcheck Path `/api/v1/health`, Healthcheck Timeout `300` (Flyway runs on first start).
   Restart policy *On failure*.
4. **Settings → Networking**: no public domain.
5. **Variables** (paste through the RAW editor; replace the angle-bracket values):

```
SPRING_PROFILES_ACTIVE=prod
APP_ENV=prod
PORT=8080
SERVER_PORT=8080
MANAGEMENT_PORT=8081
APP_BASE_URL=https://<edge public domain, filled in after step 6>
SESSION_COOKIE_SECURE=true
API_DOCS_ENABLED=false
E2E_FIXTURES_ENABLED=false

DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_MIGRATOR_USER=${{Postgres.PGUSER}}
DB_MIGRATOR_PASSWORD=${{Postgres.PGPASSWORD}}
DB_APP_USER=cyberconnect_app
DB_APP_PASSWORD=<the password used in the CREATE ROLE statement>

REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}

STORAGE_ENDPOINT=http://${{storage.RAILWAY_PRIVATE_DOMAIN}}:9000
STORAGE_ACCESS_KEY=${{storage.RUSTFS_ACCESS_KEY}}
STORAGE_SECRET_KEY=${{storage.RUSTFS_SECRET_KEY}}
STORAGE_ENSURE_BUCKETS=true

DOCUMENT_ENCRYPTION_KEY=<base64 of 32 random bytes from gen-secrets>
DOCUMENT_ENCRYPTION_KEY_ID=k1

SCANNER_TYPE=clamav
CLAMAV_HOST=${{clamav.RAILWAY_PRIVATE_DOMAIN}}
CLAMAV_PORT=3310

SMTP_HOST=<smtp.yourrelay.example>
SMTP_PORT=587
SMTP_USERNAME=<relay user>
SMTP_PASSWORD=<relay password>
SMTP_STARTTLS=true
SMTP_AUTH=true
MAIL_FROM=no-reply@<your domain>

BOOTSTRAP_SUPER_ADMIN_EMAIL=<first administrator's email>

JAVA_TOOL_OPTIONS=-Xmx1g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom
SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES=10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|::1|[fF][cCdD][0-9a-fA-F]{2}:.*|[fF][eE]80:.*
```

Mark `DB_APP_PASSWORD`, `DOCUMENT_ENCRYPTION_KEY` and `SMTP_PASSWORD` as sealed. Notes:
- `STORAGE_ENSURE_BUCKETS=true` is for the **first start only**: the backend creates the private
  `verification-documents` and `member-media` buckets on the RustFS service. Set it to `false` afterwards.
- `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` lets Tomcat trust the edge's private address so `X-Forwarded-For` becomes
  the client IP. Without it every user shares one rate-limit bucket and the audit trail records the edge address.
- `BOOTSTRAP_SUPER_ADMIN_EMAIL`: clear it after that account has registered and enrolled MFA.
- Memory: give the service at least 1.5 GB (Settings → Resources, or rely on the plan limit).

## 5. frontend
1. **Create** → **GitHub Repo** → same repo, branch `main`. Name the service `frontend`.
2. **Settings → Source**: Root Directory `/frontend` (builds `frontend/Dockerfile`).
3. **Settings → Deploy**: Healthcheck Path `/healthz`.
4. No public domain.
5. **Variables**:

| Variable | Value | Why |
|---|---|---|
| `PORT` | `3000` | matches the edge upstream |
| `HOSTNAME` | `::` | listen on IPv6 as well; Railway's private network is IPv6-first |
| `NODE_ENV` | `production` | |

Do not set `NEXT_PUBLIC_WS_URL` or `API_PROXY_TARGET`: in production the browser uses the same-origin `/api` and `/ws`.

## 6. edge (nginx, the public service)
1. **Create** → **GitHub Repo** → same repo. Name the service `edge`.
2. **Settings → Source**: Root Directory `/infrastructure/nginx/railway` (builds the small nginx image there; it
   renders `default.conf.template` at start-up and resolves the private hostnames per request).
3. **Settings → Networking → Public Networking → Generate Domain**, target port `8080`. Later add your custom domain
   (`+ Custom Domain`, then the CNAME/TXT records Railway shows).
4. **Settings → Deploy**: Healthcheck Path `/healthz`.
5. **Variables**:

| Variable | Value |
|---|---|
| `PORT` | `8080` |
| `BACKEND_HOST` | `${{backend.RAILWAY_PRIVATE_DOMAIN}}` |
| `FRONTEND_HOST` | `${{frontend.RAILWAY_PRIVATE_DOMAIN}}` |

6. Copy the edge domain into the backend: `APP_BASE_URL=https://<edge domain>` (or
   `https://${{edge.RAILWAY_PUBLIC_DOMAIN}}` while you use the Railway-generated domain). The backend only accepts
   WebSocket connections from that origin and uses it in every email link.

## 7. First rollout and verification
Deploy in this order and wait for each to be green: Postgres, Redis, storage, clamav (signatures downloaded), backend,
frontend, edge. Then:
1. `https://<edge domain>/healthz` → 200 and `https://<edge domain>/api/v1/health` → 200;
   `https://<edge domain>/actuator/health` → 404 (management stays private).
2. Backend logs show Flyway at `V007` and the two buckets created. Set `STORAGE_ENSURE_BUCKETS=false` and redeploy.
3. Register the bootstrap administrator, confirm the email (so the SMTP relay works), sign in, enrol MFA, then clear
   `BOOTSTRAP_SUPER_ADMIN_EMAIL` and redeploy the backend.
4. Register a normal account, upload a **fictional** test document, verify it as the moderator, open the directory,
   feed, messages and notifications (the bell confirms the WebSocket path through the edge).
5. Real identity-document collection stays off (`verification.real_documents_enabled`, an admin setting) until the
   institutional approval in `docs/security/checklist.md` is recorded.

## 8. Day-2 operations on Railway
- **Deploys**: every push to `main` redeploys the three repo services; use *Settings → Watch Paths* (`/backend/**`,
  `/frontend/**`, `/infrastructure/nginx/railway/**`) so a frontend commit does not rebuild the backend.
- **Logs**: service → Logs, or `railway logs -s backend`. Logs are JSON (ECS format); the `requestId` field matches the
  `X-Request-Id` header returned to clients.
- **Backups**: Railway volumes (Postgres, storage, clamav) have a *Backups* tab whose schedule depends on the plan;
  additionally take logical dumps with `railway connect Postgres` + `pg_dump` and keep the document encryption key
  off-platform. `scripts/backup.sh` is Compose-only.
- **Metrics**: the management port (8081, Prometheus) is not reachable from outside the private network; use Railway's
  service metrics, or add a private scraper service later.
- **Scaling**: one replica per service. Sessions and rate limits live in Redis, so the backend can be scaled to two
  replicas if needed; RustFS and ClamAV must stay single-replica because of their volumes.
- **Rollback**: Deployments tab → previous successful deployment → *Redeploy* (migrations are forward-only; restore the
  database from a backup if a migration must be undone).
- **Rotation**: change a sealed variable and redeploy; a changed `REDIS_PASSWORD` ends all sessions.

## 9. Differences from the VPS deployment
| | VPS (`docker-compose.prod.yml`) | Railway |
|---|---|---|
| TLS | nginx + certbot | Railway edge; nginx listens on plain HTTP |
| Images | built on the host, pinned by `APP_VERSION` | built by Railway from the Dockerfiles on each push |
| Buckets | created by the operator | created once by the backend (`STORAGE_ENSURE_BUCKETS=true`) |
| Client IP | Docker bridge network is trusted by default | `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` must cover the private network |
| Prometheus | scrape 8081 from the host | not exposed; Railway metrics |
