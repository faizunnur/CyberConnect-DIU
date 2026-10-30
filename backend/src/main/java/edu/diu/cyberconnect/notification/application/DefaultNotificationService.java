package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.notification.domain.Notification;
import edu.diu.cyberconnect.notification.domain.NotificationPreference;
import edu.diu.cyberconnect.notification.infrastructure.NotificationPreferenceRepository;
import edu.diu.cyberconnect.notification.infrastructure.NotificationRepository;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultNotificationService implements NotificationService {

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final ProfileService profiles;
    private final ApplicationEventPublisher events;

    public DefaultNotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
            ProfileService profiles, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.profiles = profiles;
        this.events = events;
    }

    @Override
    public void notify(UUID userId, NotificationType type, String title, String body, String link, UUID actorId) {
        PreferenceView pref = preferenceFor(userId, type);
        boolean inApp = pref.inApp() || !NotificationDefaults.optional(type);
        if (!inApp && !pref.email()) {
            return;
        }
        Notification n = null;
        if (inApp) {
            n = notifications.save(Notification.create(userId, type, title, body, link, actorId));
        }
        events.publishEvent(new NotificationCreated(n == null ? null : n.getId(), userId, type, title, body, link, pref.email()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Set<UUID> actorIds = page.getContent().stream().map(Notification::getActorId).filter(a -> a != null)
                .collect(Collectors.toSet());
        Map<UUID, MemberCard> actors = actorIds.isEmpty() ? Map.of() : profiles.cards(userId, actorIds);
        return page.map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getActorId() == null ? null : actors.get(n.getActorId()), n.getReadAt(), n.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found."));
        n.markRead();
    }

    @Override
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreferenceView> preferences(UUID userId) {
        Map<NotificationType, NotificationPreference> stored = preferences.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getId().getType(), p -> p, (a, b) -> a, () -> new EnumMap<>(NotificationType.class)));
        List<PreferenceView> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            NotificationPreference p = stored.get(type);
            result.add(p == null
                    ? new PreferenceView(type, NotificationDefaults.inApp(type), NotificationDefaults.email(type))
                    : new PreferenceView(type, p.isInApp(), p.isEmail()));
        }
        return result;
    }

    @Override
    public List<PreferenceView> updatePreferences(UUID userId, List<PreferenceView> updates) {
        for (PreferenceView update : updates) {
            if (update.type() == null) {
                continue;
            }
            boolean inApp = update.inApp() || !NotificationDefaults.optional(update.type());
            NotificationPreference.Key key = new NotificationPreference.Key(userId, update.type());
            preferences.findById(key).ifPresentOrElse(p -> p.update(inApp, update.email()),
                    () -> preferences.save(NotificationPreference.of(userId, update.type(), inApp, update.email())));
        }
        return preferences(userId);
    }

    private PreferenceView preferenceFor(UUID userId, NotificationType type) {
        return preferences.findById(new NotificationPreference.Key(userId, type))
                .map(p -> new PreferenceView(type, p.isInApp(), p.isEmail()))
                .orElseGet(() -> new PreferenceView(type, NotificationDefaults.inApp(type), NotificationDefaults.email(type)));
    }
}
