package edu.diu.cyberconnect.affiliation.application;

/** Affiliation-specific fields supplied by the applicant. Unused fields are null. */
public record AffiliationDetails(String studentId, String batch, String department, Integer graduationYear,
        String cscMemberId) {
}
