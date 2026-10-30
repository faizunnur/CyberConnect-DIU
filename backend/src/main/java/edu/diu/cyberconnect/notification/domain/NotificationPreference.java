package edu.diu.cyberconnect.notification.domain;

import edu.diu.cyberconnect.common.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @EmbeddedId
    private Key id;

    @Column(name = "in_app", nullable = false)
    private boolean inApp;

    @Column(nullable = false)
    private boolean email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public static NotificationPreference of(UUID userId, NotificationType type, boolean inApp, boolean email) {
        NotificationPreference p = new NotificationPreference();
        p.id = new Key(userId, type);
        p.inApp = inApp;
        p.email = email;
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(boolean inApp, boolean email) {
        this.inApp = inApp;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public Key getId() {
        return id;
    }

    public boolean isInApp() {
        return inApp;
    }

    public boolean isEmail() {
        return email;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private NotificationType type;

        protected Key() {
        }

        public Key(UUID userId, NotificationType type) {
            this.userId = userId;
            this.type = type;
        }

        public UUID getUserId() {
            return userId;
        }

        public NotificationType getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && userId.equals(k.userId) && type == k.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, type);
        }
    }
}
