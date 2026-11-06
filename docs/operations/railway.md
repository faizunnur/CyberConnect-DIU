# Deploy CyberConnect DIU on Railway — step by step

Follow the steps in order. Each step tells you exactly where to click and what to type. Expect about one hour the
first time. When something fails, jump to the *Troubleshooting* section at the end.

## What you get
Seven Railway services in one project (five built from this repository, two managed databases). Only **edge** is reachable from the internet; everything else talks over
Railway's private network. That single public origin is what keeps the login cookies, CSRF protection and the
WebSocket working, so never add a public domain to the backend or the frontend.

```
internet ──> edge (nginx)  ──> /api/* and /ws ──> backend (Spring Boot)
                           └─> everything else ─> frontend (Next.js)
backend ──> Postgres · Redis · storage (RustFS, files) · clamav (virus scanner)
```

## Step 0 — Prepare these values first
You will paste them into Railway later. Write them down in a password manager, not in the repository.

| Name | How to get it |
|---|---|
| `DB_APP_PASSWORD` | random secret (see below) |
| `STORAGE_SECRET_KEY` | random secret |
| `DOCUMENT_ENCRYPTION_KEY` | base64 of 32 random bytes (see below). **Back this one up: it is the only key that can decrypt uploaded identity documents.** |
| Mailbox password | the password of `cyberconnectdiu@hepttech.com` from cPanel |
| First administrator email | the email address you will register with to become SUPER_ADMIN |

