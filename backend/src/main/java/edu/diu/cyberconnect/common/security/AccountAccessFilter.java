package edu.diu.cyberconnect.common.security;

import edu.diu.cyberconnect.common.model.AccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces account state on every authenticated API request, independent of role checks:
 * non-verified accounts reach only their own onboarding endpoints; privileged areas additionally require MFA
 * enrolment. State is read fresh from the database, so approvals, rejections and email verification apply at once.
 */
public final class AccountAccessFilter extends OncePerRequestFilter {

    /** Reachable by any authenticated account regardless of status (own onboarding, status, appeals, MFA). */
    private static final List<String> ONBOARDING_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/mfa",
            "/api/v1/verification/",
            "/api/v1/me/affiliations",
            "/api/v1/account/");

    private static final List<String> PRIVILEGED_PREFIXES = List.of("/api/v1/moderation/", "/api/v1/admin/");

    private final AccountSnapshotProvider snapshotProvider;
    private final ProblemDetailWriter writer;

    public AccountAccessFilter(AccountSnapshotProvider snapshotProvider, ProblemDetailWriter writer) {
        this.snapshotProvider = snapshotProvider;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserPrincipal.AccountSnapshot> maybeSnapshot = snapshotProvider.snapshot(principal.getId());
        if (maybeSnapshot.isEmpty()) {
            // The account vanished underneath a live session: terminate it.
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            writer.write(request, response, HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Your session is no longer valid.");
            return;
        }
        UserPrincipal.AccountSnapshot snapshot = maybeSnapshot.get();
        request.setAttribute(CurrentUser.SNAPSHOT_ATTRIBUTE, snapshot);

        String path = request.getRequestURI();
        boolean onboardingPath = ONBOARDING_PREFIXES.stream().anyMatch(path::startsWith);
        boolean privilegedPath = PRIVILEGED_PREFIXES.stream().anyMatch(path::startsWith);

        if (!onboardingPath) {
            if (snapshot.status() != AccountStatus.VERIFIED) {
                writer.write(request, response, HttpStatus.FORBIDDEN, statusCode(snapshot.status()),
                        statusMessage(snapshot.status()));
                return;
            }
            if (!snapshot.emailVerified()) {
                writer.write(request, response, HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                        "Verify your email address to continue.");
                return;
            }
        }
        if (privilegedPath && !snapshot.mfaEnabled()) {
            writer.write(request, response, HttpStatus.FORBIDDEN, "MFA_ENROLLMENT_REQUIRED",
                    "Set up multi-factor authentication before using privileged features.");
            return;
        }
        chain.doFilter(request, response);
    }

    public static String statusCode(AccountStatus status) {
        return switch (status) {
            case PENDING -> "ACCOUNT_NOT_VERIFIED";
            case REJECTED -> "ACCOUNT_REJECTED";
            case SUSPENDED -> "ACCOUNT_SUSPENDED";
            case DEACTIVATED -> "ACCOUNT_DEACTIVATED";
            case VERIFIED -> "ACCOUNT_VERIFIED";
        };
    }

    public static String statusMessage(AccountStatus status) {
        return switch (status) {
            case PENDING -> "Your account is awaiting verification.";
            case REJECTED -> "Your application was not approved. You may resubmit or appeal where permitted.";
            case SUSPENDED -> "Your account is suspended.";
            case DEACTIVATED -> "Your account is deactivated.";
            case VERIFIED -> "Your account is verified.";
        };
    }
}
