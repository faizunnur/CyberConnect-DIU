package edu.diu.cyberconnect.opportunity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "opportunity_skills")
public class OpportunitySkill {

    @EmbeddedId
    private Key id;

    protected OpportunitySkill() {
    }

    public static OpportunitySkill of(UUID opportunityId, UUID skillId) {
        OpportunitySkill link = new OpportunitySkill();
        link.id = new Key(opportunityId, skillId);
        return link;
    }

    public Key getId() {
        return id;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "opportunity_id", nullable = false)
        private UUID opportunityId;

        @Column(name = "skill_id", nullable = false)
        private UUID skillId;

        protected Key() {
        }

        public Key(UUID opportunityId, UUID skillId) {
            this.opportunityId = opportunityId;
            this.skillId = skillId;
        }

        public UUID getOpportunityId() {
            return opportunityId;
        }

        public UUID getSkillId() {
            return skillId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && opportunityId.equals(k.opportunityId) && skillId.equals(k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(opportunityId, skillId);
        }
    }
}
