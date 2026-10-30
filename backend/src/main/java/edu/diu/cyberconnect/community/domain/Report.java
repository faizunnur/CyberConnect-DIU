package edu.diu.cyberconnect.community.domain;

import edu.diu.cyberconnect.common.model.ReportReason;
import edu.diu.cyberconnect.common.model.ReportResolution;
import edu.diu.cyberconnect.common.model.ReportStatus;
import edu.diu.cyberconnect.common.model.ReportTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_owner_id")
    private UUID targetOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    private String details;

    @Column(name = "content_snapshot")
    private String contentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    private ReportResolution resolution;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {
    }

    public static Report create(UUID reporterId, ReportTargetType targetType, UUID targetId, UUID targetOwnerId,
            ReportReason reason, String details, String contentSnapshot) {
        Report report = new Report();
        report.id = UUID.randomUUID();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.targetOwnerId = targetOwnerId;
        report.reason = reason;
        report.details = details;
        report.contentSnapshot = contentSnapshot;
        report.status = ReportStatus.OPEN;
        report.createdAt = Instant.now();
        return report;
    }

    public void resolve(UUID moderatorId, ReportResolution resolution, String note) {
        this.status = resolution == ReportResolution.DISMISSED ? ReportStatus.DISMISSED : ReportStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedBy = moderatorId;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getTargetOwnerId() {
        return targetOwnerId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public String getContentSnapshot() {
        return contentSnapshot;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public ReportResolution getResolution() {
        return resolution;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
