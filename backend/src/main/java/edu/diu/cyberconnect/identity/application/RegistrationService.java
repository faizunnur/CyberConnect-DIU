package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.config.RateLimitProperties;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.identity.domain.Role;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.RoleRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public registration. The response is identical whether or not the email already exists (an informational mail is
 * sent to existing accounts) so the endpoint cannot be used to enumerate members.
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AffiliationService affiliations;
    private final EmailVerificationService emailVerification;
    private final MailSender mail;
    private final AppProperties app;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;
    private final SuperAdminBootstrap bootstrap;
    private final RateLimitProperties limits;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            AffiliationService affiliations, EmailVerificationService emailVerification, MailSender mail,
            AppProperties app, AuditService audit, AuditContext auditContext, RateLimiter rateLimiter,
            SuperAdminBootstrap bootstrap, RateLimitProperties limits) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.affiliations = affiliations;
        this.emailVerification = emailVerification;
        this.mail = mail;
        this.app = app;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
        this.bootstrap = bootstrap;
        this.limits = limits;
    }

    @Transactional
    public void register(RegisterCommand command, String clientIp) {
        rateLimiter.check("register", clientIp == null ? "unknown" : clientIp, limits.registerPerHourPerIp(), Duration.ofHours(1));
        if (!command.privacyAccepted()) {
            throw new BadRequestException("PRIVACY_NOT_ACCEPTED", "You must accept the privacy notice.");
        }
        String email = User.normalizeEmail(command.email());
        PasswordPolicy.validate(command.password(), email);

        if (users.existsByEmail(email)) {
            mail.send(new MailMessage(email, "Your " + app.name() + " account", """
                    Someone tried to register a CyberConnect DIU account with this email address, which already exists.

                    If this was you, sign in at %s/sign-in or reset your password at %s/forgot-password.
                    If it was not you, no action is needed.
                    """.formatted(app.baseUrl(), app.baseUrl())));
            return;
        }

        Role member = roles.findByName(RoleName.MEMBER)
                .orElseThrow(() -> new IllegalStateException("MEMBER role is not seeded"));
        User user = users.save(User.register(email, passwordEncoder.encode(command.password()), command.fullName(), member));
        affiliations.createPrimary(user.getId(), command.affiliationType(), command.details());
        emailVerification.issue(user);
        audit.record(auditContext.entryAs(user.getId(), AuditActions.USER_REGISTERED, AuditActions.TARGET_USER,
                user.getId(), Map.of("affiliation", command.affiliationType().name())));
        bootstrap.promoteIfConfigured(user);
    }
}
