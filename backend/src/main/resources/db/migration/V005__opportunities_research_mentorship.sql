-- Opportunities and applications, research projects, mentorship requests (M5).

-- ---------------------------------------------------------------------------
-- Opportunities
-- ---------------------------------------------------------------------------
CREATE TABLE opportunities (
    id                 uuid        PRIMARY KEY,
    author_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type               text        NOT NULL CHECK (type IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'RESEARCH_ASSISTANT',
                                                            'FREELANCE', 'SCHOLARSHIP')),
    title              text        NOT NULL,
    organization       text        NOT NULL,
    location_type      text        NOT NULL CHECK (location_type IN ('ONSITE', 'REMOTE', 'HYBRID')),
    location           text,
    description        text        NOT NULL,
    compensation       text,
    deadline           date,
    status             text        NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'EXPIRED', 'REMOVED')),
    application_method text        NOT NULL CHECK (application_method IN ('INTERNAL', 'EXTERNAL_URL', 'EMAIL')),
    application_url    text,
    application_email  text,
    application_count  integer     NOT NULL DEFAULT 0 CHECK (application_count >= 0),
    published_at       timestamptz,
    closed_at          timestamptz,
    removed_by         uuid        REFERENCES users (id) ON DELETE SET NULL,
    removed_reason     text,
    removed_at         timestamptz,
    search_vector      tsvector    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', title), 'A')
        || setweight(to_tsvector('simple', organization), 'B')
        || setweight(to_tsvector('simple', coalesce(location, '')), 'C')
        || setweight(to_tsvector('simple', description), 'C')) STORED,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT opportunities_method_target CHECK (
        (application_method = 'INTERNAL')
        OR (application_method = 'EXTERNAL_URL' AND application_url IS NOT NULL)
        OR (application_method = 'EMAIL' AND application_email IS NOT NULL)),
    CONSTRAINT opportunities_url_scheme CHECK (application_url IS NULL OR application_url ~* '^https?://')
);
CREATE INDEX opportunities_open_idx ON opportunities (status, published_at DESC);
CREATE INDEX opportunities_author_idx ON opportunities (author_id, created_at DESC);
CREATE INDEX opportunities_deadline_idx ON opportunities (deadline) WHERE status = 'OPEN';
CREATE INDEX opportunities_search_idx ON opportunities USING gin (search_vector);

CREATE TABLE opportunity_skills (
    opportunity_id uuid NOT NULL REFERENCES opportunities (id) ON DELETE CASCADE,
    skill_id       uuid NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    PRIMARY KEY (opportunity_id, skill_id)
);
CREATE INDEX opportunity_skills_skill_idx ON opportunity_skills (skill_id);

CREATE TABLE applications (
    id             uuid        PRIMARY KEY,
    opportunity_id uuid        NOT NULL REFERENCES opportunities (id) ON DELETE CASCADE,
    applicant_id   uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status         text        NOT NULL CHECK (status IN ('SUBMITTED', 'REVIEWED', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    cover_note     text,
    decided_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (opportunity_id, applicant_id)
);
CREATE INDEX applications_applicant_idx ON applications (applicant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Research hub
-- ---------------------------------------------------------------------------
CREATE TABLE research_projects (
    id                  uuid        PRIMARY KEY,
    lead_id             uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title               text        NOT NULL,
    summary             text        NOT NULL,
    status              text        NOT NULL CHECK (status IN ('PROPOSED', 'ACTIVE', 'COMPLETED', 'ARCHIVED', 'REMOVED')),
    looking_for_members boolean     NOT NULL DEFAULT true,
    member_count        integer     NOT NULL DEFAULT 1 CHECK (member_count >= 0),
    start_date          date,
    end_date            date,
    removed_by          uuid        REFERENCES users (id) ON DELETE SET NULL,
    removed_reason      text,
    removed_at          timestamptz,
    search_vector       tsvector    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', title), 'A') || setweight(to_tsvector('simple', summary), 'C')) STORED,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT research_projects_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX research_projects_status_idx ON research_projects (status, created_at DESC);
CREATE INDEX research_projects_lead_idx ON research_projects (lead_id);
CREATE INDEX research_projects_search_idx ON research_projects USING gin (search_vector);

CREATE TABLE research_project_skills (
    project_id uuid NOT NULL REFERENCES research_projects (id) ON DELETE CASCADE,
    skill_id   uuid NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, skill_id)
);
CREATE INDEX research_project_skills_skill_idx ON research_project_skills (skill_id);

CREATE TABLE research_members (
    project_id uuid        NOT NULL REFERENCES research_projects (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       text        NOT NULL CHECK (role IN ('LEAD', 'MEMBER')),
    joined_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX research_members_user_idx ON research_members (user_id);

CREATE TABLE research_applications (
    id           uuid        PRIMARY KEY,
    project_id   uuid        NOT NULL REFERENCES research_projects (id) ON DELETE CASCADE,
    applicant_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       text        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    message      text,
    decided_by   uuid        REFERENCES users (id) ON DELETE SET NULL,
    decided_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX research_applications_pending_uidx ON research_applications (project_id, applicant_id) WHERE status = 'PENDING';
CREATE INDEX research_applications_project_idx ON research_applications (project_id, status, created_at);
CREATE INDEX research_applications_applicant_idx ON research_applications (applicant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Mentorship requests (mentor availability lives on the profile)
-- ---------------------------------------------------------------------------
CREATE TABLE mentorship_requests (
    id            uuid        PRIMARY KEY,
    mentee_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mentor_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        text        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'COMPLETED', 'CANCELLED')),
    topic         text        NOT NULL,
    message       text        NOT NULL,
    response_note text,
    responded_at  timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mentorship_requests_not_self CHECK (mentee_id <> mentor_id)
);
CREATE UNIQUE INDEX mentorship_requests_live_uidx ON mentorship_requests (mentee_id, mentor_id) WHERE status IN ('PENDING', 'ACCEPTED');
CREATE INDEX mentorship_requests_mentor_idx ON mentorship_requests (mentor_id, status, created_at DESC);
CREATE INDEX mentorship_requests_mentee_idx ON mentorship_requests (mentee_id, status, created_at DESC);