Generate the random values on your laptop (PowerShell, from the repository folder):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/gen-secrets.ps1   # writes .env with random values (skips if .env exists)
notepad .env                                                       # copy DB_APP_PASSWORD, STORAGE_SECRET_KEY, DOCUMENT_ENCRYPTION_KEY
```
If `.env` already exists, generate the three values directly:
```powershell
$b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)   # DOCUMENT_ENCRYPTION_KEY
$b = New-Object byte[] 24; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ([Convert]::ToBase64String($b) -replace '[+/=]','')   # run twice: DB_APP_PASSWORD, STORAGE_SECRET_KEY
```

Email settings used throughout (from the cPanel "Secure SSL/TLS Settings" page):

| Variable | Value |
|---|---|
| `SMTP_HOST` | `mail.hepttech.com` |
| `SMTP_PORT` | `465` |
| `SMTP_SSL` | `true` (port 465 is implicit TLS) |
| `SMTP_STARTTLS` | `false` |
| `SMTP_AUTH` | `true` |
| `SMTP_USERNAME` | `cyberconnectdiu@hepttech.com` |
| `SMTP_PASSWORD` | the mailbox password |
| `MAIL_FROM` | `cyberconnectdiu@hepttech.com` (cPanel rejects a different sender) |

You need a Railway account on the **Hobby plan or higher**: the virus scanner (ClamAV) needs about 3 GB of RAM and is
mandatory in production. Connect GitHub to Railway once: Railway dashboard → account settings → *Integrations* →
GitHub → allow access to `faizunnur/CyberConnect-DIU`.

## Step 1 — Create the project
1. Dashboard → **New Project** → **Empty project**.
2. Rename it (top-left project name) to `cyberconnect-diu`. The environment is `production`; keep it.

## Step 2 — Add PostgreSQL
1. On the project canvas click **Create** → **Database** → **Add PostgreSQL**.
2. Wait until the service card shows a green status. Keep its name **Postgres**.
3. Click the service → **Variables** tab: you will see `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`.
   Do not change them; the backend will reference them.

## Step 3 — Add Redis
1. **Create** → **Database** → **Add Redis**. Keep its name **Redis**.
2. Wait for green. Its variables `REDISHOST`, `REDISPORT`, `REDISPASSWORD` will be referenced later.

## Step 4 — Create the application database user
The managed database only has the `postgres` superuser. The app runs with a least-privilege user that you create once.
1. Click the **Postgres** service → **Data** tab → open the query editor (icon at the top of the Data view).
2. Paste, replacing the password with your `DB_APP_PASSWORD`, and run:
   ```sql
   CREATE ROLE cyberconnect_app LOGIN PASSWORD 'paste-DB_APP_PASSWORD-here';
   GRANT CONNECT ON DATABASE railway TO cyberconnect_app;
   GRANT USAGE ON SCHEMA public TO cyberconnect_app;
   ```
3. Expected result: `CREATE ROLE`, `GRANT`, `GRANT`. (If the Data tab has no query editor, install the Railway CLI with
   `npm i -g @railway/cli`, run `railway login`, `railway link`, `railway connect Postgres`, and paste the SQL there.)

## Step 5 — Add the file storage (RustFS)
Deploy it from the repository (a one-line Dockerfile that pins `rustfs/rustfs:1.0.4`). Railway's **Docker Image**
box sometimes answers *Invalid Docker image* for perfectly valid public images (its validator is rate-limited by
Docker Hub); building from the repo avoids that check entirely.
1. **Create** → **GitHub Repo** → `faizunnur/CyberConnect-DIU` → **Deploy**.
2. Rename the service **storage** (the name becomes its private hostname).
3. **Settings** → **Source** → **Root Directory**: `/infrastructure/railway/storage`.
4. **Settings** → **Volumes** → **Add volume** → mount path `/data`.
5. **Variables** → **Raw Editor** → paste, replacing the secret:
   ```
   RUSTFS_ACCESS_KEY=cyberconnect
   RUSTFS_SECRET_KEY=paste-STORAGE_SECRET_KEY-here
   RUSTFS_VOLUMES=/data
   RUSTFS_ADDRESS=:9000
   RUSTFS_CONSOLE_ENABLE=false
   ```
   Click the eye icon next to `RUSTFS_SECRET_KEY` → **Seal** (hides it for good).
6. **Deploy** the staged changes (button at the top). Do **not** add a public domain to this service.

(If you prefer the Docker Image box and it accepts the name, `docker.io/rustfs/rustfs:1.0.4` with the same volume and
variables is equivalent.)

## Step 6 — Add the virus scanner (ClamAV)
1. **Create** → **GitHub Repo** → `faizunnur/CyberConnect-DIU` → **Deploy**.
2. Rename the service **clamav**.
3. **Settings** → **Source** → **Root Directory**: `/infrastructure/railway/clamav` (pins `clamav/clamav:1.4.6`).
4. **Settings** → **Volumes** → mount path `/var/lib/clamav` (keeps the downloaded signatures).
5. Deploy. The first start downloads about 300 MB of signatures and takes 5–10 minutes; the log ends with
   `clamd started` when it is ready. Memory use settles around 2.5–3 GB; that is normal.

## Step 7 — Add the backend
1. **Create** → **GitHub Repo** → choose `faizunnur/CyberConnect-DIU` → **Add variables** is optional here; click
   **Deploy** (the first build will fail until the variables exist; that is fine).
2. Rename the service **backend**.
3. **Settings** → **Source** → **Root Directory**: `/backend`. Railway now builds `backend/Dockerfile`.
4. **Settings** → **Deploy**:
   - Healthcheck Path: `/api/v1/health`
   - Healthcheck Timeout: `300`
   - Restart Policy: *On Failure*
5. **Settings** → **Networking**: leave **Public Networking empty** (no domain).
6. **Variables** → **Raw Editor** → paste the whole block. Replace every `<...>`; leave the `${{...}}` references
   exactly as written (Railway fills them from the other services):
   ```
   SPRING_PROFILES_ACTIVE=prod
   APP_ENV=prod
   PORT=8080
   SERVER_PORT=8080
   MANAGEMENT_PORT=8081
   APP_BASE_URL=https://placeholder.invalid
   SESSION_COOKIE_SECURE=true
   API_DOCS_ENABLED=false
   E2E_FIXTURES_ENABLED=false

   DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_MIGRATOR_USER=${{Postgres.PGUSER}}
   DB_MIGRATOR_PASSWORD=${{Postgres.PGPASSWORD}}
   DB_APP_USER=cyberconnect_app
   DB_APP_PASSWORD=<DB_APP_PASSWORD from step 4>

   REDIS_HOST=${{Redis.REDISHOST}}
   REDIS_PORT=${{Redis.REDISPORT}}
   REDIS_PASSWORD=${{Redis.REDISPASSWORD}}

   STORAGE_ENDPOINT=http://${{storage.RAILWAY_PRIVATE_DOMAIN}}:9000
   STORAGE_ACCESS_KEY=${{storage.RUSTFS_ACCESS_KEY}}
   STORAGE_SECRET_KEY=${{storage.RUSTFS_SECRET_KEY}}
   STORAGE_ENSURE_BUCKETS=true

   DOCUMENT_ENCRYPTION_KEY=<DOCUMENT_ENCRYPTION_KEY from step 0>
   DOCUMENT_ENCRYPTION_KEY_ID=k1

   SCANNER_TYPE=clamav
   CLAMAV_HOST=${{clamav.RAILWAY_PRIVATE_DOMAIN}}
   CLAMAV_PORT=3310

   SMTP_HOST=mail.hepttech.com
   SMTP_PORT=465
   SMTP_SSL=true
   SMTP_STARTTLS=false
   SMTP_AUTH=true
   SMTP_USERNAME=cyberconnectdiu@hepttech.com
   SMTP_PASSWORD=<mailbox password>
   MAIL_FROM=cyberconnectdiu@hepttech.com

   BOOTSTRAP_SUPER_ADMIN_EMAIL=<your admin email>

   JAVA_TOOL_OPTIONS=-Xmx1g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom
   SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES=10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|::1|[fF][cCdD][0-9a-fA-F]{2}:.*|[fF][eE]80:.*
   ```
7. Seal `DB_APP_PASSWORD`, `DOCUMENT_ENCRYPTION_KEY` and `SMTP_PASSWORD` (eye icon → Seal).
8. Do **not** deploy yet; `APP_BASE_URL` is fixed in Step 10.

What the unusual variables do:
- `STORAGE_ENSURE_BUCKETS=true` makes the backend create the two private file buckets on its first start. You switch
  it to `false` in Step 12.
- `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` tells the backend to trust the edge's private address, so rate limits and
  the audit trail see the real visitor IP instead of the edge.
- `BOOTSTRAP_SUPER_ADMIN_EMAIL` makes the first account registered with that email the super administrator.

## Step 8 — Add the frontend
1. **Create** → **GitHub Repo** → `faizunnur/CyberConnect-DIU` → **Deploy**.
2. Rename the service **frontend**.
3. **Settings** → **Source** → **Root Directory**: `/frontend`.
4. **Settings** → **Deploy** → Healthcheck Path: `/healthz`.
5. **Settings** → **Networking**: no public domain.
6. **Variables** → Raw Editor:
   ```
   PORT=3000
   HOSTNAME=::
   NODE_ENV=production
   ```
   (`HOSTNAME=::` makes Next.js listen on IPv6 too, which Railway's private network uses.)
7. Deploy.

## Step 9 — Add the edge (the public entry point)
1. **Create** → **GitHub Repo** → `faizunnur/CyberConnect-DIU` → **Deploy**.
2. Rename the service **edge**.
3. **Settings** → **Source** → **Root Directory**: `/infrastructure/railway/edge`.
4. **Settings** → **Deploy** → Healthcheck Path: `/healthz`.
5. **Variables** → Raw Editor:
   ```
   PORT=8080
   BACKEND_HOST=${{backend.RAILWAY_PRIVATE_DOMAIN}}
   FRONTEND_HOST=${{frontend.RAILWAY_PRIVATE_DOMAIN}}
   ```
6. **Settings** → **Networking** → **Public Networking** → **Generate Domain**. When asked for the port, enter `8080`.
   Railway shows a domain such as `edge-production-xxxx.up.railway.app`. Copy it.
7. Deploy.

## Step 10 — Tell the backend its public address
1. Open **backend** → **Variables** → edit `APP_BASE_URL` → `https://<the edge domain from Step 9>`
   (for example `https://edge-production-xxxx.up.railway.app`, no trailing slash).
