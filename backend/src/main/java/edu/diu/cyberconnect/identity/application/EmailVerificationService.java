package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.security.TokenHashing;
import edu.diu.cyberconnect.identity.domain.EmailVerificationToken;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.EmailVerificationTokenRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository users;
    private final MailSender mail;
    private final AppProperties app;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;

    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository users, MailSender mail,
            AppProperties app, AuditService audit, AuditContext auditContext, RateLimiter rateLimiter) {
        this.tokens = tokens;
        this.users = users;
        this.mail = mail;
        this.app = app;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
    }

    public void issue(User user) {
        tokens.deleteByUserId(user.getId());
        String raw = TokenHashing.newToken();
        tokens.save(EmailVerificationToken.issue(user.getId(), TokenHashing.sha256(raw), TTL));
        String link = app.baseUrl() + "/verify-email?token=" + raw;
        mail.send(new MailMessage(user.getEmail(), "Verify your email for " + app.name(), """
                Hello %s,

                Confirm your email address to continue your CyberConnect DIU registration:

                %s

                The link is valid for 24 hours. If you did not create an account, ignore this message.
                """.formatted(user.getFullName(), link)));
    }

    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("TOKEN_INVALID", "The verification link is not valid.");
        }
        EmailVerificationToken token = tokens.findByTokenHash(TokenHashing.sha256(rawToken.trim()))
                .orElseThrow(() -> new BadRequestException("TOKEN_INVALID", "The verification link is not valid."));
        if (!token.isUsable()) {
            throw new BadRequestException("TOKEN_EXPIRED", "The verification link has expired or was already used.");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
        token.markUsed();
        user.markEmailVerified();
        audit.record(auditContext.entryAs(user.getId(), AuditActions.EMAIL_VERIFIED, AuditActions.TARGET_USER,
                user.getId(), Map.of()));
    }

    public void resend(UUID userId) {
        rateLimiter.check("resend-verification", userId.toString(), 3, Duration.ofHours(1));
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
        if (user.isEmailVerified()) {
            return;
        }
        issue(user);
    }
}
