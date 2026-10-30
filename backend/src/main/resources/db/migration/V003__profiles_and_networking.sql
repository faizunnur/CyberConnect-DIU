-- Profiles, skills, experience, education, connections and blocks (M3).

-- ---------------------------------------------------------------------------
-- Profiles (one row per account; created at registration, backfilled below)
-- ---------------------------------------------------------------------------
CREATE TABLE user_profiles (
    user_id                 uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name            text        NOT NULL,
    headline                text,
    bio                     text,
    organization            text,
    job_title               text,
    location                text,
    country                 text,
    specializations         text[]      NOT NULL DEFAULT '{}',
    -- Space-joined copy of specializations: generated columns cannot call array_to_string (STABLE).
    specializations_text    text        NOT NULL DEFAULT '',
    mentor_available        boolean     NOT NULL DEFAULT false,
    mentor_topics           text,
    photo_key               text,
    photo_version           integer     NOT NULL DEFAULT 0,
    profile_visibility      text        NOT NULL DEFAULT 'ALL_MEMBERS'
        CHECK (profile_visibility IN ('ALL_MEMBERS', 'CONNECTIONS_ONLY')),
    email_visibility        text        NOT NULL DEFAULT 'NOBODY'
        CHECK (email_visibility IN ('NOBODY', 'CONNECTIONS', 'ALL_MEMBERS')),
    organization_visibility text        NOT NULL DEFAULT 'ALL_MEMBERS'
        CHECK (organization_visibility IN ('NOBODY', 'CONNECTIONS', 'ALL_MEMBERS')),
    location_visibility     text        NOT NULL DEFAULT 'ALL_MEMBERS'
        CHECK (location_visibility IN ('NOBODY', 'CONNECTIONS', 'ALL_MEMBERS')),
    mentor_visibility       text        NOT NULL DEFAULT 'ALL_MEMBERS'
        CHECK (mentor_visibility IN ('NOBODY', 'CONNECTIONS', 'ALL_MEMBERS')),
    searchable              boolean     NOT NULL DEFAULT true,
    messaging_policy        text        NOT NULL DEFAULT 'CONNECTIONS_ONLY'
        CHECK (messaging_policy IN ('EVERYONE', 'CONNECTIONS_ONLY', 'NOBODY')),
    search_vector           tsvector    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', display_name), 'A')
        || setweight(to_tsvector('simple', coalesce(headline, '')), 'B')
        || setweight(to_tsvector('simple', coalesce(job_title, '') || ' ' || coalesce(organization, '')), 'B')
        || setweight(to_tsvector('simple', specializations_text), 'B')
        || setweight(to_tsvector('simple', coalesce(bio, '')), 'C')) STORED,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_profiles_search_idx ON user_profiles USING gin (search_vector);
CREATE INDEX user_profiles_display_name_trgm_idx ON user_profiles USING gin (display_name gin_trgm_ops);
CREATE INDEX user_profiles_organization_idx ON user_profiles (lower(organization));

