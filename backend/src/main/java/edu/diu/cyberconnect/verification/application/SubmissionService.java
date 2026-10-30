package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealView;
import edu.diu.cyberconnect.verification.application.VerificationViews.DecisionView;
import edu.diu.cyberconnect.verification.application.VerificationViews.DocumentView;
import edu.diu.cyberconnect.verification.application.VerificationViews.StatusView;
import edu.diu.cyberconnect.verification.application.VerificationViews.SubmissionView;
import edu.diu.cyberconnect.verification.domain.Appeal;
import edu.diu.cyberconnect.verification.domain.RejectionReasonCode;
import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import edu.diu.cyberconnect.verification.domain.VerificationDocument;
import edu.diu.cyberconnect.verification.domain.VerificationReview;
import edu.diu.cyberconnect.verification.domain.VerificationSubmission;
import edu.diu.cyberconnect.verification.infrastructure.AppealRepository;
import edu.diu.cyberconnect.verification.infrastructure.RejectionReasonCodeRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationDocumentRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationReviewRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationSubmissionRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applicant side of verification: drafts, document uploads, submission and the consolidated status view. */
@Service
@Transactional
public class SubmissionService {

    static final List<String> ACCEPTED_TYPES = List.of("image/jpeg", "image/png", "application/pdf");

    private final VerificationSubmissionRepository submissions;
    private final VerificationDocumentRepository documents;
    private final VerificationReviewRepository reviews;
    private final RejectionReasonCodeRepository reasonCodes;
    private final AppealRepository appeals;
    private final AffiliationService affiliations;
    private final UserAccounts accounts;
    private final SystemSettings settings;
    private final DocumentPipeline pipeline;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final AppProperties app;
    private final MailSender mail;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;

