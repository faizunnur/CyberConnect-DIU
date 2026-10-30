package edu.diu.cyberconnect.messaging.domain;

import edu.diu.cyberconnect.common.model.ConversationState;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "conversation_members")
public class ConversationMember {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationState state;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMember() {
    }

    public static ConversationMember of(UUID conversationId, UUID userId, ConversationState state) {
        ConversationMember m = new ConversationMember();
        m.id = new Key(conversationId, userId);
        m.state = state;
        m.joinedAt = Instant.now();
        m.updatedAt = m.joinedAt;
        return m;
    }

    public void setState(ConversationState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public void markRead(UUID messageId, Instant at) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = at;
        this.updatedAt = Instant.now();
    }

    public Key getId() {
        return id;
    }

    public ConversationState getState() {
        return state;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "conversation_id", nullable = false)
        private UUID conversationId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        public UUID getConversationId() {
            return conversationId;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && conversationId.equals(k.conversationId) && userId.equals(k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
