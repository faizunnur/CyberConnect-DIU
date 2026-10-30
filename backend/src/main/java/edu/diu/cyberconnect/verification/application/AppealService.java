package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealDetail;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealView;
import edu.diu.cyberconnect.verification.domain.Appeal;
import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import edu.diu.cyberconnect.verification.domain.VerificationReview;
import edu.diu.cyberconnect.verification.domain.VerificationSubmission;
import edu.diu.cyberconnect.verification.infrastructure.AppealRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationReviewRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationSubmissionRepository;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appeals: one per rejected submission, decided by a different reviewer; denial is terminal until an admin reopens. */
@Service
@Transactional
public class AppealService {

    private final AppealRepository appeals;
    private final VerificationSubmissionRepository submissions;
    private final VerificationReviewRepository reviews;
    private final ReviewService reviewService;
    private final UserAccounts accounts;
    private final AppProperties app;
    private final MailSender mail;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;

    public AppealService(AppealRepository appeals, VerificationSubmissionRepository submissions,
            VerificationReviewRepository reviews, ReviewService reviewService, UserAccounts accounts, AppProperties app,
            MailSender mail, AuditService audit, AuditContext auditContext, RateLimiter rateLimiter) {
        this.appeals = appeals;
        this.submissions = submissions;
        this.reviews = reviews;
        this.reviewService = reviewService;
        this.accounts = accounts;
        this.app = app;
        this.mail = mail;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
    }

    public AppealView submit(UUID userId, UUID submissionId, String statement) {
        rateLimiter.check("appeal", userId.toString(), 3, Duration.ofHours(24));
        UserSummary user = accounts.get(userId);
        if (!user.emailVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Verify your email address first.");
        }
        if (user.verificationLocked()) {
            throw new ForbiddenException("VERIFICATION_LOCKED", "Verification is closed for this account.");
        }
        VerificationSubmission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND", "Submission not found."));
        if (!submission.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_OWNER", "This submission does not belong to you.");
        }
        if (submission.getStatus() != SubmissionStatus.REJECTED) {
            throw new ConflictException("NOT_REJECTED", "Only rejected submissions can be appealed.");
        }
        if (appeals.findBySubmissionId(submissionId).isPresent()) {
            throw new ConflictException("APPEAL_EXISTS", "An appeal was already submitted for this decision.");
        }
        Appeal appeal = appeals.save(Appeal.submit(submissionId, userId, statement.trim()));
        audit.record(auditContext.entry(VerificationAuditActions.APPEAL_SUBMITTED, VerificationAuditActions.TARGET_APPEAL,
                appeal.getId(), Map.of("submissionId", submissionId.toString())));
        mail.send(new MailMessage(user.email(), "Your " + app.name() + " appeal was received",
                "A different moderator will review your appeal. Follow progress at " + app.baseUrl() + "/onboarding."));
        return SubmissionService.toView(appeal);
    }

    @Transactional(readOnly = true)
    public AppealView getForUser(UUID userId, UUID appealId) {
        Appeal appeal = load(appealId);
        if (!appeal.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_OWNER", "This appeal does not belong to you.");
        }
        return SubmissionService.toView(appeal);
    }

    @Transactional(readOnly = true)
    public Page<AppealDetail> queue(Collection<Appeal.Status> statuses, Pageable pageable) {
        Collection<Appeal.Status> filter = statuses == null || statuses.isEmpty()
                ? List.of(Appeal.Status.SUBMITTED, Appeal.Status.UNDER_REVIEW) : statuses;
        return appeals.findByStatusInOrderByCreatedAtAsc(filter, pageable)
                .map(a -> reviewService.toDetail(a, accounts.get(a.getUserId())));
    }

    @Transactional(readOnly = true)
    public AppealDetail detail(UUID appealId) {
        Appeal appeal = load(appealId);
        return reviewService.toDetail(appeal, accounts.get(appeal.getUserId()));
    }

    public void assign(UUID appealId, UUID reviewerId) {
        Appeal appeal = load(appealId);
        guardSeparation(appeal, reviewerId);
        appeal.assign(reviewerId);
        audit.record(auditContext.entry(VerificationAuditActions.APPEAL_ASSIGNED, VerificationAuditActions.TARGET_APPEAL,
                appealId, Map.of("reviewerId", reviewerId.toString())));
    }

    public void decide(UUID appealId, UUID reviewerId, boolean approved, String textForApplicant, String internalNotes) {
        Appeal appeal = load(appealId);
        guardSeparation(appeal, reviewerId);
        if (appeal.isDecided()) {
            throw new ConflictException("APPEAL_DECIDED", "This appeal was already decided.");
        }
        VerificationSubmission submission = reviewService.load(appeal.getSubmissionId());
        appeal.decide(reviewerId, approved, textForApplicant, internalNotes);
        UserSummary applicant = accounts.get(appeal.getUserId());
        if (approved) {
            submission.approveOnAppeal(reviewerId);
            reviews.save(new VerificationReview(submission.getId(), reviewerId, VerificationReview.Decision.APPROVE, null,
                    textForApplicant, internalNotes));
            reviewService.grantAccess(submission, reviewerId);
            audit.record(auditContext.entry(VerificationAuditActions.APPEAL_APPROVED,
                    VerificationAuditActions.TARGET_APPEAL, appealId, Map.of("submissionId", submission.getId().toString())));
            mail.send(new MailMessage(applicant.email(), "Your " + app.name() + " appeal was approved",
                    "Your appeal was approved and your eligibility is now verified. Sign in at " + app.baseUrl() + "."));
        } else {
            accounts.lockVerification(applicant.id(), reviewerId, "Appeal denied");
            audit.record(auditContext.entry(VerificationAuditActions.APPEAL_DENIED,
                    VerificationAuditActions.TARGET_APPEAL, appealId, Map.of("submissionId", submission.getId().toString())));
            mail.send(new MailMessage(applicant.email(), "Your " + app.name() + " appeal decision",
                    "Your appeal was not approved. " + (textForApplicant == null ? "" : textForApplicant)
                            + "\n\nThis decision is final unless an administrator reopens your case."));
        }
    }

    private void guardSeparation(Appeal appeal, UUID reviewerId) {
        if (appeal.getUserId().equals(reviewerId)) {
            throw new ForbiddenException("SELF_REVIEW", "You cannot review your own appeal.");
        }
        boolean sameReviewer = reviews.findBySubmissionIdOrderByCreatedAtAsc(appeal.getSubmissionId()).stream()
                .anyMatch(r -> r.getReviewerId().equals(reviewerId));
        if (sameReviewer) {
            throw new ForbiddenException("SAME_REVIEWER",
                    "An appeal must be decided by a different moderator than the original decision.");
        }
    }

    private Appeal load(UUID appealId) {
        return appeals.findById(appealId)
                .orElseThrow(() -> new NotFoundException("APPEAL_NOT_FOUND", "Appeal not found."));
    }
}
