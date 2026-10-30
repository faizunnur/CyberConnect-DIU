package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.notification.application.NotificationService;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealDetail;
import edu.diu.cyberconnect.verification.application.VerificationViews.QueueItem;
import edu.diu.cyberconnect.verification.application.VerificationViews.ReasonCodeView;
import edu.diu.cyberconnect.verification.application.VerificationViews.ReviewView;
import edu.diu.cyberconnect.verification.application.VerificationViews.SubmissionDetail;
import edu.diu.cyberconnect.verification.domain.Appeal;
import edu.diu.cyberconnect.verification.domain.RejectionReasonCode;
import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import edu.diu.cyberconnect.verification.domain.VerificationReview;
import edu.diu.cyberconnect.verification.domain.VerificationSubmission;
import edu.diu.cyberconnect.verification.infrastructure.AppealRepository;
import edu.diu.cyberconnect.verification.infrastructure.RejectionReasonCodeRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationDocumentRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationReviewRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationSubmissionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderator side: queue, detail, assignment, approval and rejection with separation of duties. */
@Service
@Transactional
public class ReviewService {

    private final VerificationSubmissionRepository submissions;
    private final VerificationDocumentRepository documents;
    private final VerificationReviewRepository reviews;
    private final RejectionReasonCodeRepository reasonCodes;
    private final AppealRepository appeals;
    private final AffiliationService affiliations;
    private final UserAccounts accounts;
    private final AppProperties app;
    private final MailSender mail;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final NotificationService notifications;

