package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.model.NotificationType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Default delivery per type: everything in-app; email only for the decisions people wait for. Verification decisions
 * are not emailed here because the verification module always sends its own transactional email for them.
 */
public final class NotificationDefaults {

    private static final Set<NotificationType> EMAIL_BY_DEFAULT = EnumSet.of(
            NotificationType.APPLICATION_DECISION,
            NotificationType.RESEARCH_DECISION,
            NotificationType.MENTORSHIP_REQUEST,
            NotificationType.MENTORSHIP_RESPONSE,
            NotificationType.MESSAGE_REQUEST,
            NotificationType.ACCOUNT_NOTICE);

    private NotificationDefaults() {
    }

    public static boolean inApp(NotificationType type) {
        return true;
    }

    public static boolean email(NotificationType type) {
        return EMAIL_BY_DEFAULT.contains(type);
    }

    /** Types the member may switch off entirely; account and moderation notices always reach the inbox. */
    public static boolean optional(NotificationType type) {
        return type != NotificationType.VERIFICATION_DECISION && type != NotificationType.CONTENT_REMOVED
                && type != NotificationType.ACCOUNT_NOTICE;
    }
}
