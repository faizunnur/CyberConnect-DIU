package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.notification.infrastructure.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Data minimisation: read notifications older than 90 days and unread ones older than a year are deleted nightly. */
@Component
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
    static final Duration READ_RETENTION = Duration.ofDays(90);
    static final Duration UNREAD_RETENTION = Duration.ofDays(365);

    private final NotificationRepository notifications;

    public NotificationRetentionJob(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "${app.time-zone:Asia/Dhaka}")
    @Transactional
    public int purge() {
        Instant now = Instant.now();
        int removed = notifications.deleteReadBefore(now.minus(READ_RETENTION)) + notifications.deleteUnreadBefore(now.minus(UNREAD_RETENTION));
        if (removed > 0) {
            log.info("Purged {} old notifications", removed);
        }
        return removed;
    }
}
