package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.model.NotificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the notification module. {@link #notify} stores an in-app record (when the member's preference
 * allows) and, after the surrounding transaction commits, pushes it over the WebSocket and emails it when enabled.
 */
public interface NotificationService {

    void notify(UUID userId, NotificationType type, String title, String body, String link, UUID actorId);

    Page<NotificationView> list(UUID userId, boolean unreadOnly, Pageable pageable);

    long unreadCount(UUID userId);

    void markRead(UUID userId, UUID notificationId);

    int markAllRead(UUID userId);

    List<PreferenceView> preferences(UUID userId);

    List<PreferenceView> updatePreferences(UUID userId, List<PreferenceView> preferences);
}
