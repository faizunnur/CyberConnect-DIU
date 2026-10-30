package edu.diu.cyberconnect.affiliation.application;

import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import java.time.Instant;
import java.util.UUID;

public record AffiliationSummary(
        UUID id,
        UUID userId,
        AffiliationType type,
        AffiliationStatus status,
        boolean primary,
        String studentId,
        String batch,
        String department,
        Integer graduationYear,
        String cscMemberId,
        Instant verifiedAt) {
}
