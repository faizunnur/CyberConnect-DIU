package edu.diu.cyberconnect.opportunity.domain;

import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityStatus;
import edu.diu.cyberconnect.common.model.OpportunityType;
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
@Table(name = "opportunities")
public class Opportunity {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    private String location;

    @Column(nullable = false)
    private String description;

    private String compensation;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_method", nullable = false)
    private ApplicationMethod applicationMethod;

    @Column(name = "application_url")
    private String applicationUrl;

    @Column(name = "application_email")
    private String applicationEmail;

    @Column(name = "application_count", nullable = false, insertable = false, updatable = false)
    private int applicationCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    protected Opportunity() {
    }

    public static Opportunity draft(UUID authorId) {
        Opportunity o = new Opportunity();
        o.id = UUID.randomUUID();
        o.authorId = authorId;
        o.status = OpportunityStatus.DRAFT;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public void update(OpportunityType type, String title, String organization, LocationType locationType, String location,
            String description, String compensation, LocalDate deadline, ApplicationMethod method, String url, String email) {
        this.type = type;
        this.title = title;
        this.organization = organization;
        this.locationType = locationType;
        this.location = location;
        this.description = description;
        this.compensation = compensation;
        this.deadline = deadline;
        this.applicationMethod = method;
        this.applicationUrl = method == ApplicationMethod.EXTERNAL_URL ? url : null;
        this.applicationEmail = method == ApplicationMethod.EMAIL ? email : null;
        touch();
    }

    public void publish() {
        this.status = OpportunityStatus.OPEN;
        if (this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
        this.closedAt = null;
        touch();
    }

    public void close() {
        this.status = OpportunityStatus.CLOSED;
        this.closedAt = Instant.now();
        touch();
    }

    public void expire() {
        this.status = OpportunityStatus.EXPIRED;
        this.closedAt = Instant.now();
        touch();
    }

    public void remove(UUID moderatorId, String reason) {
        this.status = OpportunityStatus.REMOVED;
        this.removedBy = moderatorId;
        this.removedReason = reason;
        this.removedAt = Instant.now();
        touch();
    }

    public void restore() {
        this.status = closedAt != null ? OpportunityStatus.CLOSED : (publishedAt != null ? OpportunityStatus.OPEN : OpportunityStatus.DRAFT);
        this.removedBy = null;
        this.removedReason = null;
        this.removedAt = null;
        touch();
    }

    public boolean isOpen() {
        return status == OpportunityStatus.OPEN;
    }

    public boolean acceptsApplications(LocalDate today) {
        return isOpen() && applicationMethod == ApplicationMethod.INTERNAL && (deadline == null || !deadline.isBefore(today));
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public OpportunityType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganization() {
        return organization;
    }

    public LocationType getLocationType() {
        return locationType;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public String getCompensation() {
        return compensation;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public OpportunityStatus getStatus() {
        return status;
    }

    public ApplicationMethod getApplicationMethod() {
        return applicationMethod;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public String getApplicationEmail() {
        return applicationEmail;
    }

    public int getApplicationCount() {
        return applicationCount;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
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
