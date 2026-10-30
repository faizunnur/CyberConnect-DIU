package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable record of a reviewer decision. Internal notes are never shown to the applicant. */
@Entity
@Table(name = "verification_reviews")
public class VerificationReview {

    public enum Decision { APPROVE, REJECT }

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_text_applicant")
    private String reasonTextApplicant;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationReview() {
    }

    public VerificationReview(UUID submissionId, UUID reviewerId, Decision decision, String reasonCode,
            String reasonTextApplicant, String internalNotes) {
        this.id = UUID.randomUUID();
        this.submissionId = submissionId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.reasonTextApplicant = reasonTextApplicant;
        this.internalNotes = internalNotes;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonTextApplicant() {
        return reasonTextApplicant;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
