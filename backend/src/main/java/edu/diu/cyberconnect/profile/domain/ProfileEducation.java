package edu.diu.cyberconnect.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_educations")
public class ProfileEducation {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String institution;

    private String degree;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfileEducation() {
    }

    public static ProfileEducation create(UUID userId) {
        ProfileEducation education = new ProfileEducation();
        education.id = UUID.randomUUID();
        education.userId = userId;
        education.createdAt = Instant.now();
        education.updatedAt = education.createdAt;
        return education;
    }

    public void update(String institution, String degree, String fieldOfStudy, Integer startYear, Integer endYear,
            String description) {
        this.institution = institution.trim();
        this.degree = blankToNull(degree);
        this.fieldOfStudy = blankToNull(fieldOfStudy);
        this.startYear = startYear;
        this.endYear = endYear;
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

    public String getInstitution() {
        return institution;
    }

    public String getDegree() {
        return degree;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public String getDescription() {
        return description;
    }
}