INSERT INTO user_profiles (user_id, display_name)
SELECT id, full_name FROM users
ON CONFLICT (user_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Skills (shared catalogue, grows with member input; slug is the identity)
-- ---------------------------------------------------------------------------
CREATE TABLE skills (
    id         uuid        PRIMARY KEY,
    name       text        NOT NULL,
    slug       text        NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT skills_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX skills_name_trgm_idx ON skills USING gin (name gin_trgm_ops);

INSERT INTO skills (id, name, slug) VALUES
    (gen_random_uuid(), 'Penetration Testing', 'penetration-testing'),
    (gen_random_uuid(), 'Web Application Security', 'web-application-security'),
    (gen_random_uuid(), 'Network Security', 'network-security'),
    (gen_random_uuid(), 'Digital Forensics', 'digital-forensics'),
    (gen_random_uuid(), 'Incident Response', 'incident-response'),
    (gen_random_uuid(), 'Malware Analysis', 'malware-analysis'),
    (gen_random_uuid(), 'Reverse Engineering', 'reverse-engineering'),
    (gen_random_uuid(), 'Threat Intelligence', 'threat-intelligence'),
    (gen_random_uuid(), 'Threat Hunting', 'threat-hunting'),
    (gen_random_uuid(), 'SIEM', 'siem'),
    (gen_random_uuid(), 'SOC Operations', 'soc-operations'),
    (gen_random_uuid(), 'Cloud Security', 'cloud-security'),
    (gen_random_uuid(), 'Application Security', 'application-security'),
    (gen_random_uuid(), 'Secure Code Review', 'secure-code-review'),
    (gen_random_uuid(), 'DevSecOps', 'devsecops'),
    (gen_random_uuid(), 'Cryptography', 'cryptography'),
    (gen_random_uuid(), 'Identity and Access Management', 'identity-and-access-management'),
    (gen_random_uuid(), 'Governance, Risk and Compliance', 'governance-risk-and-compliance'),
    (gen_random_uuid(), 'ISO 27001', 'iso-27001'),
    (gen_random_uuid(), 'Security Awareness', 'security-awareness'),
    (gen_random_uuid(), 'Vulnerability Management', 'vulnerability-management'),
    (gen_random_uuid(), 'Red Teaming', 'red-teaming'),
    (gen_random_uuid(), 'Blue Teaming', 'blue-teaming'),
    (gen_random_uuid(), 'OSINT', 'osint'),
    (gen_random_uuid(), 'Mobile Security', 'mobile-security'),
    (gen_random_uuid(), 'IoT Security', 'iot-security'),
    (gen_random_uuid(), 'Linux', 'linux'),
    (gen_random_uuid(), 'Python', 'python'),
    (gen_random_uuid(), 'Bash Scripting', 'bash-scripting'),
    (gen_random_uuid(), 'Burp Suite', 'burp-suite'),
    (gen_random_uuid(), 'Wireshark', 'wireshark'),
    (gen_random_uuid(), 'Metasploit', 'metasploit'),
    (gen_random_uuid(), 'Nmap', 'nmap'),
    (gen_random_uuid(), 'Splunk', 'splunk'),
    (gen_random_uuid(), 'CTF', 'ctf'),
    (gen_random_uuid(), 'Security Research', 'security-research'),
    (gen_random_uuid(), 'Machine Learning for Security', 'machine-learning-for-security');

CREATE TABLE user_skills (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id   uuid        NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    position   smallint    NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, skill_id)
);
CREATE INDEX user_skills_skill_idx ON user_skills (skill_id);

-- ---------------------------------------------------------------------------
-- Experience and education
-- ---------------------------------------------------------------------------
CREATE TABLE profile_experiences (
    id              uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           text        NOT NULL,
    organization    text        NOT NULL,
    employment_type text        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'FREELANCE', 'RESEARCH', 'VOLUNTEER')),
    location        text,
    start_date      date        NOT NULL,
    end_date        date,
    is_current      boolean     NOT NULL DEFAULT false,
    description     text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT profile_experiences_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX profile_experiences_user_idx ON profile_experiences (user_id, start_date DESC);

CREATE TABLE profile_educations (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    institution    text        NOT NULL,
    degree         text,
    field_of_study text,
    start_year     integer     CHECK (start_year IS NULL OR start_year BETWEEN 1950 AND 2100),
    end_year       integer     CHECK (end_year IS NULL OR end_year BETWEEN 1950 AND 2100),
    description    text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT profile_educations_years CHECK (end_year IS NULL OR start_year IS NULL OR end_year >= start_year)
);
CREATE INDEX profile_educations_user_idx ON profile_educations (user_id, start_year DESC);

-- ---------------------------------------------------------------------------
-- Networking: connections (one live row per unordered pair) and blocks
-- ---------------------------------------------------------------------------
CREATE TABLE connections (
    id           uuid        PRIMARY KEY,
    requester_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       text        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    message      text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    responded_at timestamptz,
    CONSTRAINT connections_not_self CHECK (requester_id <> addressee_id)
);
-- No reversed duplicates: A→B and B→A share one live row.
CREATE UNIQUE INDEX connections_pair_live_uidx
    ON connections (least(requester_id, addressee_id), greatest(requester_id, addressee_id))
    WHERE status IN ('PENDING', 'ACCEPTED');
CREATE INDEX connections_addressee_idx ON connections (addressee_id, status, created_at DESC);
CREATE INDEX connections_requester_idx ON connections (requester_id, status, created_at DESC);

CREATE TABLE blocks (
    blocker_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX blocks_blocked_idx ON blocks (blocked_id);
