-- Community feed: posts, comments, likes, reposts, bookmarks, hashtags and reports (M4).

CREATE TABLE posts (
    id             uuid        PRIMARY KEY,
    author_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    intent         text        NOT NULL CHECK (intent IN ('JOB_OPPORTUNITY', 'RESEARCH_COLLABORATION', 'SEEKING_HELP',
                                                          'MENTORSHIP', 'TECHNICAL_DISCUSSION', 'ACHIEVEMENT', 'EVENT')),
    body           text        NOT NULL,
    visibility     text        NOT NULL DEFAULT 'ALL_MEMBERS' CHECK (visibility IN ('ALL_MEMBERS', 'CONNECTIONS_ONLY')),
    repost_of_id   uuid        REFERENCES posts (id) ON DELETE SET NULL,
    status         text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'DELETED')),
    removed_by     uuid        REFERENCES users (id) ON DELETE SET NULL,
    removed_reason text,
    removed_at     timestamptz,
    -- Denormalised counters maintained by the service with atomic updates; never accepted from clients.
    like_count     integer     NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count  integer     NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    repost_count   integer     NOT NULL DEFAULT 0 CHECK (repost_count >= 0),
    edited_at      timestamptz,
    search_vector  tsvector    GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT posts_body_length CHECK (char_length(body) <= 5000)
);
CREATE INDEX posts_feed_idx ON posts (status, created_at DESC);
CREATE INDEX posts_author_idx ON posts (author_id, created_at DESC);
CREATE INDEX posts_repost_idx ON posts (repost_of_id) WHERE repost_of_id IS NOT NULL;
CREATE INDEX posts_search_idx ON posts USING gin (search_vector);

CREATE TABLE comments (
    id             uuid        PRIMARY KEY,
    post_id        uuid        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id      uuid        REFERENCES comments (id) ON DELETE CASCADE,
    body           text        NOT NULL,
    status         text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'DELETED')),
    removed_by     uuid        REFERENCES users (id) ON DELETE SET NULL,
    removed_reason text,
    removed_at     timestamptz,
    edited_at      timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT comments_body_length CHECK (char_length(body) <= 2000)
);
CREATE INDEX comments_post_idx ON comments (post_id, created_at);
CREATE INDEX comments_parent_idx ON comments (parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE post_likes (
    post_id    uuid        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX post_likes_user_idx ON post_likes (user_id);

CREATE TABLE bookmarks (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    uuid        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);
CREATE INDEX bookmarks_user_idx ON bookmarks (user_id, created_at DESC);

CREATE TABLE hashtags (
    id         uuid        PRIMARY KEY,
    tag        text        NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT hashtags_tag_format CHECK (tag = lower(tag) AND char_length(tag) BETWEEN 1 AND 64)
);

CREATE TABLE post_hashtags (
    post_id    uuid NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    hashtag_id uuid NOT NULL REFERENCES hashtags (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, hashtag_id)
);
CREATE INDEX post_hashtags_tag_idx ON post_hashtags (hashtag_id);

-- Reports on posts, comments or members. One open report per reporter and target; moderators resolve or dismiss.
CREATE TABLE reports (
    id               uuid        PRIMARY KEY,
    reporter_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type      text        NOT NULL CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'MESSAGE')),
    target_id        uuid        NOT NULL,
    target_owner_id  uuid        REFERENCES users (id) ON DELETE SET NULL,
    reason           text        NOT NULL CHECK (reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'MISINFORMATION',
                                                           'IMPERSONATION', 'OTHER')),
    details          text,
    content_snapshot text,
    status           text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolved_by      uuid        REFERENCES users (id) ON DELETE SET NULL,
    resolved_at      timestamptz,
    resolution       text        CHECK (resolution IS NULL OR resolution IN ('CONTENT_REMOVED', 'NO_ACTION', 'DISMISSED')),
    resolution_note  text,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX reports_open_per_reporter_uidx ON reports (reporter_id, target_type, target_id) WHERE status = 'OPEN';
CREATE INDEX reports_queue_idx ON reports (status, created_at);
CREATE INDEX reports_target_idx ON reports (target_type, target_id);
