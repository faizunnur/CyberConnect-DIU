package edu.diu.cyberconnect.affiliation.domain;

import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_affiliations")
public class UserAffiliation {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", nullable = false)
    private AffiliationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AffiliationStatus status;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "student_id")
    private String studentId;

    private String batch;

    private String department;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "csc_member_id")
    private String cscMemberId;

    @Column(name = "verified_by_submission_id")
    private UUID verifiedBySubmissionId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAffiliation() {
    }

    public static UserAffiliation create(UUID userId, AffiliationType type, boolean primary, String studentId,
            String batch, String department, Integer graduationYear, String cscMemberId) {
        UserAffiliation affiliation = new UserAffiliation();
        affiliation.id = UUID.randomUUID();
        affiliation.userId = userId;
        affiliation.type = type;
        affiliation.status = AffiliationStatus.PENDING;
        affiliation.primary = primary;
        affiliation.studentId = blankToNull(studentId);
        affiliation.batch = blankToNull(batch);
        affiliation.department = blankToNull(department);
        affiliation.graduationYear = graduationYear;
        affiliation.cscMemberId = blankToNull(cscMemberId);
        affiliation.createdAt = Instant.now();
        affiliation.updatedAt = affiliation.createdAt;
        return affiliation;
    }

    public void markVerified(UUID submissionId) {
        this.status = AffiliationStatus.VERIFIED;
        this.verifiedBySubmissionId = submissionId;
        this.verifiedAt = Instant.now();
        this.updatedAt = verifiedAt;
    }

    public void revoke() {
        this.status = AffiliationStatus.REVOKED;
        this.updatedAt = Instant.now();
    }

    public void updateDetails(String studentId, String batch, String department, Integer graduationYear,
            String cscMemberId) {
        this.studentId = blankToNull(studentId);
        this.batch = blankToNull(batch);
        this.department = blankToNull(department);
        this.graduationYear = graduationYear;
        this.cscMemberId = blankToNull(cscMemberId);
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

    public AffiliationType getType() {
        return type;
    }

    public AffiliationStatus getStatus() {
        return status;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getBatch() {
        return batch;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public String getCscMemberId() {
        return cscMemberId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedBySubmissionId() {
        return verifiedBySubmissionId;
    }
}
