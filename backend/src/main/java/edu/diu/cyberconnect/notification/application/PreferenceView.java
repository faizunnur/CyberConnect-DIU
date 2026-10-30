package edu.diu.cyberconnect.notification.application;

import edu.diu.cyberconnect.common.model.NotificationType;

public record PreferenceView(NotificationType type, boolean inApp, boolean email) {
}