    public SubmissionService(VerificationSubmissionRepository submissions, VerificationDocumentRepository documents,
            VerificationReviewRepository reviews, RejectionReasonCodeRepository reasonCodes, AppealRepository appeals,
            AffiliationService affiliations, UserAccounts accounts, SystemSettings settings, DocumentPipeline pipeline,
            ObjectStorage storage, StorageProperties storageProperties, AppProperties app, MailSender mail,
            AuditService audit, AuditContext auditContext, RateLimiter rateLimiter) {
        this.submissions = submissions;
        this.documents = documents;
        this.reviews = reviews;
        this.reasonCodes = reasonCodes;
        this.appeals = appeals;
        this.affiliations = affiliations;
        this.accounts = accounts;
        this.settings = settings;
        this.pipeline = pipeline;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.app = app;
        this.mail = mail;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public StatusView status(UUID userId) {
        UserSummary user = accounts.get(userId);
        List<AffiliationSummary> userAffiliations = affiliations.listForUser(userId);
        List<VerificationSubmission> history = submissions.findByUserIdOrderByCreatedAtDesc(userId);
        VerificationSubmission current = history.stream().filter(s -> s.getStatus().isActive()).findFirst()
                .orElseGet(() -> history.isEmpty() ? null : history.get(0));
        Appeal appeal = current == null ? null : appeals.findBySubmissionId(current.getId()).orElse(null);

        AffiliationSummary pending = userAffiliations.stream()
                .filter(a -> a.status() == AffiliationStatus.PENDING).findFirst().orElse(null);
        int maxAttempts = settings.getInt(SettingKeys.MAX_SUBMISSIONS_PER_AFFILIATION);
        int used = pending == null ? 0 : (int) submissions.countByAffiliationId(pending.id());
        boolean hasActive = current != null && current.getStatus().isActive();
        boolean lastAllowsResubmission = current == null || current.getStatus() != SubmissionStatus.REJECTED
                || latestDecision(current.getId()).map(DecisionView::allowsResubmission).orElse(true);
        boolean canStart = user.emailVerified() && !user.verificationLocked() && pending != null && !hasActive
                && used < maxAttempts && lastAllowsResubmission && (appeal == null || appeal.getStatus() == Appeal.Status.DENIED && false);
        boolean canAppeal = user.emailVerified() && !user.verificationLocked() && current != null
                && current.getStatus() == SubmissionStatus.REJECTED && appeal == null;

        return new StatusView(user.accountStatus().name(), user.emailVerified(), user.verificationLocked(),
                userAffiliations, current == null ? null : toView(current, userAffiliations),
                history.stream().map(s -> toView(s, userAffiliations)).toList(),
                appeal == null ? null : toView(appeal), canStart, canAppeal, Math.max(0, maxAttempts - used),
                documentCollectionEnabled(), pipeline.maxFileBytes(),
                settings.getInt(SettingKeys.DOCUMENT_MAX_FILES_PER_SUBMISSION), ACCEPTED_TYPES);
    }

    public SubmissionView startSubmission(UUID userId, UUID affiliationId) {
        UserSummary user = accounts.get(userId);
        requireEmailVerified(user);
        if (user.verificationLocked()) {
            throw new ForbiddenException("VERIFICATION_LOCKED",
                    "Verification is closed for this account. Contact the moderators.");
        }
        AffiliationSummary affiliation = affiliations.get(affiliationId);
        if (!affiliation.userId().equals(userId)) {
            throw new ForbiddenException("NOT_OWNER", "This affiliation does not belong to you.");
        }
        if (affiliation.status() == AffiliationStatus.VERIFIED) {
            throw new ConflictException("AFFILIATION_VERIFIED", "This affiliation is already verified.");
        }
        Optional<VerificationSubmission> active = submissions.findFirstByAffiliationIdAndStatusIn(affiliationId,
                SubmissionStatus.ACTIVE);
        if (active.isPresent()) {
            return toView(active.get(), List.of(affiliation));
        }
        List<VerificationSubmission> previous = submissions.findByAffiliationIdOrderByCreatedAtDesc(affiliationId);
        int maxAttempts = settings.getInt(SettingKeys.MAX_SUBMISSIONS_PER_AFFILIATION);
        if (previous.size() >= maxAttempts) {
            throw new ConflictException("MAX_ATTEMPTS_REACHED",
                    "The maximum number of submissions for this affiliation has been reached.");
        }
        if (!previous.isEmpty()) {
            VerificationSubmission last = previous.get(0);
            if (last.getStatus() == SubmissionStatus.REJECTED
                    && !latestDecision(last.getId()).map(DecisionView::allowsResubmission).orElse(true)) {
                throw new ConflictException("RESUBMISSION_NOT_ALLOWED",
                        "The previous decision does not allow resubmission. You may appeal instead.");
            }
            if (appeals.findBySubmissionId(last.getId()).filter(a -> !a.isDecided()).isPresent()) {
                throw new ConflictException("APPEAL_PENDING", "An appeal is still being reviewed.");
            }
        }
        VerificationSubmission draft = submissions.save(
                VerificationSubmission.draft(userId, affiliationId, previous.size() + 1));
        audit.record(auditContext.entry(VerificationAuditActions.SUBMISSION_STARTED,
                VerificationAuditActions.TARGET_SUBMISSION, draft.getId(),
                Map.of("affiliation", affiliation.type().name(), "attempt", draft.getAttemptNumber())));
        return toView(draft, List.of(affiliation));
    }

    public DocumentView addDocument(UUID userId, UUID submissionId, String documentType, byte[] content,
            String originalFilename, String declaredContentType) {
        rateLimiter.check("document-upload", userId.toString(), 20, Duration.ofHours(1));
        if (!documentCollectionEnabled()) {
            throw new ForbiddenException("DOCUMENT_COLLECTION_DISABLED",
                    "Document collection is not enabled yet. Please check back later.");
        }
        VerificationSubmission submission = ownedDraft(userId, submissionId);
        int maxFiles = settings.getInt(SettingKeys.DOCUMENT_MAX_FILES_PER_SUBMISSION);
        if (documents.countBySubmissionIdAndPurgedAtIsNull(submissionId) >= maxFiles) {
            throw new ConflictException("MAX_FILES_REACHED", "This submission already has the maximum number of files.");
        }
        DocumentPipeline.Stored stored;
        try {
            stored = pipeline.process(submissionId, content, declaredContentType);
        } catch (BadRequestException e) {
            audit.recordDetached(auditContext.entry(VerificationAuditActions.DOCUMENT_REJECTED,
                    VerificationAuditActions.TARGET_SUBMISSION, submissionId, Map.of("code", e.getCode())));
            throw e;
        }
        VerificationDocument document = documents.save(new VerificationDocument(stored.documentId(), submissionId,
                sanitizeType(documentType), stored.storageKey(), stored.contentType(), stored.sizeBytes(),
                stored.sha256(), sanitizeFilename(originalFilename), stored.scanEngine(), stored.encryptionKeyId()));
        audit.record(auditContext.entry(VerificationAuditActions.DOCUMENT_UPLOADED,
                VerificationAuditActions.TARGET_DOCUMENT, document.getId(),
                Map.of("submissionId", submissionId.toString(), "contentType", stored.contentType(),
                        "sizeBytes", stored.sizeBytes(), "scanner", stored.scanEngine())));
        return toView(document);
    }

    public void removeDocument(UUID userId, UUID submissionId, UUID documentId) {
        ownedDraft(userId, submissionId);
        VerificationDocument document = documents.findById(documentId)
                .filter(d -> d.getSubmissionId().equals(submissionId))
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found."));
        storage.delete(storageProperties.buckets().documents(), document.getStorageKey());
        documents.delete(document);
        audit.record(auditContext.entry(VerificationAuditActions.DOCUMENT_REMOVED,
                VerificationAuditActions.TARGET_DOCUMENT, documentId, Map.of("submissionId", submissionId.toString())));
    }

    public SubmissionView submit(UUID userId, UUID submissionId) {
        UserSummary user = accounts.get(userId);
        requireEmailVerified(user);
        VerificationSubmission submission = ownedDraft(userId, submissionId);
        if (documents.countBySubmissionIdAndPurgedAtIsNull(submissionId) == 0) {
            throw new BadRequestException("NO_DOCUMENTS", "Upload at least one document before submitting.");
        }
        submission.submit();
        if (user.accountStatus() == AccountStatus.REJECTED) {
            accounts.changeStatus(userId, AccountStatus.PENDING, userId, "Resubmitted verification evidence");
        }
        audit.record(auditContext.entry(VerificationAuditActions.SUBMISSION_SUBMITTED,
                VerificationAuditActions.TARGET_SUBMISSION, submissionId, Map.of("attempt", submission.getAttemptNumber())));
        mail.send(new MailMessage(user.email(), "Your " + app.name() + " verification was submitted",
                "Thank you. A moderator will review your evidence. You can follow progress at " + app.baseUrl()
                        + "/onboarding."));
        return toView(submission, affiliations.listForUser(userId));
    }

    boolean documentCollectionEnabled() {
        return !app.isProduction() || settings.getBoolean(SettingKeys.REAL_DOCUMENTS_ENABLED);
    }

    Optional<DecisionView> latestDecision(UUID submissionId) {
        return reviews.findTopBySubmissionIdOrderByCreatedAtDesc(submissionId).map(this::toDecision);
    }

    DecisionView toDecision(VerificationReview review) {
        RejectionReasonCode code = review.getReasonCode() == null ? null
                : reasonCodes.findById(review.getReasonCode()).orElse(null);
        String text = review.getReasonTextApplicant();
        if ((text == null || text.isBlank()) && code != null && code.isVisibleToApplicant()) {
            text = code.getApplicantText();
        }
        return new DecisionView(review.getDecision().name(), code == null ? null : code.getCode(), text,
                code == null || code.isAllowsResubmission(), review.getCreatedAt());
    }

    SubmissionView toView(VerificationSubmission s, List<AffiliationSummary> userAffiliations) {
        String type = userAffiliations.stream().filter(a -> a.id().equals(s.getAffiliationId()))
                .map(a -> a.type().name()).findFirst().orElse(null);
        List<DocumentView> docs = documents.findBySubmissionIdOrderByCreatedAtAsc(s.getId()).stream()
                .map(SubmissionService::toView).toList();
        DecisionView decision = s.getStatus() == SubmissionStatus.REJECTED || s.getStatus() == SubmissionStatus.APPROVED
                ? latestDecision(s.getId()).orElse(null) : null;
        return new SubmissionView(s.getId(), s.getAffiliationId(), type, s.getStatus().name(), s.getAttemptNumber(),
                s.getSubmittedAt(), s.getDecidedAt(), docs, decision);
    }

    static DocumentView toView(VerificationDocument d) {
        return new DocumentView(d.getId(), d.getDocumentType(), d.getContentType(), d.getSizeBytes(),
                d.getOriginalFilename(), d.getScanStatus().name(), d.getCreatedAt(), d.isPurged());
    }

    static AppealView toView(Appeal a) {
        return new AppealView(a.getId(), a.getSubmissionId(), a.getStatus().name(), a.getStatement(), a.getCreatedAt(),
                a.getDecidedAt(), a.getDecisionTextApplicant());
    }

    private VerificationSubmission ownedDraft(UUID userId, UUID submissionId) {
        VerificationSubmission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND", "Submission not found."));
        if (!submission.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_OWNER", "This submission does not belong to you.");
        }
        if (submission.getStatus() != SubmissionStatus.DRAFT) {
            throw new ConflictException("SUBMISSION_NOT_EDITABLE", "This submission can no longer be changed.");
        }
        return submission;
    }

    private static void requireEmailVerified(UserSummary user) {
        if (!user.emailVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Verify your email address first.");
        }
    }

    private static String sanitizeType(String documentType) {
        String value = documentType == null ? "EVIDENCE" : documentType.trim().toUpperCase().replaceAll("[^A-Z_]", "");
        return value.isEmpty() ? "EVIDENCE" : value.substring(0, Math.min(40, value.length()));
    }

    private static String sanitizeFilename(String name) {
        if (name == null) {
            return null;
        }
        String base = name.replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.isEmpty() ? null : base.substring(0, Math.min(120, base.length()));
    }
}
