package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.model.NotificationType;
import java.util.UUID;

/** Published inside the transaction; delivered (push + email) only after it commits. */
public record NotificationCreated(UUID notificationId, UUID userId, NotificationType type, String title, String body,
        String link, boolean email) {
}
