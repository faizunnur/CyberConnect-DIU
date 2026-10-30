package edu.diu.cyberconnect.networking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "blocks")
public class Block {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Block() {
    }

    public static Block of(UUID blockerId, UUID blockedId) {
        Block block = new Block();
        block.id = new Key(blockerId, blockedId);
        block.createdAt = Instant.now();
        return block;
    }

    public Key getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "blocker_id", nullable = false)
        private UUID blockerId;

        @Column(name = "blocked_id", nullable = false)
        private UUID blockedId;

        protected Key() {
        }

        public Key(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        public UUID getBlockerId() {
            return blockerId;
        }

        public UUID getBlockedId() {
            return blockedId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && blockerId.equals(other.blockerId) && blockedId.equals(other.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
