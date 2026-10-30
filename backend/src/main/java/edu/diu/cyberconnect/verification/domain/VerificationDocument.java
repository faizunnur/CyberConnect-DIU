package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Metadata of an encrypted identity document. The object itself lives in private storage under {@code storageKey}. */
@Entity
@Table(name = "verification_documents")
public class VerificationDocument {

    public enum ScanStatus { PENDING, CLEAN, INFECTED, ERROR }

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "original_filename")
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false)
    private ScanStatus scanStatus;

    @Column(name = "scan_engine")
    private String scanEngine;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "encryption_key_id", nullable = false)
    private String encryptionKeyId;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationDocument() {
    }

    public VerificationDocument(UUID id, UUID submissionId, String documentType, String storageKey, String contentType,
            long sizeBytes, String sha256, String originalFilename, String scanEngine, String encryptionKeyId) {
        this.id = id;
        this.submissionId = submissionId;
        this.documentType = documentType;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.originalFilename = originalFilename;
        this.scanStatus = ScanStatus.CLEAN;
        this.scanEngine = scanEngine;
        this.scannedAt = Instant.now();
        this.encryptionKeyId = encryptionKeyId;
        this.createdAt = Instant.now();
    }

    public void markPurged() {
        this.purgedAt = Instant.now();
    }

    public boolean isPurged() {
        return purgedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }

    public String getScanEngine() {
        return scanEngine;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
