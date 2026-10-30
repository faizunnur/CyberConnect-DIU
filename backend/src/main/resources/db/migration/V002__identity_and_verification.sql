-- Identity extensions, affiliations, verification workflow, appeals, document access log, moderation actions.

ALTER TABLE users ADD COLUMN verification_locked_at timestamptz;

-- ---------------------------------------------------------------------------
-- Tokens (hashed; raw values only ever travel by email)
-- ---------------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------------------
-- MFA
-- ---------------------------------------------------------------------------
CREATE TABLE mfa_credentials (
    user_id           uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    totp_secret_enc   text        NOT NULL,
    encryption_key_id text        NOT NULL,
    enabled_at        timestamptz NOT NULL,
    last_used_step    bigint,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE mfa_recovery_codes (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  text        NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mfa_recovery_codes_user_idx ON mfa_recovery_codes (user_id);

-- ---------------------------------------------------------------------------
-- Affiliations (why a person is eligible; each verified separately)
-- ---------------------------------------------------------------------------
CREATE TABLE user_affiliations (
    id                        uuid        PRIMARY KEY,
    user_id                   uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    affiliation_type          text        NOT NULL CHECK (affiliation_type IN ('STUDENT', 'ALUMNI', 'CSC_MEMBER')),
    status                    text        NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'REVOKED')),
    is_primary                boolean     NOT NULL DEFAULT false,
    student_id                text,
    batch                     text,
    department                text,
    graduation_year           integer,
    csc_member_id             text,
    verified_by_submission_id uuid,
    verified_at               timestamptz,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, affiliation_type)
);
CREATE UNIQUE INDEX user_affiliations_primary_uidx ON user_affiliations (user_id) WHERE is_primary;

-- ---------------------------------------------------------------------------
-- Verification workflow
-- ---------------------------------------------------------------------------
CREATE TABLE rejection_reason_codes (
    code                 text    PRIMARY KEY,
    applicant_text       text    NOT NULL,
    allows_resubmission  boolean NOT NULL,
    visible_to_applicant boolean NOT NULL DEFAULT true,
    sort_order           integer NOT NULL DEFAULT 0
);

INSERT INTO rejection_reason_codes (code, applicant_text, allows_resubmission, visible_to_applicant, sort_order) VALUES
    ('DOCUMENT_UNREADABLE', 'The uploaded document could not be read clearly. Please upload a sharper, complete image.', true, true, 10),
    ('DOCUMENT_INCOMPLETE', 'The document does not show all required details (name, identifier and validity).', true, true, 20),
    ('DOCUMENT_EXPIRED', 'The document appears to be expired. Please upload current evidence.', true, true, 30),
    ('DETAILS_MISMATCH', 'The details you entered do not match the document you uploaded.', true, true, 40),
    ('WRONG_DOCUMENT_TYPE', 'The uploaded file is not an accepted type of evidence for this affiliation.', true, true, 50),
    ('NOT_ELIGIBLE', 'The evidence provided does not establish eligibility for this community.', false, true, 60),
    ('DUPLICATE_ACCOUNT', 'An account for this person already exists.', false, true, 70),
    ('SUSPECTED_FRAUD', 'The submission could not be accepted.', false, true, 80),
    ('OTHER', 'The submission could not be approved. Please see the explanation provided.', true, true, 90);

CREATE TABLE verification_submissions (
    id                   uuid        PRIMARY KEY,
    user_id              uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    affiliation_id       uuid        NOT NULL REFERENCES user_affiliations (id) ON DELETE CASCADE,
    status               text        NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    attempt_number       integer     NOT NULL,
    submitted_at         timestamptz,
    assigned_reviewer_id uuid        REFERENCES users (id) ON DELETE SET NULL,
    assigned_at          timestamptz,
    decided_by           uuid        REFERENCES users (id) ON DELETE SET NULL,
    decided_at           timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX verification_submissions_active_uidx ON verification_submissions (affiliation_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW');
CREATE INDEX verification_submissions_queue_idx ON verification_submissions (status, submitted_at);
CREATE INDEX verification_submissions_user_idx ON verification_submissions (user_id, created_at DESC);

CREATE TABLE verification_documents (
    id                uuid        PRIMARY KEY,
    submission_id     uuid        NOT NULL REFERENCES verification_submissions (id) ON DELETE CASCADE,
    document_type     text        NOT NULL,
    storage_key       text        NOT NULL UNIQUE,
    content_type      text        NOT NULL,
    size_bytes        bigint      NOT NULL,
    sha256            text        NOT NULL,
    original_filename text,
    scan_status       text        NOT NULL CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'ERROR')),
    scan_engine       text,
    scanned_at        timestamptz,
    encryption_key_id text        NOT NULL,
    purged_at         timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX verification_documents_submission_idx ON verification_documents (submission_id);

CREATE TABLE verification_reviews (
    id                    uuid        PRIMARY KEY,
    submission_id         uuid        NOT NULL REFERENCES verification_submissions (id) ON DELETE CASCADE,
    reviewer_id           uuid        NOT NULL REFERENCES users (id),
    decision              text        NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    reason_code           text        REFERENCES rejection_reason_codes (code),
    reason_text_applicant text,
    internal_notes        text,
    created_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX verification_reviews_submission_idx ON verification_reviews (submission_id, created_at);

CREATE TABLE appeals (
    id                      uuid        PRIMARY KEY,
    submission_id           uuid        NOT NULL UNIQUE REFERENCES verification_submissions (id) ON DELETE CASCADE,
    user_id                 uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status                  text        NOT NULL CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED')),
    statement               text        NOT NULL,
    assigned_reviewer_id    uuid        REFERENCES users (id) ON DELETE SET NULL,
    assigned_at             timestamptz,
    decided_by              uuid        REFERENCES users (id) ON DELETE SET NULL,
    decided_at              timestamptz,
    decision_text_applicant text,
    internal_notes          text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX appeals_queue_idx ON appeals (status, created_at);

CREATE TABLE document_access_log (
    id          uuid        PRIMARY KEY,
    document_id uuid        NOT NULL REFERENCES verification_documents (id) ON DELETE CASCADE,
    viewer_id   uuid        NOT NULL REFERENCES users (id),
    purpose     text        NOT NULL,
    ip          text,
    user_agent  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX document_access_log_document_idx ON document_access_log (document_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Moderation actions on members
-- ---------------------------------------------------------------------------
CREATE TABLE moderation_actions (
    id             uuid        PRIMARY KEY,
    actor_id       uuid        NOT NULL REFERENCES users (id),
    target_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action         text        NOT NULL CHECK (action IN ('SUSPEND', 'UNSUSPEND', 'DEACTIVATE', 'REACTIVATE', 'WARN', 'REOPEN_VERIFICATION')),
    reason         text        NOT NULL,
    expires_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX moderation_actions_target_idx ON moderation_actions (target_user_id, created_at DESC);

-- Append-only tables: the runtime role may insert and read, never change history.
REVOKE UPDATE, DELETE ON verification_reviews FROM ${app_role};
REVOKE UPDATE, DELETE ON document_access_log FROM ${app_role};
REVOKE UPDATE, DELETE ON moderation_actions FROM ${app_role};