2. **Deploy** the staged backend changes.

The backend refuses to start if this is not an `https://` address, if `SCANNER_TYPE` is not `clamav`, or if
`E2E_FIXTURES_ENABLED` is `true`; that is intentional.

## Step 11 — Check that it works
Wait until all seven service cards are green (the backend needs ClamAV to be ready first; its first start runs the
database migrations and takes 1–2 minutes). Then open in a browser:

| URL | Expect |
|---|---|
| `https://<edge domain>/` | the landing page |
| `https://<edge domain>/healthz` | `{"status":"ok","service":"frontend", ...}` |
| `https://<edge domain>/api/v1/health` | `{"status":"UP","application":"CyberConnect DIU","environment":"prod", ...}` |
| `https://<edge domain>/actuator/health` | `404` (management endpoints are private, as designed) |

In **backend** → **Logs** you should see Flyway finishing at version `V007` and two lines
`Created private storage bucket 'verification-documents'` and `... 'member-media'`.

## Step 12 — Create your administrator account and lock things down
1. Open `https://<edge domain>/register` and register with the email you put in `BOOTSTRAP_SUPER_ADMIN_EMAIL`.
2. Check that mailbox: the verification email proves the SMTP settings work. Click the link.
3. Sign in, open **Settings → Security** and enrol MFA with an authenticator app (admin screens require it).
4. In **backend** → **Variables**: set `STORAGE_ENSURE_BUCKETS=false` and clear `BOOTSTRAP_SUPER_ADMIN_EMAIL`
   (empty value). Deploy.
