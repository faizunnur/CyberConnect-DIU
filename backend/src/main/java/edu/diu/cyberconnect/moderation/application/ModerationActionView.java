package edu.diu.cyberconnect.moderation.application;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionView(UUID id, UUID actorId, UUID targetUserId, String action, String reason,
        Instant expiresAt, Instant createdAt) {
}
