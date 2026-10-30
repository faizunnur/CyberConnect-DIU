package edu.diu.cyberconnect.audit.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What to record. {@code metadata} must never contain secrets, tokens, passwords or document contents.
 */
public record AuditEntry(
        UUID actorId,
        Set<String> actorRoles,
        String action,
        String targetType,
        UUID targetId,
        Outcome outcome,
        Map<String, Object> metadata,
        String ip,
        String userAgent,
        String requestId) {

    public enum Outcome { SUCCESS, FAILURE }

    public static AuditEntry system(String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        return new AuditEntry(null, Set.of(), action, targetType, targetId, Outcome.SUCCESS, metadata, null, null, null);
    }
}
