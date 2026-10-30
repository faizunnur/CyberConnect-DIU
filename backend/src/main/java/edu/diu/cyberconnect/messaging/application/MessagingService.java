package edu.diu.cyberconnect.messaging.application;

import edu.diu.cyberconnect.messaging.application.MessagingViews.ConversationView;
import edu.diu.cyberconnect.messaging.application.MessagingViews.MessageSnapshot;
import edu.diu.cyberconnect.messaging.application.MessagingViews.MessageView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Public API of the messaging module. Every call checks conversation membership server-side. */
public interface MessagingService {

    enum Box { INBOX, REQUESTS, ARCHIVED }

    /** Starts (or reuses) the direct conversation with {@code otherId} and sends the first message. */
    ConversationView start(UUID userId, UUID otherId, String body);

    Page<ConversationView> list(UUID userId, Box box, Pageable pageable);

    ConversationView get(UUID userId, UUID conversationId);

    /** Newest first; {@code beforeMessageId} pages backwards (keyset). */
    List<MessageView> history(UUID userId, UUID conversationId, UUID beforeMessageId, int size);

    MessageView send(UUID userId, UUID conversationId, String body);

    void markRead(UUID userId, UUID conversationId);

    ConversationView accept(UUID userId, UUID conversationId);

    ConversationView decline(UUID userId, UUID conversationId);

    ConversationView archive(UUID userId, UUID conversationId, boolean archived);

    void deleteOwnMessage(UUID userId, UUID messageId);

    long unreadConversations(UUID userId);

    /** For reports: the reporter must be a member of the message's conversation. */
    MessageSnapshot snapshot(UUID reporterId, UUID messageId);

    /** Moderator removal of a reported message; the moderator never reads the thread. */
    void removeByModeration(UUID moderatorId, UUID messageId, String reason);
}
