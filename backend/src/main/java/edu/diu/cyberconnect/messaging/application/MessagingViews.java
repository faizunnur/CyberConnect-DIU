package edu.diu.cyberconnect.messaging.application;

import edu.diu.cyberconnect.common.model.ConversationState;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

public final class MessagingViews {

    private MessagingViews() {
    }

    /** One conversation from the viewer's side: {@code state} is the viewer's, {@code otherState} the counterpart's. */
    public record ConversationView(UUID id, MemberCard other, ConversationState state, ConversationState otherState,
            String lastMessagePreview, Instant lastMessageAt, boolean lastMessageMine, long unreadCount,
            boolean canSend, Instant createdAt) {
    }

    public record MessageView(UUID id, UUID conversationId, UUID senderId, boolean mine, String body, boolean deleted,
            Instant createdAt) {
    }

    /** What a report carries: the message text and sender at report time; never the rest of the thread (D-14). */
    public record MessageSnapshot(UUID messageId, UUID conversationId, UUID senderId, String body, Instant createdAt) {
    }
}
