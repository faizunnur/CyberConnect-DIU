package edu.diu.cyberconnect.opportunity.domain;

import edu.diu.cyberconnect.common.model.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    private UUID id;

    @Column(name = "opportunity_id", nullable = false)
    private UUID opportunityId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "cover_note")
    private String coverNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() {
    }

    public static Application submit(UUID opportunityId, UUID applicantId, String coverNote) {
        Application a = new Application();
        a.id = UUID.randomUUID();
        a.opportunityId = opportunityId;
        a.applicantId = applicantId;
        a.status = ApplicationStatus.SUBMITTED;
        a.coverNote = coverNote;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public void resubmit(String coverNote) {
        this.status = ApplicationStatus.SUBMITTED;
        this.coverNote = coverNote;
        this.decidedAt = null;
        this.updatedAt = Instant.now();
    }

    public void withdraw() {
        this.status = ApplicationStatus.WITHDRAWN;
        this.updatedAt = Instant.now();
    }

    public void decide(ApplicationStatus decision) {
        this.status = decision;
        this.decidedAt = Instant.now();
        this.updatedAt = this.decidedAt;
    }

    public boolean isLive() {
        return status != ApplicationStatus.WITHDRAWN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOpportunityId() {
        return opportunityId;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getCoverNote() {
        return coverNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
