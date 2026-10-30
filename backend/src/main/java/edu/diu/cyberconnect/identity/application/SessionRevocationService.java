package edu.diu.cyberconnect.identity.application;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Deletes every Redis session of a user. Relies on Spring Session's principal-name index (ADR-0002). */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public int revokeAll(UUID userId) {
        return revokeAllExcept(userId, null);
    }

    public int revokeAllExcept(UUID userId, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(userId.toString());
        int revoked = 0;
        for (String sessionId : found.keySet()) {
            if (keepSessionId != null && keepSessionId.equals(sessionId)) {
                continue;
            }
            sessions.deleteById(sessionId);
            revoked++;
        }
        log.info("Revoked {} session(s) for user {}", revoked, userId);
        return revoked;
    }
}
