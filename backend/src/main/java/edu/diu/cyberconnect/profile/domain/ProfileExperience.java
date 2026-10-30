package edu.diu.cyberconnect.profile.domain;

import edu.diu.cyberconnect.common.model.EmploymentType;
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
@Table(name = "profile_experiences")
public class ProfileExperience {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;

    private String location;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfileExperience() {
    }

    public static ProfileExperience create(UUID userId) {
        ProfileExperience experience = new ProfileExperience();
        experience.id = UUID.randomUUID();
        experience.userId = userId;
        experience.createdAt = Instant.now();
        experience.updatedAt = experience.createdAt;
        return experience;
    }

    public void update(String title, String organization, EmploymentType employmentType, String location,
            LocalDate startDate, LocalDate endDate, boolean current, String description) {
        this.title = title.trim();
        this.organization = organization.trim();
        this.employmentType = employmentType;
        this.location = blankToNull(location);
        this.startDate = startDate;
        this.endDate = current ? null : endDate;
        this.current = current;
        this.description = blankToNull(description);
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganization() {
        return organization;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public String getLocation() {
        return location;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getDescription() {
        return description;
    }
}
