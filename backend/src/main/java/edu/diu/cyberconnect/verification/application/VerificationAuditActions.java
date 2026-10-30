package edu.diu.cyberconnect.verification.application;

public final class VerificationAuditActions {

    public static final String SUBMISSION_STARTED = "VERIFICATION_SUBMISSION_STARTED";
    public static final String DOCUMENT_UPLOADED = "VERIFICATION_DOCUMENT_UPLOADED";
    public static final String DOCUMENT_REJECTED = "VERIFICATION_DOCUMENT_REJECTED";
    public static final String DOCUMENT_REMOVED = "VERIFICATION_DOCUMENT_REMOVED";
    public static final String SUBMISSION_SUBMITTED = "VERIFICATION_SUBMISSION_SUBMITTED";
    public static final String SUBMISSION_ASSIGNED = "VERIFICATION_SUBMISSION_ASSIGNED";
    public static final String SUBMISSION_APPROVED = "VERIFICATION_SUBMISSION_APPROVED";
    public static final String SUBMISSION_REJECTED = "VERIFICATION_SUBMISSION_REJECTED";
    public static final String APPEAL_SUBMITTED = "VERIFICATION_APPEAL_SUBMITTED";
    public static final String APPEAL_ASSIGNED = "VERIFICATION_APPEAL_ASSIGNED";
    public static final String APPEAL_APPROVED = "VERIFICATION_APPEAL_APPROVED";
    public static final String APPEAL_DENIED = "VERIFICATION_APPEAL_DENIED";
    public static final String DOCUMENT_VIEWED = "VERIFICATION_DOCUMENT_VIEWED";
    public static final String DOCUMENT_PURGED = "VERIFICATION_DOCUMENT_PURGED";

    public static final String TARGET_SUBMISSION = "VERIFICATION_SUBMISSION";
    public static final String TARGET_DOCUMENT = "VERIFICATION_DOCUMENT";
    public static final String TARGET_APPEAL = "APPEAL";

    private VerificationAuditActions() {
    }
}
