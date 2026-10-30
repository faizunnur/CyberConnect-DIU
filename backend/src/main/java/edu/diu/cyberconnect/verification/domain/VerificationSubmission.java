package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One attempt to verify one affiliation. State transitions are enforced here (ADR-0006). */
@Entity
@Table(name = "verification_submissions")
public class VerificationSubmission {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "affiliation_id", nullable = false)
    private UUID affiliationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "assigned_reviewer_id")
    private UUID assignedReviewerId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VerificationSubmission() {
    }

    public static VerificationSubmission draft(UUID userId, UUID affiliationId, int attemptNumber) {
        VerificationSubmission s = new VerificationSubmission();
        s.id = UUID.randomUUID();
        s.userId = userId;
        s.affiliationId = affiliationId;
        s.status = SubmissionStatus.DRAFT;
        s.attemptNumber = attemptNumber;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void submit() {
        require(SubmissionStatus.DRAFT, "Only drafts can be submitted");
        status = SubmissionStatus.SUBMITTED;
        submittedAt = Instant.now();
        touch();
    }

    public void assign(UUID reviewerId) {
        if (!SubmissionStatus.REVIEWABLE.contains(status)) {
            throw new IllegalStateException("Submission is not awaiting review");
        }
        assignedReviewerId = reviewerId;
        assignedAt = Instant.now();
        status = SubmissionStatus.UNDER_REVIEW;
        touch();
    }

    public void approve(UUID reviewerId) {
        decide(reviewerId, SubmissionStatus.APPROVED);
    }

    public void reject(UUID reviewerId) {
        decide(reviewerId, SubmissionStatus.REJECTED);
    }

    /** An approved appeal flips a rejected submission to approved; the review history keeps both decisions. */
    public void approveOnAppeal(UUID reviewerId) {
        require(SubmissionStatus.REJECTED, "Only rejected submissions can be approved on appeal");
        status = SubmissionStatus.APPROVED;
        decidedBy = reviewerId;
        decidedAt = Instant.now();
        touch();
    }

    public void withdraw() {
        if (!status.isActive()) {
            throw new IllegalStateException("Only active submissions can be withdrawn");
        }
        status = SubmissionStatus.WITHDRAWN;
        decidedAt = Instant.now();
        touch();
    }

    private void decide(UUID reviewerId, SubmissionStatus target) {
        if (!SubmissionStatus.REVIEWABLE.contains(status)) {
            throw new IllegalStateException("Submission is not awaiting review");
        }
        status = target;
        decidedBy = reviewerId;
        decidedAt = Instant.now();
        touch();
    }

    private void require(SubmissionStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAffiliationId() {
        return affiliationId;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getAssignedReviewerId() {
        return assignedReviewerId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
