package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.mail.MailMessage;
import edu.diu.cyberconnect.common.mail.MailSender;
import edu.diu.cyberconnect.common.realtime.RealtimePublisher;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers committed notifications: realtime push to the member's sessions and, when enabled, an email. */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final RealtimePublisher realtime;
    private final MailSender mail;
    private final UserAccounts accounts;
    private final AppProperties app;

    public NotificationDispatcher(RealtimePublisher realtime, MailSender mail, UserAccounts accounts, AppProperties app) {
        this.realtime = realtime;
        this.mail = mail;
        this.accounts = accounts;
        this.app = app;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationCreated event) {
        if (event.notificationId() != null) {
            realtime.toUser(event.userId(), RealtimePublisher.NOTIFICATIONS, Map.of(
                    "id", event.notificationId().toString(),
                    "type", event.type().name(),
                    "title", event.title(),
                    "body", event.body() == null ? "" : event.body(),
                    "link", event.link() == null ? "" : event.link()));
        }
        if (event.email()) {
            try {
                String to = accounts.get(event.userId()).email();
                String link = event.link() == null ? app.baseUrl() : app.baseUrl() + event.link();
                mail.send(new MailMessage(to, app.name() + ": " + event.title(),
                        (event.body() == null ? "" : event.body() + "\n\n") + "Open " + app.name() + ": " + link
                                + "\n\nYou can change which emails you receive under Settings → Notifications."));
            } catch (RuntimeException e) {
                log.warn("Notification email for type {} not sent: {}", event.type(), e.getMessage());
            }
        }
    }
}
