package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.crypto.AesGcmEncryptor;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.identity.domain.MfaCredential;
import edu.diu.cyberconnect.identity.domain.MfaRecoveryCode;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.MfaCredentialRepository;
import edu.diu.cyberconnect.identity.infrastructure.MfaRecoveryCodeRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP multi-factor authentication with encrypted secrets, hashed single-use recovery codes and step-up. */
@Service
@Transactional
public class MfaService {

    public record Enrolment(String secret, String otpauthUri) {
    }

    public record RecoveryCodes(List<String> recoveryCodes) {
    }

    static final String PENDING_SECRET = "MFA_PENDING_SECRET";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaCredentialRepository credentials;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final UserRepository users;
    private final AesGcmEncryptor encryptor;
    private final PasswordEncoder passwordEncoder;
    private final StepUpService stepUp;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final RateLimiter rateLimiter;
    private final AppProperties app;

    public MfaService(MfaCredentialRepository credentials, MfaRecoveryCodeRepository recoveryCodes,
            UserRepository users, AesGcmEncryptor encryptor, PasswordEncoder passwordEncoder, StepUpService stepUp,
            AuditService audit, AuditContext auditContext, RateLimiter rateLimiter, AppProperties app) {
        this.credentials = credentials;
        this.recoveryCodes = recoveryCodes;
        this.users = users;
        this.encryptor = encryptor;
        this.passwordEncoder = passwordEncoder;
        this.stepUp = stepUp;
        this.audit = audit;
        this.auditContext = auditContext;
        this.rateLimiter = rateLimiter;
        this.app = app;
    }

    public Enrolment beginEnrolment(UUID userId, HttpSession session) {
        if (credentials.existsById(userId)) {
            throw new ConflictException("MFA_ALREADY_ENABLED", "Multi-factor authentication is already enabled.");
        }
        User user = load(userId);
        String secret = TotpGenerator.base32Encode(TotpGenerator.newSecret());
        session.setAttribute(PENDING_SECRET, secret);
        String issuer = app.name();
        String label = URLEncoder.encode(issuer + ":" + user.getEmail(), StandardCharsets.UTF_8).replace("+", "%20");
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=6&period=30";
        return new Enrolment(secret, uri);
    }

    public RecoveryCodes confirmEnrolment(UUID userId, String code, HttpSession session) {
        Object pending = session.getAttribute(PENDING_SECRET);
        if (!(pending instanceof String secretBase32)) {
            throw new BadRequestException("MFA_ENROLMENT_NOT_STARTED", "Start MFA setup first.");
        }
        byte[] secret = TotpGenerator.base32Decode(secretBase32);
        long step = TotpGenerator.matchingStep(secret, normalize(code), 1);
        if (step < 0) {
            throw new BadRequestException("MFA_CODE_INVALID", "The code is not valid. Check your authenticator app.");
        }
        byte[] encrypted = encryptor.encrypt(secret, userId.toString().getBytes(StandardCharsets.UTF_8));
        MfaCredential credential = new MfaCredential(userId, Base64.getEncoder().encodeToString(encrypted),
                encryptor.keyId());
        credential.consumeStep(step);
        credentials.save(credential);
        session.removeAttribute(PENDING_SECRET);
        List<String> codes = regenerate(userId);
        stepUp.markVerified(session);
        audit.record(auditContext.entry(AuditActions.MFA_ENABLED, AuditActions.TARGET_USER, userId, Map.of()));
        return new RecoveryCodes(codes);
    }

    /** Verifies a TOTP code (with replay protection) or consumes a recovery code. */
    public boolean verify(UUID userId, String code) {
        MfaCredential credential = credentials.findById(userId).orElse(null);
        if (credential == null || code == null) {
            return false;
        }
        String normalized = normalize(code);
        if (normalized.matches("\\d{6}")) {
            byte[] secret = encryptor.decrypt(Base64.getDecoder().decode(credential.getTotpSecretEnc()),
                    userId.toString().getBytes(StandardCharsets.UTF_8));
            long step = TotpGenerator.matchingStep(secret, normalized, 1);
            return step >= 0 && credential.consumeStep(step);
        }
        String recovery = normalized.toUpperCase().replace("-", "");
        if (recovery.length() != 8) {
            return false;
        }
        for (MfaRecoveryCode candidate : recoveryCodes.findByUserIdAndUsedAtIsNull(userId)) {
            if (passwordEncoder.matches(recovery, candidate.getCodeHash())) {
                candidate.markUsed();
                audit.record(auditContext.entryAs(userId, AuditActions.MFA_RECOVERY_CODE_USED, AuditActions.TARGET_USER,
                        userId, Map.of("remaining", recoveryCodes.countByUserIdAndUsedAtIsNull(userId) - 1)));
                return true;
            }
        }
        return false;
    }

    public Instant stepUp(UUID userId, String code, HttpSession session) {
        rateLimiter.check("mfa-step-up", userId.toString(), 6, Duration.ofMinutes(10));
        if (!credentials.existsById(userId)) {
            throw new BadRequestException("MFA_NOT_ENABLED", "Multi-factor authentication is not enabled.");
        }
        if (!verify(userId, code)) {
            throw new BadRequestException("MFA_CODE_INVALID", "The code is not valid.");
        }
        stepUp.markVerified(session);
        audit.record(auditContext.entry(AuditActions.MFA_STEP_UP, AuditActions.TARGET_USER, userId, Map.of()));
        return stepUp.validUntil(session).orElseThrow();
    }

    public RecoveryCodes regenerateRecoveryCodes(UUID userId, String code) {
        rateLimiter.check("mfa-recovery-regen", userId.toString(), 3, Duration.ofHours(1));
        if (!verify(userId, code)) {
            throw new BadRequestException("MFA_CODE_INVALID", "The code is not valid.");
        }
        List<String> codes = regenerate(userId);
        audit.record(auditContext.entry(AuditActions.MFA_RECOVERY_CODES_REGENERATED, AuditActions.TARGET_USER, userId,
                Map.of()));
        return new RecoveryCodes(codes);
    }

    public void disable(UUID userId, String code) {
        User user = load(userId);
        if (user.roleNames().stream().anyMatch(RoleName::isPrivileged)) {
            throw new ForbiddenException("MFA_REQUIRED_FOR_ROLE",
                    "Moderators and administrators must keep multi-factor authentication enabled.");
        }
        if (!verify(userId, code)) {
            throw new BadRequestException("MFA_CODE_INVALID", "The code is not valid.");
        }
        credentials.deleteById(userId);
        recoveryCodes.deleteByUserId(userId);
        audit.record(auditContext.entry(AuditActions.MFA_DISABLED, AuditActions.TARGET_USER, userId, Map.of()));
    }

    public boolean isEnabled(UUID userId) {
        return credentials.existsById(userId);
    }

    private List<String> regenerate(UUID userId) {
        recoveryCodes.deleteByUserId(userId);
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            recoveryCodes.save(new MfaRecoveryCode(userId, passwordEncoder.encode(code.toString())));
            raw.add(code.substring(0, 4) + "-" + code.substring(4));
        }
        return raw;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().replace(" ", "");
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found."));
    }
}
