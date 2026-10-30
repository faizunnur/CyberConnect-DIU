package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.config.RateLimitProperties;
import edu.diu.cyberconnect.common.exception.UnauthorizedException;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.security.Authorities;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import edu.diu.cyberconnect.identity.domain.User;
import edu.diu.cyberconnect.identity.infrastructure.MfaCredentialRepository;
import edu.diu.cyberconnect.identity.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Password login with uniform failures, rate limits, session fixation protection and an MFA second step. */
@Service
public class AuthenticationService {

    static final String PENDING_MFA_USER = "PENDING_MFA_USER_ID";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final SecurityContextRepository securityContextRepository;
    private final MfaCredentialRepository mfaCredentials;
    private final MfaService mfa;
    private final StepUpService stepUp;
    private final MeAssembler me;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final String dummyHash;
    private final RateLimitProperties limits;

    public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder, RateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository, MfaCredentialRepository mfaCredentials,
            MfaService mfa, StepUpService stepUp, MeAssembler me, AuditService audit, AuditContext auditContext,
            RateLimitProperties limits) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.securityContextRepository = securityContextRepository;
        this.mfaCredentials = mfaCredentials;
        this.mfa = mfa;
        this.stepUp = stepUp;
        this.me = me;
        this.audit = audit;
        this.auditContext = auditContext;
        this.limits = limits;
        // Hashing a dummy value keeps timing comparable for unknown emails.
        this.dummyHash = passwordEncoder.encode("placeholder-for-unknown-accounts");
    }

    @Transactional
    public LoginResult login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String normalized = User.normalizeEmail(email == null ? "" : email);
        rateLimiter.check("login-ip", ip, limits.loginPerTenMinutesPerIp(), Duration.ofMinutes(10));
        rateLimiter.check("login-email", normalized, limits.loginPerFifteenMinutesPerEmail(), Duration.ofMinutes(15));

        Optional<User> maybeUser = users.findByEmail(normalized);
        String hash = maybeUser.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (maybeUser.isEmpty() || !matches) {
            audit.recordDetached(auditContext.entry(AuditActions.LOGIN_FAILED, AuditActions.TARGET_USER,
                    maybeUser.map(User::getId).orElse(null), AuditEntry.Outcome.FAILURE,
                    Map.of("emailDomain", domainOf(normalized))));
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        User user = maybeUser.get();
        if (mfaCredentials.existsById(user.getId())) {
            HttpSession session = request.getSession(true);
            session.setAttribute(PENDING_MFA_USER, user.getId());
            return LoginResult.requiresMfa();
        }
        return establish(user, request, response);
    }

    @Transactional
    public LoginResult completeMfa(String code, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Object pending = session == null ? null : session.getAttribute(PENDING_MFA_USER);
        if (!(pending instanceof UUID userId)) {
            throw new UnauthorizedException("MFA_SESSION_MISSING", "Sign in with your password first.");
        }
        rateLimiter.check("login-mfa", userId.toString(), 6, Duration.ofMinutes(10));
        User user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Sign in again."));
        if (!mfa.verify(userId, code)) {
            audit.recordDetached(auditContext.entryAs(userId, AuditActions.LOGIN_MFA_FAILED, AuditActions.TARGET_USER,
                    userId, Map.of()));
            throw new UnauthorizedException("MFA_CODE_INVALID", "The code is not valid.");
        }
        session.removeAttribute(PENDING_MFA_USER);
        LoginResult result = establish(user, request, response);
        stepUp.markVerified(request.getSession());
        return LoginResult.authenticated(me.assemble(user, request.getSession()));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser.get().ifPresent(principal -> audit.recordDetached(
                auditContext.entry(AuditActions.LOGOUT, AuditActions.TARGET_USER, principal.getId(), Map.of())));
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private LoginResult establish(User user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(),
                user.roleNames(), mfaCredentials.existsById(user.getId()));
        List<SimpleGrantedAuthority> authorities = Authorities.forRoles(user.roleNames()).stream()
                .map(SimpleGrantedAuthority::new).toList();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getId().toString());

        user.recordLogin();
        audit.record(auditContext.entryAs(user.getId(), AuditActions.LOGIN_SUCCESS, AuditActions.TARGET_USER,
                user.getId(), Map.of()));
        return LoginResult.authenticated(me.assemble(user, session));
    }

    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "unknown" : email.substring(at + 1);
    }
}
