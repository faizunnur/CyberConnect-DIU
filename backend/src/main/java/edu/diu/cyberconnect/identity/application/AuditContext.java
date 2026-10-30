package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds audit entries enriched with the current request (actor, roles, IP, user agent). */
@Component
public class AuditContext {

    public AuditEntry entry(String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        return entry(action, targetType, targetId, AuditEntry.Outcome.SUCCESS, metadata);
    }

    public AuditEntry entry(String action, String targetType, UUID targetId, AuditEntry.Outcome outcome,
            Map<String, Object> metadata) {
        UserPrincipal principal = CurrentUser.get().orElse(null);
        UUID actorId = principal == null ? null : principal.getId();
        Set<String> roles = principal == null ? Set.of()
                : principal.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
        HttpServletRequest request = CurrentUser.request().orElse(null);
        String ip = request == null ? null : request.getRemoteAddr();
        String userAgent = request == null ? null : truncate(request.getHeader("User-Agent"), 255);
        String requestId = request == null ? null : request.getHeader("X-Request-Id");
        return new AuditEntry(actorId, roles, action, targetType, targetId, outcome, metadata, ip, userAgent, requestId);
    }

    public AuditEntry entryAs(UUID actorId, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        AuditEntry base = entry(action, targetType, targetId, metadata);
        return new AuditEntry(actorId, base.actorRoles(), action, targetType, targetId, base.outcome(), metadata,
                base.ip(), base.userAgent(), base.requestId());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
