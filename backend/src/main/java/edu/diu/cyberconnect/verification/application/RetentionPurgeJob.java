package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.admin.application.SettingKeys;
import edu.diu.cyberconnect.admin.application.SystemSettings;
import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.verification.domain.Appeal;
import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import edu.diu.cyberconnect.verification.domain.VerificationDocument;
import edu.diu.cyberconnect.verification.domain.VerificationSubmission;
import edu.diu.cyberconnect.verification.infrastructure.AppealRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationDocumentRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationSubmissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes document objects after the configured retention window following the final decision (including a decided
 * appeal). Metadata rows stay, marked purged, so the audit trail remains complete.
 */
@Component
public class RetentionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeJob.class);

    private final VerificationSubmissionRepository submissions;
    private final VerificationDocumentRepository documents;
    private final AppealRepository appeals;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final SystemSettings settings;
    private final AuditService audit;

    public RetentionPurgeJob(VerificationSubmissionRepository submissions, VerificationDocumentRepository documents,
            AppealRepository appeals, ObjectStorage storage, StorageProperties storageProperties,
            SystemSettings settings, AuditService audit) {
        this.submissions = submissions;
        this.documents = documents;
        this.appeals = appeals;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.settings = settings;
        this.audit = audit;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.time-zone:Asia/Dhaka}")
    public void runScheduled() {
        int purged = purgeExpired(Instant.now());
        if (purged > 0) {
            log.info("Retention purge removed {} document(s)", purged);
        }
    }

    @Transactional
    public int purgeExpired(Instant now) {
        Duration retention = Duration.ofDays(settings.getInt(SettingKeys.DOCUMENT_RETENTION_DAYS));
        Instant cutoff = now.minus(retention);
        int purged = 0;
        for (VerificationSubmission submission : submissions.finalDecidedBeforeWithUnpurgedDocuments(
                SubmissionStatus.FINAL, cutoff)) {
            Optional<Appeal> appeal = appeals.findBySubmissionId(submission.getId());
            if (appeal.isPresent()) {
                Appeal a = appeal.get();
                if (!a.isDecided() || a.getDecidedAt().isAfter(cutoff)) {
                    continue;
                }
            }
            for (VerificationDocument document : documents.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())) {
                if (document.isPurged()) {
                    continue;
                }
                storage.delete(storageProperties.buckets().documents(), document.getStorageKey());
                document.markPurged();
                audit.record(AuditEntry.system(VerificationAuditActions.DOCUMENT_PURGED,
                        VerificationAuditActions.TARGET_DOCUMENT, document.getId(),
                        Map.of("submissionId", submission.getId().toString(), "retentionDays", retention.toDays())));
                purged++;
            }
        }
        return purged;
    }
}
