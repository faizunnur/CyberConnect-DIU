package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the identity module for other modules (verification, moderation, admin). Every mutation records an
 * audit entry and revokes the affected user's sessions where the lifecycle rules require it.
 */
public interface UserAccounts {

    UserSummary get(UUID userId);

    Optional<UserSummary> findByEmail(String email);

    Page<UserSummary> search(String query, AccountStatus status, RoleName role, Pageable pageable);

    /** Validated state-machine transition. {@code actorId} may be null for system actions. */
    void changeStatus(UUID userId, AccountStatus target, UUID actorId, String reason);

    void lockVerification(UUID userId, UUID actorId, String reason);

    void unlockVerification(UUID userId, UUID actorId, String reason);

    /** Replaces the privileged roles of a user. MEMBER is always kept. Enforces the SUPER_ADMIN safeguards. */
    void replaceRoles(UUID userId, Set<RoleName> roles, UUID actorId);

    long countWithRole(RoleName role);

    /** Terminates every session of the user (status change, privilege change, password change). */
    void revokeSessions(UUID userId);
}
