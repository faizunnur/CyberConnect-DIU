package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Cross-module view of an account. Never carries password hashes or secrets. */
public record UserSummary(
        UUID id,
        String email,
        String fullName,
        AccountStatus accountStatus,
        boolean emailVerified,
        Set<RoleName> roles,
        boolean mfaEnabled,
        boolean verificationLocked,
        Instant createdAt,
        Instant lastLoginAt,
        Instant statusChangedAt) {
}
