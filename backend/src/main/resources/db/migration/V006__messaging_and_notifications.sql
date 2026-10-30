-- Direct messaging (conversations, membership states, messages) and notifications with preferences (M6).

-- ---------------------------------------------------------------------------
-- Conversations: direct (two members) only for now; direct_key keeps one conversation per pair.
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id                   uuid        PRIMARY KEY,
    direct_key           text        NOT NULL UNIQUE,
    last_message_at      timestamptz,
    last_message_preview text,
    last_message_sender  uuid        REFERENCES users (id) ON DELETE SET NULL,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
    conversation_id      uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id              uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- REQUEST: the other member wrote first and this member has not accepted yet.
    state                text        NOT NULL CHECK (state IN ('ACTIVE', 'REQUEST', 'DECLINED', 'ARCHIVED')),
    last_read_message_id uuid,
    last_read_at         timestamptz,
    joined_at            timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX conversation_members_user_idx ON conversation_members (user_id, state);

CREATE TABLE messages (
    id              uuid        PRIMARY KEY,
    conversation_id uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body            text        NOT NULL,
    deleted_at      timestamptz,
    deleted_by      uuid        REFERENCES users (id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_body_length CHECK (char_length(body) <= 4000)
);
-- Keyset pagination: newest first within a conversation.
CREATE INDEX messages_conversation_idx ON messages (conversation_id, created_at DESC, id DESC);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text        NOT NULL,
    title      text        NOT NULL,
    body       text,
    link       text,
    actor_id   uuid        REFERENCES users (id) ON DELETE SET NULL,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text        NOT NULL,
    in_app     boolean     NOT NULL DEFAULT true,
    email      boolean     NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type)
);
