package edu.diu.cyberconnect.research.domain;

import edu.diu.cyberconnect.common.model.ResearchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "research_projects")
public class ResearchProject {

    @Id
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResearchStatus status;

    @Column(name = "looking_for_members", nullable = false)
    private boolean lookingForMembers;

    @Column(name = "member_count", nullable = false, insertable = false, updatable = false)
    private int memberCount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_reason")
    private String removedReason;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResearchProject() {
    }

    public static ResearchProject create(UUID leadId) {
        ResearchProject p = new ResearchProject();
        p.id = UUID.randomUUID();
        p.leadId = leadId;
        p.status = ResearchStatus.PROPOSED;
        p.lookingForMembers = true;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void update(String title, String summary, boolean lookingForMembers, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.summary = summary;
        this.lookingForMembers = lookingForMembers;
        this.startDate = startDate;
        this.endDate = endDate;
        touch();
    }

    public void setStatus(ResearchStatus target) {
        this.status = target;
        touch();
    }

    public void remove(UUID moderatorId, String reason) {
        this.status = ResearchStatus.REMOVED;
        this.removedBy = moderatorId;
        this.removedReason = reason;
        this.removedAt = Instant.now();
        touch();
    }

    public void restore() {
        this.status = ResearchStatus.ACTIVE;
        this.removedBy = null;
        this.removedReason = null;
        this.removedAt = null;
        touch();
    }

    public boolean isLive() {
        return status == ResearchStatus.PROPOSED || status == ResearchStatus.ACTIVE;
    }

    public boolean isVisible() {
        return status != ResearchStatus.REMOVED;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public ResearchStatus getStatus() {
        return status;
    }

    public boolean isLookingForMembers() {
        return lookingForMembers;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
