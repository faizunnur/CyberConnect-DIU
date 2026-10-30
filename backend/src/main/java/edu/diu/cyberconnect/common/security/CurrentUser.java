package edu.diu.cyberconnect.common.security;

import edu.diu.cyberconnect.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Access to the authenticated principal and the per-request account snapshot. */
public final class CurrentUser {

    public static final String SNAPSHOT_ATTRIBUTE = CurrentUser.class.getName() + ".SNAPSHOT";

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static UserPrincipal require() {
        return get().orElseThrow(() -> new UnauthorizedException("AUTHENTICATION_REQUIRED", "Authentication is required."));
    }

    public static Optional<UserPrincipal.AccountSnapshot> snapshot() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object snapshot = request.getAttribute(SNAPSHOT_ATTRIBUTE);
            if (snapshot instanceof UserPrincipal.AccountSnapshot s) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public static Optional<HttpServletRequest> request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }
}
