-- Photos and documents attached to posts. Uploaded one at a time (each request stays under the edge body limit), held
-- as the uploader's unclaimed attachment until a post claims it, then served only to viewers who may see that post.
-- Unclaimed uploads older than a day are purged by a scheduled job.
CREATE TABLE post_attachments (
    id                uuid        PRIMARY KEY,
    owner_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id           uuid        REFERENCES posts (id) ON DELETE CASCADE,
    kind              text        NOT NULL CHECK (kind IN ('IMAGE', 'FILE')),
    storage_key       text        NOT NULL UNIQUE,
    content_type      text        NOT NULL,
    size_bytes        bigint      NOT NULL CHECK (size_bytes > 0),
    original_filename text        CHECK (original_filename IS NULL OR char_length(original_filename) <= 200),
    width             integer,
    height            integer,
    position          integer     NOT NULL DEFAULT 0 CHECK (position BETWEEN 0 AND 9),
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX post_attachments_post_idx ON post_attachments (post_id, position) WHERE post_id IS NOT NULL;
CREATE INDEX post_attachments_unclaimed_idx ON post_attachments (owner_id, created_at) WHERE post_id IS NULL;
-- Posts made of attachments only keep an empty body (posts.body stays NOT NULL); grants come from V001's defaults.
