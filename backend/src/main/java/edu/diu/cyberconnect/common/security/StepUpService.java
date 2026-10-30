package edu.diu.cyberconnect.common.security;

import edu.diu.cyberconnect.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Step-up MFA: sensitive operations require a TOTP verification within the last {@link #VALIDITY}. */
@Component
public class StepUpService {

    public static final Duration VALIDITY = Duration.ofMinutes(10);
    static final String ATTRIBUTE = "MFA_STEP_UP_AT";

    public void markVerified(HttpSession session) {
        session.setAttribute(ATTRIBUTE, Instant.now());
    }

    public Optional<Instant> validUntil(HttpSession session) {
        Object at = session == null ? null : session.getAttribute(ATTRIBUTE);
        if (at instanceof Instant instant && instant.plus(VALIDITY).isAfter(Instant.now())) {
            return Optional.of(instant.plus(VALIDITY));
        }
        return Optional.empty();
    }

    public void requireFresh(HttpSession session) {
        if (validUntil(session).isEmpty()) {
            throw new ForbiddenException("MFA_STEP_UP_REQUIRED",
                    "Confirm your identity with your authenticator app to continue.");
        }
    }
}
