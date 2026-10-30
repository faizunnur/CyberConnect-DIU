# Database schema

Conventions: UUID keys, `timestamptz` UTC, text + CHECK enumerations, FK on every reference, Flyway migrations in
`backend/src/main/resources/db/migration`. Runtime role `${app_role}` gets DML via default privileges; append-only
tables REVOKE UPDATE/DELETE.

## V001 — baseline
| Table | Purpose | Notes |
|---|---|---|
| `users` | account identity and lifecycle | `email` unique + lowercase CHECK; `account_status` CHECK; `email_verified_at`, `status_changed_at`, `deactivated_at`, `last_login_at` |
| `roles` | MEMBER, MODERATOR, ADMIN, SUPER_ADMIN (fixed UUIDs `…0001`–`…0004`) | |
| `user_roles` | role grants with `granted_by`, `granted_at` | PK (user_id, role_id) |
| `system_settings` | operator settings as `jsonb` | seeded: real_documents_enabled=false, max_submissions=3, retention_days=30, max_file_bytes=8 MiB, max_files=3, student_id_pattern, deactivation_restore_days=30 |
| `audit_logs` | append-only audit trail | actor_id (FK, SET NULL), actor_roles, action, target_type/id, outcome CHECK, metadata jsonb, ip, user_agent, request_id; indexes on target, actor, created_at; **UPDATE/DELETE revoked from app role** |
| `flyway_schema_history` | Flyway bookkeeping | INSERT/UPDATE/DELETE revoked from app role |

Extensions: `pg_trgm`.

Planned (M2+): email_verification_tokens, password_reset_tokens, mfa_credentials, mfa_recovery_codes,
user_affiliations, verification_submissions, verification_documents, verification_reviews, rejection_reason_codes,
appeals, document_access_log, user_profiles, skills, user_skills, profile_experiences, profile_educations,
connections, mentorship_requests, posts, post_attachments, comments, post_likes, bookmarks, hashtags, post_hashtags,
reports, opportunities, opportunity_skills, applications, research_projects, research_members,
research_applications, conversations, conversation_members, messages, blocks, notifications,
notification_preferences, impact_records, impact_confirmations, moderation_actions.
