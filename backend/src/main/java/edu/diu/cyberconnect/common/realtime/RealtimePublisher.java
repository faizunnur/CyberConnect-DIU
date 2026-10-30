package edu.diu.cyberconnect.common.realtime;

import java.util.UUID;

/** Port for pushing events to a signed-in member's browser sessions (STOMP user queues). Fire-and-forget. */
public interface RealtimePublisher {

    String NOTIFICATIONS = "/queue/notifications";
    String MESSAGES = "/queue/messages";

    void toUser(UUID userId, String destination, Object payload);
}
