package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

public record NotificationView(UUID id, NotificationType type, String title, String body, String link, MemberCard actor,
        Instant readAt, Instant createdAt) {
}
