-- CyberConnect DIU baseline schema.
-- Conventions: UUID keys, timestamptz (UTC), text + CHECK for enumerations, FK on every reference.
-- Runs as the migrator role; grants the runtime role (${app_role}) least privilege.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                uuid        PRIMARY KEY,
    email             text        NOT NULL,
    email_verified_at timestamptz,
    password_hash     text        NOT NULL,
    full_name         text        NOT NULL,
    account_status    text        NOT NULL
        CHECK (account_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED', 'DEACTIVATED')),
    status_changed_at timestamptz NOT NULL DEFAULT now(),
    deactivated_at    timestamptz,
    last_login_at     timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);
CREATE UNIQUE INDEX users_email_uidx ON users (email);
CREATE INDEX users_account_status_idx ON users (account_status);

CREATE TABLE roles (
    id          uuid PRIMARY KEY,
    name        text NOT NULL UNIQUE CHECK (name IN ('MEMBER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN')),
    description text NOT NULL
);

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES roles (id),
    granted_by uuid        REFERENCES users (id) ON DELETE SET NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

-- ---------------------------------------------------------------------------
-- Administration
-- ---------------------------------------------------------------------------
CREATE TABLE system_settings (
    setting_key text        PRIMARY KEY,
    value       jsonb       NOT NULL,
    description text        NOT NULL DEFAULT '',
    updated_by  uuid        REFERENCES users (id) ON DELETE SET NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Audit (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          uuid        PRIMARY KEY,
    actor_id    uuid        REFERENCES users (id) ON DELETE SET NULL,
    actor_roles text,
    action      text        NOT NULL,
    target_type text,
    target_id   uuid,
    outcome     text        NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    metadata    jsonb,
    ip          text,
    user_agent  text,
    request_id  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_target_idx ON audit_logs (target_type, target_id, created_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_id, created_at DESC);
CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'MEMBER',      'Default role for every account'),
    ('00000000-0000-0000-0000-000000000002', 'MODERATOR',   'Reviews verification submissions, appeals and reports'),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN',       'Manages roles, settings and moderation oversight'),
    ('00000000-0000-0000-0000-000000000004', 'SUPER_ADMIN', 'Full administrative authority; cannot be removed if last');

INSERT INTO system_settings (setting_key, value, description) VALUES
    ('verification.real_documents_enabled', 'false',
     'Real identity-document collection stays disabled until institutional approval and retention policy are recorded.'),
    ('verification.max_submissions_per_affiliation', '3',
     'Maximum verification submissions a member may make for one affiliation.'),
    ('verification.documents.retention_days', '30',
     'Days after the final verification or appeal decision before document objects are purged.'),
    ('verification.documents.max_file_bytes', '8388608',
     'Maximum size of a single identity document upload (8 MiB).'),
    ('verification.documents.max_files_per_submission', '3',
     'Maximum number of files per verification submission.'),
    ('verification.student_id_pattern', '"^[A-Za-z0-9-]{5,20}$"',
     'Permissive student-id format until the official DIU format is confirmed.'),
    ('accounts.deactivation_restore_days', '30',
     'Window in which a member can restore a deactivated account.');

-- ---------------------------------------------------------------------------
-- Runtime role privileges (least privilege; audit tables are append-only)
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO ${app_role};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_role};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${app_role};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${app_role};

REVOKE UPDATE, DELETE ON audit_logs FROM ${app_role};
REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM ${app_role};
