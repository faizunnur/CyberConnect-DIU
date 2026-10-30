package edu.diu.cyberconnect.impact.domain;

import edu.diu.cyberconnect.common.model.ImpactDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only decision trail of an impact record. */
@Entity
@Table(name = "impact_confirmations")
public class ImpactConfirmation {

    public enum Role { BENEFICIARY, CONTRIBUTOR, MODERATOR }

    @Id
    private UUID id;

    @Column(name = "impact_id", nullable = false)
    private UUID impactId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImpactDecision decision;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImpactConfirmation() {
    }

    public static ImpactConfirmation of(UUID impactId, UUID userId, Role role, ImpactDecision decision, String note) {
        ImpactConfirmation c = new ImpactConfirmation();
        c.id = UUID.randomUUID();
        c.impactId = impactId;
        c.userId = userId;
        c.role = role;
        c.decision = decision;
        c.note = note;
        c.createdAt = Instant.now();
        return c;
    }

    public UUID getId() {
        return id;
    }

    public UUID getImpactId() {
        return impactId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public ImpactDecision getDecision() {
        return decision;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
