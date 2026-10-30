# Database schema

Conventions: UUID keys, `timestamptz` UTC, text + CHECK enumerations, FK on every reference, Flyway migrations in
`backend/src/main/resources/db/migration`. Runtime role `${app_role}` gets DML via default privileges; append-only
tables REVOKE UPDATE/DELETE. Cross-module references are plain UUID columns (no JPA associations across modules).

## V001 — baseline (M1)
| Table | Purpose | Notes |
|---|---|---|
| `users` | account identity and lifecycle | `email` unique + lowercase CHECK; `account_status` CHECK; `email_verified_at`, `status_changed_at`, `deactivated_at`, `last_login_at`, `verification_locked_at` (V002) |
| `roles` | MEMBER, MODERATOR, ADMIN, SUPER_ADMIN (fixed UUIDs `…0001`–`…0004`) | |
| `user_roles` | role grants with `granted_by`, `granted_at` | PK (user_id, role_id) |
| `system_settings` | operator settings as `jsonb` | seeded: real_documents_enabled=false, max_submissions=3, retention_days=30, max_file_bytes=8 MiB, max_files=3, student_id_pattern, deactivation_restore_days=30 |
| `audit_logs` | append-only audit trail | actor_id (FK, SET NULL), actor_roles, action, target_type/id, outcome CHECK, metadata jsonb, ip, user_agent, request_id; **UPDATE/DELETE revoked** |
| `flyway_schema_history` | Flyway bookkeeping | INSERT/UPDATE/DELETE revoked from app role |

Extensions: `pg_trgm`.

## V002 — identity and verification (M2)
| Table | Purpose | Notes |
|---|---|---|
| `email_verification_tokens`, `password_reset_tokens` | one-time links | only `token_hash` (SHA-256) stored; `expires_at`, `used_at` |
| `mfa_credentials` | TOTP secret per user | `totp_secret_enc` AES-256-GCM (AAD = user id), `encryption_key_id`, `last_used_step` (replay protection) |
| `mfa_recovery_codes` | single-use codes | Argon2id `code_hash`, `used_at` |
| `user_affiliations` | why a person is eligible | `affiliation_type`, `status` (PENDING/VERIFIED/REVOKED), `is_primary` (partial unique per user), applicant fields, `verified_by_submission_id`; UNIQUE (user_id, affiliation_type) |
| `rejection_reason_codes` | catalogue | `applicant_text`, `allows_resubmission`, `visible_to_applicant`; seeded with 9 codes |
| `verification_submissions` | one attempt per affiliation | status CHECK; `attempt_number`; assignment + decision columns; **partial unique index: one active submission per affiliation** |
| `verification_documents` | encrypted document metadata | opaque `storage_key` (unique), `sha256`, `scan_status`, `scan_engine`, `encryption_key_id`, `purged_at`; never a URL |
| `verification_reviews` | decision history | `decision`, `reason_code` FK, `reason_text_applicant`, `internal_notes` (never shown); **append-only** |
| `appeals` | one per rejected submission | `submission_id` UNIQUE; status CHECK; applicant-facing text vs internal notes |
| `document_access_log` | every document view | viewer, purpose, ip, user agent; **append-only** |
| `moderation_actions` | member lifecycle actions | SUSPEND/UNSUSPEND/DEACTIVATE/REACTIVATE/WARN/REOPEN_VERIFICATION with reason, optional `expires_at`; **append-only** |

Integrity rules covered so far: unique normalised email; unique user–affiliation pair; one active submission per
affiliation; one appeal per submission; audit/review/access-log/moderation tables append-only.

Planned (M3+): user_profiles, skills, user_skills, profile_experiences, profile_educations, connections,
mentorship_requests, posts, post_attachments, comments, post_likes, bookmarks, hashtags, post_hashtags, reports,
opportunities, opportunity_skills, applications, research_projects, research_members, research_applications,
conversations, conversation_members, messages, blocks, notifications, notification_preferences, impact_records,
impact_confirmations.
