package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models for applicants and moderators. Applicant views never contain internal notes or storage keys. */
public final class VerificationViews {

    private VerificationViews() {
    }

    public record DocumentView(UUID id, String documentType, String contentType, long sizeBytes,
            String originalFilename, String scanStatus, Instant createdAt, boolean purged) {
    }

    public record DecisionView(String decision, String reasonCode, String reasonText, boolean allowsResubmission,
            Instant decidedAt) {
    }

    public record SubmissionView(UUID id, UUID affiliationId, String affiliationType, String status, int attemptNumber,
            Instant submittedAt, Instant decidedAt, List<DocumentView> documents, DecisionView decision) {
    }

    public record AppealView(UUID id, UUID submissionId, String status, String statement, Instant createdAt,
            Instant decidedAt, String decisionText) {
    }

    /** Everything the onboarding screens need in one call. */
    public record StatusView(
            String accountStatus,
            boolean emailVerified,
            boolean verificationLocked,
            List<AffiliationSummary> affiliations,
            SubmissionView currentSubmission,
            List<SubmissionView> history,
            AppealView appeal,
            boolean canStartSubmission,
            boolean canAppeal,
            int attemptsRemaining,
            boolean documentCollectionEnabled,
            int maxFileBytes,
            int maxFilesPerSubmission,
            List<String> acceptedContentTypes) {
    }

    public record ReasonCodeView(String code, String applicantText, boolean allowsResubmission) {
    }

    public record QueueItem(UUID id, UUID userId, String applicantName, String affiliationType, String status,
            int attemptNumber, Instant submittedAt, UUID assignedReviewerId, int documentCount) {
    }

    public record ReviewView(UUID id, UUID reviewerId, String decision, String reasonCode, String reasonTextApplicant,
            String internalNotes, Instant createdAt) {
    }

    public record SubmissionDetail(UUID id, UUID userId, String applicantName, String applicantEmail,
            AffiliationSummary affiliation, String status, int attemptNumber, Instant submittedAt,
            UUID assignedReviewerId, UUID decidedBy, Instant decidedAt, List<DocumentView> documents,
            List<ReviewView> reviews, AppealDetail appeal, int previousAttempts) {
    }

    public record AppealDetail(UUID id, UUID submissionId, UUID userId, String applicantName, String status,
            String statement, UUID assignedReviewerId, UUID decidedBy, Instant decidedAt, String decisionTextApplicant,
            String internalNotes, Instant createdAt, UUID originalReviewerId, String originalReasonCode) {
    }
}
