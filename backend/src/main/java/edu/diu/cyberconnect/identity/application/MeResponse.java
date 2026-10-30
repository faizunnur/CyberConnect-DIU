package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The signed-in user's own view. Never includes identifiers of other members or secrets. */
public record MeResponse(
        UUID id,
        String email,
        String fullName,
        boolean emailVerified,
        AccountStatus accountStatus,
        Set<RoleName> roles,
        Set<String> authorities,
        boolean mfaEnabled,
        boolean mfaRequiredForRole,
        Instant stepUpValidUntil,
        boolean verificationLocked,
        List<AffiliationSummary> affiliations,
        Instant createdAt) {
}
