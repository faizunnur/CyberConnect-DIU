package edu.diu.cyberconnect.messaging.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ConversationState;
import edu.diu.cyberconnect.common.model.MessagingPolicy;
import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.realtime.RealtimePublisher;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.messaging.application.MessagingViews.ConversationView;
import edu.diu.cyberconnect.messaging.application.MessagingViews.MessageSnapshot;
import edu.diu.cyberconnect.messaging.application.MessagingViews.MessageView;
import edu.diu.cyberconnect.messaging.domain.Conversation;
import edu.diu.cyberconnect.messaging.domain.ConversationMember;
import edu.diu.cyberconnect.messaging.domain.Message;
import edu.diu.cyberconnect.messaging.infrastructure.ConversationMemberRepository;
import edu.diu.cyberconnect.messaging.infrastructure.ConversationRepository;
import edu.diu.cyberconnect.messaging.infrastructure.MessageRepository;
import edu.diu.cyberconnect.networking.application.NetworkingService;
import edu.diu.cyberconnect.notification.application.NotificationService;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class DefaultMessagingService implements MessagingService {

    public static final int MAX_BODY = 4000;
    static final int MESSAGES_PER_HOUR = 200;
    static final int NEW_CONVERSATIONS_PER_DAY = 30;
    static final String AUDIT_MESSAGE_REMOVED = "MESSAGE_REMOVED";

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final ProfileService profiles;
    private final NetworkingService networking;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;

    public DefaultMessagingService(ConversationRepository conversations, ConversationMemberRepository members,
            MessageRepository messages, ProfileService profiles, NetworkingService networking,
            NotificationService notifications, RealtimePublisher realtime, RateLimiter rateLimiter, AuditService audit,
            AuditContext auditContext) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.profiles = profiles;
        this.networking = networking;
        this.notifications = notifications;
        this.realtime = realtime;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
    }

    @Override
    public ConversationView start(UUID userId, UUID otherId, String rawBody) {
        if (userId.equals(otherId)) {
            throw new BadRequestException("SELF_MESSAGE", "You cannot message yourself.");
        }
        profiles.view(userId, otherId); // uniform 404 for unknown, non-verified or blocked members
        String body = sanitize(rawBody);
        Optional<Conversation> existing = conversations.findByDirectKey(Conversation.directKey(userId, otherId));
        Conversation conversation;
        if (existing.isPresent()) {
            conversation = existing.get();
        } else {
            MessagingPolicy policy = profiles.messagingPolicy(otherId);
            boolean connected = networking.isConnected(userId, otherId);
            if (policy == MessagingPolicy.NOBODY || (policy == MessagingPolicy.CONNECTIONS_ONLY && !connected)) {
                if (policy == MessagingPolicy.NOBODY) {
                    throw new ForbiddenException("MESSAGING_NOT_ALLOWED", "This member does not accept messages.");
                }
            }
            rateLimiter.check("conversation-start", userId.toString(), NEW_CONVERSATIONS_PER_DAY, Duration.ofDays(1));
            conversation = conversations.save(Conversation.direct(userId, otherId));
            members.save(ConversationMember.of(conversation.getId(), userId, ConversationState.ACTIVE));
            ConversationState otherState = policy == MessagingPolicy.EVERYONE || connected
                    ? ConversationState.ACTIVE : ConversationState.REQUEST;
            members.save(ConversationMember.of(conversation.getId(), otherId, otherState));
            if (otherState == ConversationState.REQUEST) {
                notifications.notify(otherId, NotificationType.MESSAGE_REQUEST, "New message request",
                        "A member who is not a connection would like to message you.", "/messages?box=requests", userId);
            }
        }
        deliver(conversation, userId, body);
        return get(userId, conversation.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationView> list(UUID userId, Box box, Pageable pageable) {
        EnumSet<ConversationState> states = switch (box) {
            case INBOX -> EnumSet.of(ConversationState.ACTIVE);
            case REQUESTS -> EnumSet.of(ConversationState.REQUEST);
            case ARCHIVED -> EnumSet.of(ConversationState.ARCHIVED);
        };
        Page<ConversationMember> page = members.inbox(userId, states, pageable);
        return page.map(m -> view(userId, conversations.findById(m.getId().getConversationId()).orElseThrow(), m));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationView get(UUID userId, UUID conversationId) {
        ConversationMember me = membership(userId, conversationId);
        return view(userId, conversations.findById(conversationId).orElseThrow(DefaultMessagingService::notFound), me);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageView> history(UUID userId, UUID conversationId, UUID beforeMessageId, int size) {
        membership(userId, conversationId);
        int limit = Math.clamp(size, 1, 100);
        List<Message> batch;
        if (beforeMessageId == null) {
            batch = messages.latest(conversationId, PageRequest.of(0, limit));
        } else {
            Message before = messages.findById(beforeMessageId)
                    .filter(m -> m.getConversationId().equals(conversationId))
                    .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found."));
            batch = messages.before(conversationId, before.getCreatedAt(), before.getId(), PageRequest.of(0, limit));
        }
        return batch.stream().map(m -> messageView(userId, m)).toList();
    }

    @Override
    public MessageView send(UUID userId, UUID conversationId, String rawBody) {
        membership(userId, conversationId);
        Conversation conversation = conversations.findById(conversationId).orElseThrow(DefaultMessagingService::notFound);
        return deliver(conversation, userId, sanitize(rawBody));
    }

    @Override
    public void markRead(UUID userId, UUID conversationId) {
        ConversationMember me = membership(userId, conversationId);
        List<Message> latest = messages.latest(conversationId, PageRequest.of(0, 1));
        if (!latest.isEmpty()) {
            me.markRead(latest.get(0).getId(), latest.get(0).getCreatedAt());
        }
    }

    @Override
    public ConversationView accept(UUID userId, UUID conversationId) {
        ConversationMember me = membership(userId, conversationId);
        if (me.getState() != ConversationState.REQUEST) {
            throw new ConflictException("NOT_A_REQUEST", "There is no pending request to accept.");
        }
        me.setState(ConversationState.ACTIVE);
        return get(userId, conversationId);
    }

    @Override
    public ConversationView decline(UUID userId, UUID conversationId) {
        ConversationMember me = membership(userId, conversationId);
        if (me.getState() != ConversationState.REQUEST) {
            throw new ConflictException("NOT_A_REQUEST", "There is no pending request to decline.");
        }
        me.setState(ConversationState.DECLINED);
        return get(userId, conversationId);
    }

    @Override
    public ConversationView archive(UUID userId, UUID conversationId, boolean archived) {
        ConversationMember me = membership(userId, conversationId);
        if (archived && me.getState() == ConversationState.ACTIVE) {
            me.setState(ConversationState.ARCHIVED);
        } else if (!archived && me.getState() == ConversationState.ARCHIVED) {
            me.setState(ConversationState.ACTIVE);
        }
        return get(userId, conversationId);
    }

    @Override
    public void deleteOwnMessage(UUID userId, UUID messageId) {
        Message message = messages.findById(messageId)
                .filter(m -> m.getSenderId().equals(userId))
                .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found."));
        message.delete(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadConversations(UUID userId) {
        return members.inbox(userId, EnumSet.of(ConversationState.ACTIVE), PageRequest.of(0, 50)).getContent().stream()
                .filter(m -> messages.unread(m.getId().getConversationId(), userId, m.getLastReadAt()) > 0)
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public MessageSnapshot snapshot(UUID reporterId, UUID messageId) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found."));
        membership(reporterId, message.getConversationId());
        if (message.isDeleted()) {
            throw new NotFoundException("MESSAGE_NOT_FOUND", "Message not found.");
        }
        return new MessageSnapshot(message.getId(), message.getConversationId(), message.getSenderId(), message.getBody(),
                message.getCreatedAt());
    }

    @Override
    public void removeByModeration(UUID moderatorId, UUID messageId, String reason) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found."));
        if (!message.isDeleted()) {
            message.delete(moderatorId);
            audit.record(auditContext.entryAs(moderatorId, AUDIT_MESSAGE_REMOVED, "MESSAGE", messageId,
                    Map.of("reason", reason, "senderId", message.getSenderId().toString())));
            notifications.notify(message.getSenderId(), NotificationType.CONTENT_REMOVED, "A message was removed",
                    "A moderator removed one of your messages: " + reason, "/messages", null);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private MessageView deliver(Conversation conversation, UUID senderId, String body) {
        List<ConversationMember> both = members.findByIdConversationId(conversation.getId());
        ConversationMember me = both.stream().filter(m -> m.getId().getUserId().equals(senderId)).findFirst()
                .orElseThrow(DefaultMessagingService::notFound);
        ConversationMember other = both.stream().filter(m -> !m.getId().getUserId().equals(senderId)).findFirst()
                .orElseThrow(DefaultMessagingService::notFound);
        UUID otherId = other.getId().getUserId();
        if (networking.isBlockedEitherWay(senderId, otherId)) {
            throw notFound();
        }
        if (other.getState() == ConversationState.DECLINED) {
            throw new ConflictException("REQUEST_DECLINED", "This member declined your message request.");
        }
        if (other.getState() == ConversationState.REQUEST && messages.countByConversationIdAndSenderId(conversation.getId(), senderId) >= 1) {
            throw new ConflictException("REQUEST_PENDING", "Wait for the member to accept your request before sending more.");
        }
        if (me.getState() == ConversationState.DECLINED || me.getState() == ConversationState.REQUEST) {
            me.setState(ConversationState.ACTIVE); // replying accepts
        } else if (me.getState() == ConversationState.ARCHIVED) {
            me.setState(ConversationState.ACTIVE);
        }
        if (other.getState() == ConversationState.ARCHIVED) {
            other.setState(ConversationState.ACTIVE);
        }
        rateLimiter.check("message-send", senderId.toString(), MESSAGES_PER_HOUR, Duration.ofHours(1));
        Message message = messages.save(Message.create(conversation.getId(), senderId, body));
        conversation.recordMessage(senderId, preview(body), message.getCreatedAt());
        me.markRead(message.getId(), message.getCreatedAt());
        MessageView view = messageView(senderId, message);
        MessageView forOther = messageView(otherId, message);
        boolean firstUnread = other.getState() == ConversationState.ACTIVE
                && messages.unread(conversation.getId(), otherId, other.getLastReadAt()) == 1;
        if (firstUnread) {
            MemberCard sender = profiles.cards(otherId, List.of(senderId)).get(senderId);
            notifications.notify(otherId, NotificationType.NEW_MESSAGE, "New message from "
                    + (sender == null ? "a member" : sender.displayName()), preview(body),
                    "/messages/" + conversation.getId(), senderId);
        }
        afterCommit(() -> realtime.toUser(otherId, RealtimePublisher.MESSAGES, forOther));
        return view;
    }

    private ConversationMember membership(UUID userId, UUID conversationId) {
        return members.findById(new ConversationMember.Key(conversationId, userId))
                .orElseThrow(DefaultMessagingService::notFound);
    }

    private ConversationView view(UUID userId, Conversation c, ConversationMember me) {
        List<ConversationMember> both = members.findByIdConversationId(c.getId());
        ConversationMember other = both.stream().filter(m -> !m.getId().getUserId().equals(userId)).findFirst().orElse(null);
        UUID otherId = other == null ? null : other.getId().getUserId();
        MemberCard card = otherId == null ? null : profiles.cards(userId, List.of(otherId)).get(otherId);
        long unread = messages.unread(c.getId(), userId, me.getLastReadAt());
        boolean canSend = other != null && other.getState() != ConversationState.DECLINED
                && !(other.getState() == ConversationState.REQUEST
                     && messages.countByConversationIdAndSenderId(c.getId(), userId) >= 1)
                && !networking.isBlockedEitherWay(userId, otherId);
        return new ConversationView(c.getId(), card, me.getState(), other == null ? null : other.getState(),
                c.getLastMessagePreview(), c.getLastMessageAt(), userId.equals(c.getLastMessageSender()), unread, canSend,
                c.getCreatedAt());
    }

    private static MessageView messageView(UUID viewerId, Message m) {
        return new MessageView(m.getId(), m.getConversationId(), m.getSenderId(), m.getSenderId().equals(viewerId),
                m.isDeleted() ? null : m.getBody(), m.isDeleted(), m.getCreatedAt());
    }

    private static String sanitize(String raw) {
        String body = PlainText.sanitize(raw);
        if (body.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Write a message first.");
        }
        if (body.length() > MAX_BODY) {
            throw new BadRequestException("BODY_TOO_LONG", "Messages may be at most " + MAX_BODY + " characters.");
        }
        return body;
    }

    private static String preview(String body) {
        String single = body.replace('\n', ' ');
        return single.length() <= 120 ? single : single.substring(0, 119) + "…";
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("CONVERSATION_NOT_FOUND", "Conversation not found.");
    }
}