    public ReviewService(VerificationSubmissionRepository submissions, VerificationDocumentRepository documents,
            VerificationReviewRepository reviews, RejectionReasonCodeRepository reasonCodes, AppealRepository appeals,
            AffiliationService affiliations, UserAccounts accounts, AppProperties app, MailSender mail,
            AuditService audit, AuditContext auditContext, NotificationService notifications) {
        this.submissions = submissions;
        this.documents = documents;
        this.reviews = reviews;
        this.reasonCodes = reasonCodes;
        this.appeals = appeals;
        this.affiliations = affiliations;
        this.accounts = accounts;
        this.app = app;
        this.mail = mail;
        this.audit = audit;
        this.auditContext = auditContext;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Page<QueueItem> queue(Collection<SubmissionStatus> statuses, UUID assignee, Pageable pageable) {
        Collection<SubmissionStatus> filter = statuses == null || statuses.isEmpty() ? SubmissionStatus.REVIEWABLE : statuses;
        return submissions.queue(filter, assignee, pageable).map(this::toQueueItem);
    }

    @Transactional(readOnly = true)
    public List<ReasonCodeView> reasonCodes() {
        return reasonCodes.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new ReasonCodeView(c.getCode(), c.getApplicantText(), c.isAllowsResubmission())).toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detail(UUID submissionId) {
        VerificationSubmission submission = load(submissionId);
        UserSummary applicant = accounts.get(submission.getUserId());
        AffiliationSummary affiliation = affiliations.get(submission.getAffiliationId());
        List<ReviewView> history = reviews.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(r -> new ReviewView(r.getId(), r.getReviewerId(), r.getDecision().name(), r.getReasonCode(),
                        r.getReasonTextApplicant(), r.getInternalNotes(), r.getCreatedAt())).toList();
        AppealDetail appeal = appeals.findBySubmissionId(submissionId).map(a -> toDetail(a, applicant)).orElse(null);
        int previous = (int) submissions.countByAffiliationId(submission.getAffiliationId()) - 1;
        return new SubmissionDetail(submission.getId(), submission.getUserId(), applicant.fullName(), applicant.email(),
                affiliation, submission.getStatus().name(), submission.getAttemptNumber(), submission.getSubmittedAt(),
                submission.getAssignedReviewerId(), submission.getDecidedBy(), submission.getDecidedAt(),
                documents.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream().map(SubmissionService::toView).toList(),
                history, appeal, Math.max(previous, 0));
    }

    public void assign(UUID submissionId, UUID reviewerId) {
        VerificationSubmission submission = load(submissionId);
        guardSeparation(submission, reviewerId);
        submission.assign(reviewerId);
        audit.record(auditContext.entry(VerificationAuditActions.SUBMISSION_ASSIGNED,
                VerificationAuditActions.TARGET_SUBMISSION, submissionId, Map.of("reviewerId", reviewerId.toString())));
    }

    public void approve(UUID submissionId, UUID reviewerId, String internalNotes) {
        VerificationSubmission submission = load(submissionId);
        guardSeparation(submission, reviewerId);
        if (!SubmissionStatus.REVIEWABLE.contains(submission.getStatus())) {
            throw new ConflictException("SUBMISSION_NOT_REVIEWABLE", "This submission is not awaiting review.");
        }
        submission.approve(reviewerId);
        reviews.save(new VerificationReview(submissionId, reviewerId, VerificationReview.Decision.APPROVE, null, null,
                internalNotes));
        grantAccess(submission, reviewerId);
        audit.record(auditContext.entry(VerificationAuditActions.SUBMISSION_APPROVED,
                VerificationAuditActions.TARGET_SUBMISSION, submissionId, Map.of("userId", submission.getUserId().toString())));
        UserSummary applicant = accounts.get(submission.getUserId());
        mail.send(new MailMessage(applicant.email(), "Welcome to " + app.name(),
                "Your eligibility has been verified. Sign in at " + app.baseUrl() + " to complete your profile."));
        notifications.notify(applicant.id(), NotificationType.VERIFICATION_DECISION, "Your membership is verified",
                "Welcome to the community. Complete your profile to be found by other members.", "/settings/profile", null);
    }

    public void reject(UUID submissionId, UUID reviewerId, String reasonCode, String reasonTextApplicant,
            String internalNotes) {
        VerificationSubmission submission = load(submissionId);
        guardSeparation(submission, reviewerId);
        if (!SubmissionStatus.REVIEWABLE.contains(submission.getStatus())) {
            throw new ConflictException("SUBMISSION_NOT_REVIEWABLE", "This submission is not awaiting review.");
        }
        RejectionReasonCode code = reasonCodes.findById(reasonCode == null ? "" : reasonCode)
                .orElseThrow(() -> new BadRequestException("REASON_CODE_INVALID", "Choose a valid rejection reason."));
        String applicantText = reasonTextApplicant == null || reasonTextApplicant.isBlank()
                ? (code.isVisibleToApplicant() ? code.getApplicantText() : "The submission could not be approved.")
                : reasonTextApplicant.trim();
        submission.reject(reviewerId);
        reviews.save(new VerificationReview(submissionId, reviewerId, VerificationReview.Decision.REJECT, code.getCode(),
                applicantText, internalNotes));

        UserSummary applicant = accounts.get(submission.getUserId());
        if (applicant.accountStatus() == AccountStatus.PENDING && !affiliations.hasVerifiedAffiliation(applicant.id())) {
            accounts.changeStatus(applicant.id(), AccountStatus.REJECTED, reviewerId, "Verification rejected: " + code.getCode());
        }
        audit.record(auditContext.entry(VerificationAuditActions.SUBMISSION_REJECTED,
                VerificationAuditActions.TARGET_SUBMISSION, submissionId,
                Map.of("userId", submission.getUserId().toString(), "reasonCode", code.getCode(),
                        "allowsResubmission", code.isAllowsResubmission())));
        notifications.notify(applicant.id(), NotificationType.VERIFICATION_DECISION, "Your verification was not approved",
                applicantText, "/onboarding", null);
        mail.send(new MailMessage(applicant.email(), "Your " + app.name() + " verification decision", """
                Your verification submission was not approved.

                Reason: %s

                %s
                Review the decision at %s/onboarding.
                """.formatted(applicantText,
                code.isAllowsResubmission() ? "You may submit new evidence." : "You may submit an appeal.",
                app.baseUrl())));
    }

    /** Shared by approval and approved appeals: verifies the affiliation and lifts the account to VERIFIED. */
    void grantAccess(VerificationSubmission submission, UUID reviewerId) {
        affiliations.markVerified(submission.getAffiliationId(), submission.getId());
        UserSummary applicant = accounts.get(submission.getUserId());
        if (applicant.accountStatus() == AccountStatus.PENDING || applicant.accountStatus() == AccountStatus.REJECTED) {
            accounts.changeStatus(applicant.id(), AccountStatus.VERIFIED, reviewerId, "Verification approved");
        }
    }

    VerificationSubmission load(UUID submissionId) {
        return submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND", "Submission not found."));
    }

    static void guardSeparation(VerificationSubmission submission, UUID reviewerId) {
        if (submission.getUserId().equals(reviewerId)) {
            throw new ForbiddenException("SELF_REVIEW", "You cannot review your own submission.");
        }
    }

    AppealDetail toDetail(Appeal a, UserSummary applicant) {
        VerificationReview original = reviews.findTopBySubmissionIdOrderByCreatedAtDesc(a.getSubmissionId()).orElse(null);
        return new AppealDetail(a.getId(), a.getSubmissionId(), a.getUserId(), applicant.fullName(), a.getStatus().name(),
                a.getStatement(), a.getAssignedReviewerId(), a.getDecidedBy(), a.getDecidedAt(),
                a.getDecisionTextApplicant(), a.getInternalNotes(), a.getCreatedAt(),
                original == null ? null : original.getReviewerId(), original == null ? null : original.getReasonCode());
    }

    private QueueItem toQueueItem(VerificationSubmission s) {
        UserSummary applicant = accounts.get(s.getUserId());
        AffiliationSummary affiliation = affiliations.get(s.getAffiliationId());
        return new QueueItem(s.getId(), s.getUserId(), applicant.fullName(), affiliation.type().name(),
                s.getStatus().name(), s.getAttemptNumber(), s.getSubmittedAt(), s.getAssignedReviewerId(),
                (int) documents.countBySubmissionIdAndPurgedAtIsNull(s.getId()));
    }
}
