package edu.diu.cyberconnect.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A direct conversation between two members; {@code directKey} orders the pair so there is exactly one. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id;

    @Column(name = "direct_key", nullable = false, unique = true)
    private String directKey;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "last_message_sender")
    private UUID lastMessageSender;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public static String directKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public static Conversation direct(UUID a, UUID b) {
        Conversation c = new Conversation();
        c.id = UUID.randomUUID();
        c.directKey = directKey(a, b);
        c.createdAt = Instant.now();
        return c;
    }

    public void recordMessage(UUID senderId, String preview, Instant at) {
        this.lastMessageAt = at;
        this.lastMessagePreview = preview;
        this.lastMessageSender = senderId;
    }

    public UUID getId() {
        return id;
    }

    public String getDirectKey() {
        return directKey;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public UUID getLastMessageSender() {
        return lastMessageSender;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
