package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.BootstrapProperties;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.RoleRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first SUPER_ADMIN without any manual database edit: the configured email is promoted when it
 * registers (or at start-up if it already exists) as long as no SUPER_ADMIN exists. Audited as a system action.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public SuperAdminBootstrap(BootstrapProperties properties, UserRepository users, RoleRepository roles,
            AuditService audit, TransactionTemplate transactions) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        transactions.executeWithoutResult(tx -> users.findByEmail(User.normalizeEmail(properties.superAdminEmail()))
                .ifPresent(this::promoteIfConfigured));
    }

    /** Must be called inside a transaction. */
    public void promoteIfConfigured(User user) {
        if (!properties.isConfigured()
                || !user.getEmail().equalsIgnoreCase(properties.superAdminEmail().trim())
                || users.countByRole(RoleName.SUPER_ADMIN) > 0) {
            return;
        }
        for (RoleName role : List.of(RoleName.MODERATOR, RoleName.ADMIN, RoleName.SUPER_ADMIN)) {
            roles.findByName(role).ifPresent(r -> user.grantRole(r, null));
        }
        if (user.getAccountStatus() != AccountStatus.VERIFIED && user.getAccountStatus().canTransitionTo(AccountStatus.VERIFIED)) {
            user.changeStatus(AccountStatus.VERIFIED);
        }
        audit.record(AuditEntry.system(AuditActions.SUPER_ADMIN_BOOTSTRAPPED, AuditActions.TARGET_USER, user.getId(),
                Map.of("email", user.getEmail())));
        log.warn("Bootstrapped SUPER_ADMIN for {}. Remove app.bootstrap.super-admin-email once MFA is enrolled.",
                user.getEmail());
    }
}
