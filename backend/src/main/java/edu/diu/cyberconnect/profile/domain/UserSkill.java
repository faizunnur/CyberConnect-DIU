package edu.diu.cyberconnect.profile.domain;

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
@Table(name = "user_skills")
public class UserSkill {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private short position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserSkill() {
    }

    public static UserSkill of(UUID userId, UUID skillId, int position) {
        UserSkill link = new UserSkill();
        link.id = new Key(userId, skillId);
        link.position = (short) position;
        link.createdAt = Instant.now();
        return link;
    }

    public Key getId() {
        return id;
    }

    public short getPosition() {
        return position;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "skill_id", nullable = false)
        private UUID skillId;

        protected Key() {
        }

        public Key(UUID userId, UUID skillId) {
            this.userId = userId;
            this.skillId = skillId;
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getSkillId() {
            return skillId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && userId.equals(other.userId) && skillId.equals(other.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, skillId);
        }
    }
}
