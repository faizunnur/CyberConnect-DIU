package edu.diu.cyberconnect.common.realtime;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompRealtimePublisher implements RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(StompRealtimePublisher.class);

    private final SimpMessagingTemplate template;

    public StompRealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    @Override
    public void toUser(UUID userId, String destination, Object payload) {
        try {
            template.convertAndSendToUser(userId.toString(), destination, payload);
        } catch (RuntimeException e) {
            // Realtime delivery is best effort; the record is already persisted and visible through the REST API.
            log.warn("Realtime push to {} failed: {}", destination, e.getMessage());
        }
    }
}
