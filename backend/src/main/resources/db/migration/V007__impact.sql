-- Confirmed impact tracking (M7, D-12): either party proposes, the counterpart confirms or disputes, moderators
-- resolve disputes; only CONFIRMED records count anywhere.

CREATE TABLE impact_records (
    id                       uuid        PRIMARY KEY,
    impact_type              text        NOT NULL CHECK (impact_type IN ('JOB_REFERRAL', 'RESEARCH', 'MENTORSHIP')),
    application_id           uuid        REFERENCES applications (id) ON DELETE CASCADE,
    research_project_id      uuid        REFERENCES research_projects (id) ON DELETE CASCADE,
    mentorship_request_id    uuid        REFERENCES mentorship_requests (id) ON DELETE CASCADE,
    beneficiary_id           uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    contributor_id           uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    proposed_by              uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                    text        NOT NULL,
    description              text,
    status                   text        NOT NULL CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'DISPUTED', 'REJECTED')),
    beneficiary_confirmed_at timestamptz,
    contributor_confirmed_at timestamptz,
    confirmed_at             timestamptz,
    disputed_by              uuid        REFERENCES users (id) ON DELETE SET NULL,
    dispute_reason           text,
    resolved_by              uuid        REFERENCES users (id) ON DELETE SET NULL,
    resolved_at              timestamptz,
    resolution_note          text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT impact_records_parties CHECK (beneficiary_id <> contributor_id),
    CONSTRAINT impact_records_proposer CHECK (proposed_by IN (beneficiary_id, contributor_id)),
    -- Exactly one source, and it must match the type.
    CONSTRAINT impact_records_source CHECK (
        (impact_type = 'JOB_REFERRAL' AND application_id IS NOT NULL AND research_project_id IS NULL AND mentorship_request_id IS NULL)
        OR (impact_type = 'RESEARCH' AND research_project_id IS NOT NULL AND application_id IS NULL AND mentorship_request_id IS NULL)
        OR (impact_type = 'MENTORSHIP' AND mentorship_request_id IS NOT NULL AND application_id IS NULL AND research_project_id IS NULL))
);
-- One live record per source and pair (rejected ones may be re-proposed).
CREATE UNIQUE INDEX impact_records_source_pair_uidx
    ON impact_records (coalesce(application_id, research_project_id, mentorship_request_id), beneficiary_id, contributor_id)
    WHERE status <> 'REJECTED';
CREATE INDEX impact_records_beneficiary_idx ON impact_records (beneficiary_id, status);
CREATE INDEX impact_records_contributor_idx ON impact_records (contributor_id, status);
CREATE INDEX impact_records_status_idx ON impact_records (status, updated_at DESC);

CREATE TABLE impact_confirmations (
    id         uuid        PRIMARY KEY,
    impact_id  uuid        NOT NULL REFERENCES impact_records (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       text        NOT NULL CHECK (role IN ('BENEFICIARY', 'CONTRIBUTOR', 'MODERATOR')),
    decision   text        NOT NULL CHECK (decision IN ('PROPOSE', 'CONFIRM', 'DISPUTE', 'WITHDRAW', 'RESOLVE_CONFIRMED', 'RESOLVE_REJECTED')),
    note       text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX impact_confirmations_impact_idx ON impact_confirmations (impact_id, created_at);

-- The decision trail is append-only.
REVOKE UPDATE, DELETE ON impact_confirmations FROM ${app_role};
