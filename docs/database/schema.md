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
affiliation; one appeal per submission; one live connection row per member pair; one open report per reporter and
target; one application per opportunity and applicant; one pending research application and one live mentorship
request per pair; audit/review/access-log/moderation tables append-only.

## V003 — profiles and networking (M3)
| Table | Purpose | Notes |
|---|---|---|
| `user_profiles` | member-facing profile + privacy settings (PK = user id) | `display_name` (defaults to the registered name), headline, bio, organization, job_title, location, country, `specializations text[]` (+ `specializations_text` copy for FTS), mentor fields, `photo_key`/`photo_version` (opaque; served via `/members/{id}/photo`), visibility CHECKs (`profile_visibility`, `email/organization/location/mentor_visibility`), `searchable`, `messaging_policy`; `search_vector` generated tsvector (GIN) + trigram index on name. Created at registration; backfilled for existing users. |
| `skills` | shared catalogue | `slug` unique (`^[a-z0-9]+(-[a-z0-9]+)*$`), trigram index; 37 seeded cybersecurity skills, members may add more |
| `user_skills` | member ↔ skill with `position` | PK (user_id, skill_id) |
| `profile_experiences` | work history | `employment_type` CHECK, `start_date`/`end_date` with CHECK, `is_current` |
| `profile_educations` | education history | `start_year`/`end_year` CHECKs |
| `connections` | requests and accepted connections | `status` PENDING/ACCEPTED/DECLINED, optional `message`; **partial unique index on (least, greatest) of the pair for live rows** → no reversed duplicates; not-self CHECK |
| `blocks` | one-directional blocks | PK (blocker_id, blocked_id), not-self CHECK |

## V004 — community (M4)
| Table | Purpose | Notes |
|---|---|---|
| `posts` | feed posts and reposts | `intent` CHECK (7 intents), `visibility` ALL_MEMBERS/CONNECTIONS_ONLY, `repost_of_id` (one level), `status` ACTIVE/REMOVED/DELETED with `removed_by/reason/at`, denormalised `like_count`/`comment_count`/`repost_count` (atomic SQL updates, never client-supplied), `edited_at`, generated `search_vector` (GIN); body ≤ 5000 chars |
| `comments` | comments and one-level replies | `parent_id` → top-level comment (enforced in the service), same status model; body ≤ 2000 |
| `post_likes` | PK (post_id, user_id) | |
| `bookmarks` | PK (user_id, post_id) | private to the member |
| `hashtags`, `post_hashtags` | lower-cased tags extracted from bodies (≤ 10 per post) | `tag` unique, lowercase CHECK |
| `reports` | member reports on POST/COMMENT/USER (MESSAGE reserved for M6) | `content_snapshot` at report time, `target_owner_id`, status OPEN/RESOLVED/DISMISSED, `resolution` CONTENT_REMOVED/NO_ACTION/DISMISSED; **partial unique index: one OPEN report per reporter and target** |

## V005 — opportunities, research, mentorship (M5)
| Table | Purpose | Notes |
|---|---|---|
| `opportunities` | listings | `type` CHECK (6 types, no MENTORSHIP per D-10), `location_type`, `status` DRAFT/OPEN/CLOSED/EXPIRED/REMOVED, `application_method` INTERNAL/EXTERNAL_URL/EMAIL with CHECKs that the matching target is present and URLs are http(s), `deadline`, `application_count` (server-maintained), removal columns, generated `search_vector` |
| `opportunity_skills` | needed skills from the shared `skills` catalogue | PK (opportunity_id, skill_id) |
| `applications` | internal applications | UNIQUE (opportunity_id, applicant_id); status SUBMITTED/REVIEWED/SHORTLISTED/ACCEPTED/REJECTED/WITHDRAWN; `cover_note` |
| `research_projects` | research hub projects | `status` PROPOSED/ACTIVE/COMPLETED/ARCHIVED/REMOVED, `looking_for_members`, `member_count`, dates, removal columns, `search_vector` |
| `research_project_skills`, `research_members` (role LEAD/MEMBER), `research_applications` | collaboration | **partial unique: one PENDING application per project and applicant** |
| `mentorship_requests` | mentee → mentor requests | status PENDING/ACCEPTED/DECLINED/COMPLETED/CANCELLED, topic, message, response note; not-self CHECK; **partial unique: one live request per pair** |

Planned (M6+): conversations, conversation_members, messages, notifications, notification_preferences,
impact_records, impact_confirmations.
