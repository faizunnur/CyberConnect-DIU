package edu.diu.cyberconnect.impact.domain;

import edu.diu.cyberconnect.common.model.ImpactStatus;
import edu.diu.cyberconnect.common.model.ImpactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "impact_records")
public class ImpactRecord {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false)
    private ImpactType type;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "research_project_id")
    private UUID researchProjectId;

    @Column(name = "mentorship_request_id")
    private UUID mentorshipRequestId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "contributor_id", nullable = false)
    private UUID contributorId;

    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImpactStatus status;

    @Column(name = "beneficiary_confirmed_at")
    private Instant beneficiaryConfirmedAt;

    @Column(name = "contributor_confirmed_at")
    private Instant contributorConfirmedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "disputed_by")
    private UUID disputedBy;

    @Column(name = "dispute_reason")
    private String disputeReason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImpactRecord() {
    }

    public static ImpactRecord propose(ImpactType type, UUID sourceId, UUID beneficiaryId, UUID contributorId,
            UUID proposedBy, String title, String description) {
        ImpactRecord r = new ImpactRecord();
        r.id = UUID.randomUUID();
        r.type = type;
        switch (type) {
            case JOB_REFERRAL -> r.applicationId = sourceId;
            case RESEARCH -> r.researchProjectId = sourceId;
            case MENTORSHIP -> r.mentorshipRequestId = sourceId;
        }
        r.beneficiaryId = beneficiaryId;
        r.contributorId = contributorId;
        r.proposedBy = proposedBy;
        r.title = title;
        r.description = description;
        r.status = ImpactStatus.PENDING_CONFIRMATION;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        if (proposedBy.equals(beneficiaryId)) {
            r.beneficiaryConfirmedAt = r.createdAt;
        } else {
            r.contributorConfirmedAt = r.createdAt;
        }
        return r;
    }

    public UUID sourceId() {
        return switch (type) {
            case JOB_REFERRAL -> applicationId;
            case RESEARCH -> researchProjectId;
            case MENTORSHIP -> mentorshipRequestId;
        };
    }

    public UUID counterpartOf(UUID userId) {
        return beneficiaryId.equals(userId) ? contributorId : beneficiaryId;
    }

    public boolean involves(UUID userId) {
        return beneficiaryId.equals(userId) || contributorId.equals(userId);
    }

    public boolean awaiting(UUID userId) {
        return status == ImpactStatus.PENDING_CONFIRMATION && involves(userId) && !proposedBy.equals(userId);
    }

    public void confirm(UUID userId) {
        Instant now = Instant.now();
        if (userId.equals(beneficiaryId)) {
            beneficiaryConfirmedAt = now;
        } else {
            contributorConfirmedAt = now;
        }
        status = ImpactStatus.CONFIRMED;
        confirmedAt = now;
        updatedAt = now;
    }

    public void dispute(UUID userId, String reason) {
        status = ImpactStatus.DISPUTED;
        disputedBy = userId;
        disputeReason = reason;
        updatedAt = Instant.now();
    }

    public void withdraw() {
        status = ImpactStatus.REJECTED;
        updatedAt = Instant.now();
    }

    public void resolve(UUID moderatorId, boolean confirmed, String note) {
        Instant now = Instant.now();
        status = confirmed ? ImpactStatus.CONFIRMED : ImpactStatus.REJECTED;
        if (confirmed) {
            confirmedAt = now;
        }
        resolvedBy = moderatorId;
        resolvedAt = now;
        resolutionNote = note;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public ImpactType getType() {
        return type;
    }

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public UUID getContributorId() {
        return contributorId;
    }

    public UUID getProposedBy() {
        return proposedBy;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ImpactStatus getStatus() {
        return status;
    }

    public Instant getBeneficiaryConfirmedAt() {
        return beneficiaryConfirmedAt;
    }

    public Instant getContributorConfirmedAt() {
        return contributorConfirmedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public UUID getDisputedBy() {
        return disputedBy;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
