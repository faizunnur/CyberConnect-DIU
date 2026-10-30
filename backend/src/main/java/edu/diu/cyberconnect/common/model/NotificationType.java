package edu.diu.cyberconnect.common.model;

/** Notification kinds; each has in-app/email preferences (see NotificationDefaults). */
public enum NotificationType {
    CONNECTION_REQUEST,
    CONNECTION_ACCEPTED,
    MENTORSHIP_REQUEST,
    MENTORSHIP_RESPONSE,
    POST_COMMENT,
    COMMENT_REPLY,
    APPLICATION_RECEIVED,
    APPLICATION_DECISION,
    RESEARCH_APPLICATION,
    RESEARCH_DECISION,
    MESSAGE_REQUEST,
    NEW_MESSAGE,
    VERIFICATION_DECISION,
    CONTENT_REMOVED,
    IMPACT_PROPOSED,
    IMPACT_RESOLVED,
    ACCOUNT_NOTICE
}
