package edu.diu.cyberconnect.identity.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> roles = new HashSet<>();

    protected User() {
    }

    /** Registers a new account. Every account starts PENDING with the MEMBER role; nothing else is assignable here. */
    public static User register(String email, String passwordHash, String fullName, Role memberRole) {
        if (memberRole.getName() != RoleName.MEMBER) {
            throw new IllegalArgumentException("New accounts may only receive the MEMBER role");
        }
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.fullName = fullName.trim();
        user.accountStatus = AccountStatus.PENDING;
        Instant now = Instant.now();
        user.statusChangedAt = now;
        user.createdAt = now;
        user.updatedAt = now;
        user.roles.add(new UserRole(user, memberRole, null));
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void markEmailVerified() {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Instant.now();
            touch();
        }
    }

    public void changeStatus(AccountStatus target) {
        if (!accountStatus.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal account status transition " + accountStatus + " -> " + target);
        }
        accountStatus = target;
        statusChangedAt = Instant.now();
        if (target == AccountStatus.DEACTIVATED) {
            deactivatedAt = statusChangedAt;
        } else {
            deactivatedAt = null;
        }
        touch();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        touch();
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public void grantRole(Role role, UUID grantedBy) {
        if (!hasRole(role.getName())) {
            roles.add(new UserRole(this, role, grantedBy));
            touch();
        }
    }

    public void revokeRole(RoleName roleName) {
        if (roleName == RoleName.MEMBER) {
            throw new IllegalArgumentException("The MEMBER role cannot be revoked");
        }
        roles.removeIf(r -> r.getRole().getName() == roleName);
        touch();
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(r -> r.getRole().getName() == roleName);
    }

    public Set<RoleName> roleNames() {
        return roles.stream().map(r -> r.getRole().getName()).collect(Collectors.toUnmodifiableSet());
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<UserRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
