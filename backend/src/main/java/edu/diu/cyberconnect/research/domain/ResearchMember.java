package edu.diu.cyberconnect.research.domain;

import edu.diu.cyberconnect.common.model.ResearchRole;
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
@Table(name = "research_members")
public class ResearchMember {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResearchRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected ResearchMember() {
    }

    public static ResearchMember of(UUID projectId, UUID userId, ResearchRole role) {
        ResearchMember m = new ResearchMember();
        m.id = new Key(projectId, userId);
        m.role = role;
        m.joinedAt = Instant.now();
        return m;
    }

    public Key getId() {
        return id;
    }

    public ResearchRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "project_id", nullable = false)
        private UUID projectId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID projectId, UUID userId) {
            this.projectId = projectId;
            this.userId = userId;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && projectId.equals(k.projectId) && userId.equals(k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, userId);
        }
    }
}
