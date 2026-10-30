package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.security.AccountSnapshotProvider;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import edu.diu.cyberconnect.identity.domain.Role;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.MfaCredentialRepository;
import edu.diu.cyberconnect.identity.infrastructure.RoleRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultUserAccounts implements UserAccounts, AccountSnapshotProvider {

    private final UserRepository users;
    private final RoleRepository roles;
    private final MfaCredentialRepository mfaCredentials;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final SessionRevocationService sessions;

    public DefaultUserAccounts(UserRepository users, RoleRepository roles, MfaCredentialRepository mfaCredentials,
            AuditService audit, AuditContext auditContext, SessionRevocationService sessions) {
        this.users = users;
        this.roles = roles;
        this.mfaCredentials = mfaCredentials;
        this.audit = audit;
        this.auditContext = auditContext;
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummary get(UUID userId) {
        return toSummary(load(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findByEmail(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSummary> search(String query, AccountStatus status, RoleName role, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return users.search(q, status, role, pageable).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserPrincipal.AccountSnapshot> snapshot(UUID userId) {
        return users.findById(userId).map(user -> new UserPrincipal.AccountSnapshot(user.getId(),
                user.getAccountStatus(), user.isEmailVerified(), user.isVerificationLocked(),
                mfaCredentials.existsById(user.getId()), user.roleNames()));
    }

    @Override
    public void changeStatus(UUID userId, AccountStatus target, UUID actorId, String reason) {
        User user = load(userId);
        AccountStatus from = user.getAccountStatus();
        if (from == target) {
            return;
        }
        if (!from.canTransitionTo(target)) {
            throw new ConflictException("ILLEGAL_STATUS_TRANSITION",
                    "Cannot change account status from " + from + " to " + target + ".");
        }
        user.changeStatus(target);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("from", from.name());
        metadata.put("to", target.name());
        metadata.put("reason", reason);
        audit.record(auditContext.entryAs(actorId, AuditActions.ACCOUNT_STATUS_CHANGED, AuditActions.TARGET_USER, userId,
                metadata));
        // Sessions are revoked whenever a decision removes access (rejection, suspension, deactivation), so access
        // rights never outlive the decision. Approval and a self-initiated resubmission (REJECTED -> PENDING) keep
        // the current session: neither grants more access than the session already had.
        if (target == AccountStatus.REJECTED || target == AccountStatus.SUSPENDED || target == AccountStatus.DEACTIVATED) {
            revokeSessions(userId);
        }
    }

    @Override
    public void lockVerification(UUID userId, UUID actorId, String reason) {
        User user = load(userId);
        user.lockVerification();
        audit.record(auditContext.entryAs(actorId, AuditActions.VERIFICATION_LOCKED, AuditActions.TARGET_USER, userId,
                Map.of("reason", reason)));
    }

    @Override
    public void unlockVerification(UUID userId, UUID actorId, String reason) {
        User user = load(userId);
        user.unlockVerification();
        audit.record(auditContext.entryAs(actorId, AuditActions.VERIFICATION_UNLOCKED, AuditActions.TARGET_USER, userId,
                Map.of("reason", reason)));
    }

    @Override
    public void replaceRoles(UUID userId, Set<RoleName> requested, UUID actorId) {
        if (actorId != null && actorId.equals(userId)) {
            throw new ForbiddenException("SELF_ROLE_CHANGE", "You cannot change your own roles.");
        }
        User user = load(userId);
        Set<RoleName> target = EnumSet.of(RoleName.MEMBER);
        target.addAll(requested);
        Set<RoleName> current = user.roleNames();

        boolean removesSuperAdmin = current.contains(RoleName.SUPER_ADMIN) && !target.contains(RoleName.SUPER_ADMIN);
        if (removesSuperAdmin && users.countByRole(RoleName.SUPER_ADMIN) <= 1) {
            throw new ConflictException("LAST_SUPER_ADMIN", "The last SUPER_ADMIN cannot be removed or demoted.");
        }
        if (user.getAccountStatus() != AccountStatus.VERIFIED && target.stream().anyMatch(RoleName::isPrivileged)) {
            throw new BadRequestException("PRIVILEGED_ROLE_REQUIRES_VERIFIED",
                    "Privileged roles can only be granted to verified accounts.");
        }

        for (RoleName role : RoleName.values()) {
            if (role == RoleName.MEMBER) {
                continue;
            }
            if (target.contains(role) && !current.contains(role)) {
                Role entity = roles.findByName(role)
                        .orElseThrow(() -> new IllegalStateException("Role not seeded: " + role));
                user.grantRole(entity, actorId);
            } else if (!target.contains(role) && current.contains(role)) {
                user.revokeRole(role);
            }
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("from", current.stream().map(Enum::name).sorted().toList());
        metadata.put("to", target.stream().map(Enum::name).sorted().toList());
        audit.record(auditContext.entryAs(actorId, AuditActions.ROLES_CHANGED, AuditActions.TARGET_USER, userId, metadata));
        revokeSessions(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countWithRole(RoleName role) {
        return users.countByRole(role);
    }

    @Override
    public void revokeSessions(UUID userId) {
        int revoked = sessions.revokeAll(userId);
        if (revoked > 0) {
            audit.record(auditContext.entry(AuditActions.SESSIONS_REVOKED, AuditActions.TARGET_USER, userId,
                    Map.of("count", revoked)));
        }
    }

    UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getAccountStatus(),
                user.isEmailVerified(), user.roleNames(), mfaCredentials.existsById(user.getId()),
                user.isVerificationLocked(), user.getCreatedAt(), user.getLastLoginAt(), user.getStatusChangedAt());
    }

    private User load(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
    }
}
