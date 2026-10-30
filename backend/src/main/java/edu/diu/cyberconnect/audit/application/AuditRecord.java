package edu.diu.cyberconnect.audit.application;

import java.time.Instant;
import java.util.UUID;

public record AuditRecord(
        UUID id,
        UUID actorId,
        String actorRoles,
        String action,
        String targetType,
        UUID targetId,
        String outcome,
        String metadata,
        String ip,
        Instant createdAt) {
}
