package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One appeal per rejected submission. A denied appeal is terminal unless an admin reopens verification. */
@Entity
@Table(name = "appeals")
public class Appeal {

    public enum Status { SUBMITTED, UNDER_REVIEW, APPROVED, DENIED }

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private UUID submissionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String statement;

    @Column(name = "assigned_reviewer_id")
    private UUID assignedReviewerId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_text_applicant")
    private String decisionTextApplicant;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appeal() {
    }

    public static Appeal submit(UUID submissionId, UUID userId, String statement) {
        Appeal appeal = new Appeal();
        appeal.id = UUID.randomUUID();
        appeal.submissionId = submissionId;
        appeal.userId = userId;
        appeal.status = Status.SUBMITTED;
        appeal.statement = statement;
        appeal.createdAt = Instant.now();
        appeal.updatedAt = appeal.createdAt;
        return appeal;
    }

    public void assign(UUID reviewerId) {
        if (status != Status.SUBMITTED && status != Status.UNDER_REVIEW) {
            throw new IllegalStateException("Appeal is already decided");
        }
        assignedReviewerId = reviewerId;
        assignedAt = Instant.now();
        status = Status.UNDER_REVIEW;
        updatedAt = assignedAt;
    }

    public void decide(UUID reviewerId, boolean approved, String textForApplicant, String notes) {
        if (status != Status.SUBMITTED && status != Status.UNDER_REVIEW) {
            throw new IllegalStateException("Appeal is already decided");
        }
        status = approved ? Status.APPROVED : Status.DENIED;
        decidedBy = reviewerId;
        decidedAt = Instant.now();
        decisionTextApplicant = textForApplicant;
        internalNotes = notes;
        updatedAt = decidedAt;
    }

    public boolean isDecided() {
        return status == Status.APPROVED || status == Status.DENIED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Status getStatus() {
        return status;
    }

    public String getStatement() {
        return statement;
    }

    public UUID getAssignedReviewerId() {
        return assignedReviewerId;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionTextApplicant() {
        return decisionTextApplicant;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
