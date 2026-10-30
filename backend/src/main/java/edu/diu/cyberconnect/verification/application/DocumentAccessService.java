package edu.diu.cyberconnect.verification.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.StorageProperties;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.storage.ObjectStorage;
import edu.diu.cyberconnect.common.storage.StoredObject;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.verification.domain.DocumentAccessLog;
import edu.diu.cyberconnect.verification.domain.VerificationDocument;
import edu.diu.cyberconnect.verification.infrastructure.DocumentAccessLogRepository;
import edu.diu.cyberconnect.verification.infrastructure.VerificationDocumentRepository;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Decrypts an identity document for an authorised reviewer and records the access (ADR-0007). */
@Service
@Transactional
public class DocumentAccessService {

    public record OpenedDocument(byte[] content, String contentType, String filename) {
    }

    private final VerificationDocumentRepository documents;
    private final DocumentAccessLogRepository accessLog;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final DocumentPipeline pipeline;
    private final AuditService audit;
    private final AuditContext auditContext;

    public DocumentAccessService(VerificationDocumentRepository documents, DocumentAccessLogRepository accessLog,
            ObjectStorage storage, StorageProperties storageProperties, DocumentPipeline pipeline, AuditService audit,
            AuditContext auditContext) {
        this.documents = documents;
        this.accessLog = accessLog;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.pipeline = pipeline;
        this.audit = audit;
        this.auditContext = auditContext;
    }

    public OpenedDocument open(UUID submissionId, UUID documentId, UUID viewerId, String purpose, String ip,
            String userAgent) {
        VerificationDocument document = documents.findById(documentId)
                .filter(d -> d.getSubmissionId().equals(submissionId))
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found."));
        if (document.isPurged()) {
            throw new NotFoundException("DOCUMENT_PURGED", "This document has been deleted under the retention policy.");
        }
        byte[] encrypted;
        try (StoredObject object = storage.get(storageProperties.buckets().documents(), document.getStorageKey())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document content is missing."))) {
            encrypted = object.content().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read document from storage", e);
        }
        byte[] plain = pipeline.decrypt(encrypted, documentId);
        accessLog.save(new DocumentAccessLog(documentId, viewerId, purpose, ip, userAgent));
        audit.record(auditContext.entry(VerificationAuditActions.DOCUMENT_VIEWED, VerificationAuditActions.TARGET_DOCUMENT,
                documentId, Map.of("submissionId", submissionId.toString(), "purpose", purpose)));
        String filename = document.getOriginalFilename() == null ? "document" : document.getOriginalFilename();
        return new OpenedDocument(plain, document.getContentType(), filename);
    }
}
