package edu.diu.cyberconnect.research.domain;

import edu.diu.cyberconnect.common.model.ResearchApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_applications")
public class ResearchApplication {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResearchApplicationStatus status;

    private String message;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResearchApplication() {
    }

    public static ResearchApplication submit(UUID projectId, UUID applicantId, String message) {
        ResearchApplication a = new ResearchApplication();
        a.id = UUID.randomUUID();
        a.projectId = projectId;
        a.applicantId = applicantId;
        a.status = ResearchApplicationStatus.PENDING;
        a.message = message;
        a.createdAt = Instant.now();
        return a;
    }

    public void decide(UUID leadId, boolean accepted) {
        this.status = accepted ? ResearchApplicationStatus.ACCEPTED : ResearchApplicationStatus.DECLINED;
        this.decidedBy = leadId;
        this.decidedAt = Instant.now();
    }

    public void withdraw() {
        this.status = ResearchApplicationStatus.WITHDRAWN;
        this.decidedAt = Instant.now();
    }

    public boolean isPending() {
        return status == ResearchApplicationStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public ResearchApplicationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
