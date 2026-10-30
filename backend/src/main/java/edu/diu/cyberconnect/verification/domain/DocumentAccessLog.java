package edu.diu.cyberconnect.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of every identity-document view. */
@Entity
@Table(name = "document_access_log")
public class DocumentAccessLog {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(nullable = false)
    private String purpose;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentAccessLog() {
    }

    public DocumentAccessLog(UUID documentId, UUID viewerId, String purpose, String ip, String userAgent) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.viewerId = viewerId;
        this.purpose = purpose;
        this.ip = ip;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
