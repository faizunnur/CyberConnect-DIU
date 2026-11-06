# Share the local stack with testers

`scripts/share-dev.ps1` puts the full local stack (nginx on http://localhost:8088) behind a public HTTPS address so
people outside your network can try it while it runs on your computer. It is for short test sessions, not hosting:
the address changes every start and dies with the laptop.

## How it works
1. Starts the Docker stack (`docker compose --profile full up -d`), using Docker Desktop when it answers and the
   Ubuntu WSL engine otherwise (keeps a hidden WSL session alive so the distro does not stop).
2. Starts a Cloudflare **quick tunnel** (`cloudflared tunnel --url http://localhost:8088`): free, no account, a random
   `https://<words>.trycloudflare.com` address, HTTPS and WebSockets included. `cloudflared.exe` is downloaded once to
   `%LOCALAPPDATA%\cloudflared\` if missing.
3. Restarts the backend with `APP_BASE_URL=<tunnel address>` and `API_DOCS_ENABLED=false`, so verification and
   notification emails link to the public address and the WebSocket handshake accepts that origin. nginx is restarted
   so it picks up the new backend container.
4. Prints the link. `-Status` shows it again; `-Stop` closes the tunnel and restarts the backend with the localhost
   base URL.

```
powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1          # start + print link
powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Status  # current link
powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Stop    # stop sharing
```

## Before the first share
| Setting in `.env` | Why |
|---|---|
| `E2E_FIXTURES_ENABLED=false` | the script refuses to start otherwise: that endpoint creates verified members without any check |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_SSL`/`SMTP_STARTTLS`, `SMTP_AUTH`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | without a real relay, email stays in Mailpit (http://localhost:8025) and testers never receive their verification links; the script warns about this |
| `BOOTSTRAP_SUPER_ADMIN_EMAIL` | register with this email yourself to get the SUPER_ADMIN account that verifies testers' (fictional) documents; needs MFA |

The dev profile stays a development profile: the NoOp scanner is allowed, Swagger is switched off only for the shared
session, and `verification.real_documents_enabled` is irrelevant outside production. Tell testers to upload
**fictional** documents only.

## What testers can do
Register → confirm email → fill the onboarding form → upload a fictional document → wait for you to verify it in the
admin pages → directory, feed, opportunities, research, mentorship, messaging, notifications, impact.

## Limits and caveats
- Quick tunnels are rate-limited by Cloudflare and meant for testing; expect occasional slow first requests.
- All testers arrive through the tunnel; per-address rate limits (login, registration) use the address Cloudflare
  reports in `X-Forwarded-For`, which nginx passes on.
- Stopping the laptop, the stack or the tunnel ends the link. Re-run the script to get a new one and resend it.
- Never share while `E2E_FIXTURES_ENABLED=true`, and never run it against a database that holds real identity
  documents.
