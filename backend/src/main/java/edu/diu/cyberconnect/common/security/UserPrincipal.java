package edu.diu.cyberconnect.common.security;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Session principal. Roles/authorities are fixed at login (sessions are revoked when privileges change); account
 * status and email verification are re-read from the database on every request by {@link AccountAccessFilter}.
 */
public final class UserPrincipal implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String fullName;
    private final Set<RoleName> roles;
    private final boolean mfaEnabled;

    public UserPrincipal(UUID id, String email, String fullName, Set<RoleName> roles, boolean mfaEnabled) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.roles = Collections.unmodifiableSet(roles);
        this.mfaEnabled = mfaEnabled;
    }

    /** Used by Spring Session's principal-name index so all sessions of a user can be found and revoked. */
    @Override
    public String getName() {
        return id.toString();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<RoleName> getRoles() {
        return roles;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public boolean isPrivileged() {
        return roles.stream().anyMatch(RoleName::isPrivileged);
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    /** Fresh view of the account for the current request, populated by {@link AccountAccessFilter}. */
    public record AccountSnapshot(UUID id, AccountStatus status, boolean emailVerified, boolean verificationLocked,
            boolean mfaEnabled, Set<RoleName> roles) {
    }
}
