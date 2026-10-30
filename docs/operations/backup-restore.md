# Backup and restore

## What is backed up
| Data | Where | How | Contains personal data |
|---|---|---|---|
| PostgreSQL | volume `postgres-data` | `pg_dump --format=custom` (logical, restorable across minor versions) | yes (profiles, messages, audit) |
| Object storage | volume `storage-data` | tar of the RustFS data directory | yes, but identity documents are AES-256-GCM encrypted at rest |
| Redis | volume `redis-data` | tar of the AOF (sessions, rate limits) | session ids only; optional |
| `.env` | host file | **not** copied by the script: store it in the institution's secret manager | the document encryption key |

`scripts/backup.sh [dir]` produces `postgres-<ts>.dump`, `storage-<ts>.tar.gz`, `redis-<ts>.tar.gz` and prunes files
older than 14 days. Copy the directory off-host nightly (e.g. `rclone sync ./backups remote:cyberconnect-backups`
or `scp`); encrypt the transport and the remote (`rclone crypt` or an encrypted bucket).

## Restore
```
sh scripts/restore.sh backups/postgres-20261030T020000Z.dump backups/storage-20261030T020000Z.tar.gz
```
The script stops the application containers, restores the database (`pg_restore --clean --if-exists`), replaces the
storage volume contents, then starts the stack; Flyway validates the schema on start. Members have to sign in again.

## Verification drill (quarterly)
1. Restore the latest backup into a scratch Compose project on another host (`name:` changed in the compose file).
2. Sign in as a moderator, open the verification queue, open a document: it must decrypt (proves the key and the
   storage archive are consistent).
3. Record the drill date and duration in this file.

| Drill date | Result |
|---|---|
| (none yet) | — |

## Retention (what is deleted automatically)
- Identity documents: 30 days after the final decision (`verification.documents.retention_days`), metadata and
  audit rows are kept (D-24).
- Notifications: read ones after 90 days, unread after 365 days.
- Deactivated accounts: restorable for 30 days (D-16); permanent erasure is manual (runbook).
- Backups: 14 days on host; off-host copies follow the institution's policy.