5. Register a second, normal account, upload a **fictional** test document, verify it from the admin pages, then try
   the directory, feed, messages and notifications. The bell updating without a refresh confirms the WebSocket path.

Real identity-document collection stays switched off (admin setting `verification.real_documents_enabled`) until the
institution's approval is recorded; see `docs/security/checklist.md`.

## Step 13 — Optional: your own domain
1. **edge** → **Settings** → **Networking** → **+ Custom Domain** → type for example `cyberconnect.diu.edu.bd`.
2. Create the CNAME (and TXT, if shown) records Railway displays at your DNS provider; wait for the green check.
3. Change the backend's `APP_BASE_URL` to `https://cyberconnect.diu.edu.bd` and deploy the backend.

## Day-to-day
- **Updates**: every push to `main` rebuilds the five repo services. To avoid rebuilding all of them on every
  commit, set **Settings → Watch Paths** to `/backend/**` on backend, `/frontend/**` on frontend and
  `/infrastructure/railway/<service>/**` on edge, storage and clamav.
- **Logs**: service → **Logs**. Backend logs are JSON; the `requestId` field equals the `X-Request-Id` header a user
  can read from their browser's network tab when reporting a problem.
- **Rollback**: service → **Deployments** → previous successful deployment → **Redeploy**. Database migrations are
  forward-only; to undo one, restore a backup.
- **Backups**: Postgres, storage and clamav each have a volume; open the volume → **Backups** (available on paid
  plans) and enable a schedule. Also take a logical dump now and then: `railway connect Postgres` then
  `pg_dump` from the CLI shell. Keep `DOCUMENT_ENCRYPTION_KEY` outside Railway as well.
- **Costs**: memory is billed per service; ClamAV is the biggest item. Everything else fits in about 2 GB together.
- **Changing a secret**: edit the variable, deploy. Changing `REDIS_PASSWORD` or the Redis service signs everyone out.

## Troubleshooting
| Symptom | Cause and fix |
|---|---|
| backend log: `Refusing to start in production: APP_BASE_URL must use https` | Step 10 not done, or the value has no `https://`. |
| backend log: `scanner.type=noop is not allowed in production` | `SCANNER_TYPE` must be `clamav`. |
| backend log: `Connection refused` to `clamav.railway.internal:3310` | ClamAV still downloading signatures (Step 6). Wait for `clamd started`, then redeploy the backend. |
| backend log: `password authentication failed for user "cyberconnect_app"` | The SQL in Step 4 used a different password than `DB_APP_PASSWORD`. Re-run `ALTER ROLE cyberconnect_app PASSWORD '...'`. |
| backend log: `role "cyberconnect_app" does not exist` | Step 4 was skipped. |
| backend log: `NoSuchBucket` | `STORAGE_ENSURE_BUCKETS` was `false` on the first start. Set it to `true`, deploy once, set it back. |
| edge returns `502` | backend or frontend not healthy yet, or `BACKEND_HOST`/`FRONTEND_HOST` do not match the service names `backend`/`frontend` (renaming a service changes its private hostname). |
| edge returns `429` for everyone after a few requests | `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` missing on the backend, so all visitors look like one address. |
| Verification email never arrives | Check the backend log for `MailSendException`. Port 465 needs `SMTP_SSL=true` and `SMTP_STARTTLS=false`; `MAIL_FROM` must equal the mailbox address; confirm the password in cPanel. |
| Sign-in works but the page says "session expired" immediately | The browser reached the frontend through a different domain than `APP_BASE_URL`. Use only the edge domain and never give backend/frontend their own public domain. |
| WebSocket (bell) not live | Same cause as above, or the `Origin` differs from `APP_BASE_URL`. |
| Railway build fails with `pnpm-lock.yaml` or Maven errors | The service's **Root Directory** is wrong (Steps 5.3, 6.3, 7.3, 8.3, 9.3). |
| **Create → Docker Image** says *Invalid Docker image* | Railway's validator, not the image (`rustfs/rustfs:1.0.4` and `clamav/clamav:1.4.6` are public). Use the GitHub-repo route in Steps 5 and 6, or retry later with the `docker.io/` prefix. |

## Why the setup looks like this
- The app needs two private buckets through one key pair with path-style URLs, which Railway's own Buckets product
  does not offer (per-bucket credentials, virtual-hosted URLs, public network only), so RustFS runs as a service.
- TLS ends at Railway's edge, so the nginx image listens on plain HTTP; nginx resolves the private hostnames on every
  request because their addresses change on each redeploy.
- The single-origin rule, the production guards and the private management port are the same as on the VPS
  deployment (`docs/operations/deployment.md`, ADR-0009).
