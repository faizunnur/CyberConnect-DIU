package edu.diu.cyberconnect.networking.domain;

import edu.diu.cyberconnect.common.model.ConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One live row per unordered pair of members (enforced by a partial unique index). */
@Entity
@Table(name = "connections")
public class Connection {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected Connection() {
    }

    public static Connection request(UUID requesterId, UUID addresseeId, String message) {
        Connection connection = new Connection();
        connection.id = UUID.randomUUID();
        connection.requesterId = requesterId;
        connection.addresseeId = addresseeId;
        connection.status = ConnectionStatus.PENDING;
        connection.message = message == null || message.isBlank() ? null : message.trim();
        connection.createdAt = Instant.now();
        return connection;
    }

    public void accept() {
        this.status = ConnectionStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public void decline() {
        this.status = ConnectionStatus.DECLINED;
        this.respondedAt = Instant.now();
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    public UUID otherParty(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getAddresseeId() {
        return addresseeId;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
