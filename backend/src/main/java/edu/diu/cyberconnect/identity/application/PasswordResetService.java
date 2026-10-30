package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.config.RateLimitProperties;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.security.TokenHashing;
import edu.diu.cyberconnect.identity.domain.PasswordResetToken;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.PasswordResetTokenRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mail;
    private final AppProperties app;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;
    private final SessionRevocationService sessions;
    private final RateLimitProperties limits;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
            PasswordEncoder passwordEncoder, MailSender mail, AppProperties app, AuditService audit,
            AuditContext auditContext, RateLimiter rateLimiter, SessionRevocationService sessions,
            RateLimitProperties limits) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.app = app;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.limits = limits;
    }

    /** Always succeeds from the caller's perspective; only existing accounts receive a mail. */
    public void requestReset(String email, String clientIp) {
        rateLimiter.check("forgot-password-ip", clientIp == null ? "unknown" : clientIp, limits.forgotPasswordPerFifteenMinutesPerIp(), Duration.ofMinutes(15));
        String normalized = User.normalizeEmail(email);
        rateLimiter.check("forgot-password-email", normalized, 3, Duration.ofHours(1));
        users.findByEmail(normalized).ifPresent(user -> {
            tokens.deleteByUserId(user.getId());
            String raw = TokenHashing.newToken();
            tokens.save(PasswordResetToken.issue(user.getId(), TokenHashing.sha256(raw), TTL));
            mail.send(new MailMessage(user.getEmail(), "Reset your " + app.name() + " password", """
                    Hello %s,

                    Use the link below to choose a new password. It is valid for 30 minutes:

                    %s/reset-password?token=%s

                    If you did not request this, ignore this message; your password stays unchanged.
                    """.formatted(user.getFullName(), app.baseUrl(), raw)));
            audit.record(auditContext.entryAs(user.getId(), AuditActions.PASSWORD_RESET_REQUESTED,
                    AuditActions.TARGET_USER, user.getId(), Map.of()));
        });
    }

    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("TOKEN_INVALID", "The reset link is not valid.");
        }
        PasswordResetToken token = tokens.findByTokenHash(TokenHashing.sha256(rawToken.trim()))
                .orElseThrow(() -> new BadRequestException("TOKEN_INVALID", "The reset link is not valid."));
        if (!token.isUsable()) {
            throw new BadRequestException("TOKEN_EXPIRED", "The reset link has expired or was already used.");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed();
        sessions.revokeAll(user.getId());
        audit.record(auditContext.entryAs(user.getId(), AuditActions.PASSWORD_RESET, AuditActions.TARGET_USER,
                user.getId(), Map.of()));
        mail.send(new MailMessage(user.getEmail(), "Your " + app.name() + " password was changed",
                "Your password was just changed. If this was not you, contact the moderators immediately."));
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("CURRENT_PASSWORD_INVALID", "The current password is incorrect.");
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        sessions.revokeAll(user.getId());
        audit.record(auditContext.entry(AuditActions.PASSWORD_CHANGED, AuditActions.TARGET_USER, userId, Map.of()));
    }
}
