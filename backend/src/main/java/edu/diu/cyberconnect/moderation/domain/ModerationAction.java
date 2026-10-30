package edu.diu.cyberconnect.moderation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of a moderation decision about a member. */
@Entity
@Table(name = "moderation_actions")
public class ModerationAction {

    public enum Type { SUSPEND, UNSUSPEND, DEACTIVATE, REACTIVATE, WARN, REOPEN_VERIFICATION }

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type action;

    @Column(nullable = false)
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationAction() {
    }

    public ModerationAction(UUID actorId, UUID targetUserId, Type action, String reason, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.targetUserId = targetUserId;
        this.action = action;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public Type getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
