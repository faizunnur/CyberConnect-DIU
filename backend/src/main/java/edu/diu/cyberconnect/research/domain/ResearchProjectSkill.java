package edu.diu.cyberconnect.research.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "research_project_skills")
public class ResearchProjectSkill {

    @EmbeddedId
    private Key id;

    protected ResearchProjectSkill() {
    }

    public static ResearchProjectSkill of(UUID projectId, UUID skillId) {
        ResearchProjectSkill link = new ResearchProjectSkill();
        link.id = new Key(projectId, skillId);
        return link;
    }

    public Key getId() {
        return id;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "project_id", nullable = false)
        private UUID projectId;

        @Column(name = "skill_id", nullable = false)
        private UUID skillId;

        protected Key() {
        }

        public Key(UUID projectId, UUID skillId) {
            this.projectId = projectId;
            this.skillId = skillId;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public UUID getSkillId() {
            return skillId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && projectId.equals(k.projectId) && skillId.equals(k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, skillId);
        }
    }
}
