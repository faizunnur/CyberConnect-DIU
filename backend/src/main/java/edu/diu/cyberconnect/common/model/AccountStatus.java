package edu.diu.cyberconnect.common.model;

import java.util.EnumSet;
import java.util.Set;

/** Account lifecycle (ADR-0006). Transitions are validated by {@link #canTransitionTo(AccountStatus)}. */
public enum AccountStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    SUSPENDED,
    DEACTIVATED;

    public Set<AccountStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(VERIFIED, REJECTED, DEACTIVATED);
            case REJECTED -> EnumSet.of(PENDING, VERIFIED, DEACTIVATED);
            case VERIFIED -> EnumSet.of(SUSPENDED, DEACTIVATED);
            case SUSPENDED -> EnumSet.of(VERIFIED, DEACTIVATED);
            case DEACTIVATED -> EnumSet.of(VERIFIED, PENDING);
        };
    }

    public boolean canTransitionTo(AccountStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Only verified accounts may use community features. */
    public boolean hasCommunityAccess() {
        return this == VERIFIED;
    }
}
