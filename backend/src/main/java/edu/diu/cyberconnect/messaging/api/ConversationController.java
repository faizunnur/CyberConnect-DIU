package edu.diu.cyberconnect.messaging.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.messaging.application.MessagingService;
import edu.diu.cyberconnect.messaging.application.MessagingViews.ConversationView;
import edu.diu.cyberconnect.messaging.application.MessagingViews.MessageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final MessagingService messaging;

    public ConversationController(MessagingService messaging) {
        this.messaging = messaging;
    }

    @GetMapping("/conversations")
    public Page<ConversationView> list(@RequestParam(defaultValue = "inbox") String box,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        MessagingService.Box selected = switch (box.toLowerCase()) {
            case "requests" -> MessagingService.Box.REQUESTS;
            case "archived" -> MessagingService.Box.ARCHIVED;
            default -> MessagingService.Box.INBOX;
        };
        return messaging.list(me(), selected, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/conversations/summary")
    public Map<String, Long> summary() {
        return Map.of("unread", messaging.unreadConversations(me()));
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView start(@Valid @RequestBody StartRequest request) {
        return messaging.start(me(), request.userId(), request.body());
    }

    @GetMapping("/conversations/{id}")
    public ConversationView get(@PathVariable UUID id) {
        return messaging.get(me(), id);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageView> history(@PathVariable UUID id, @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int size) {
        return messaging.history(me(), id, before, size);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@PathVariable UUID id, @Valid @RequestBody MessageRequest request) {
        return messaging.send(me(), id, request.body());
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        messaging.markRead(me(), id);
    }

    @PostMapping("/conversations/{id}/accept")
    public ConversationView accept(@PathVariable UUID id) {
        return messaging.accept(me(), id);
    }

    @PostMapping("/conversations/{id}/decline")
    public ConversationView decline(@PathVariable UUID id) {
        return messaging.decline(me(), id);
    }

    @PostMapping("/conversations/{id}/archive")
    public ConversationView archive(@PathVariable UUID id) {
        return messaging.archive(me(), id, true);
    }

    @PostMapping("/conversations/{id}/unarchive")
    public ConversationView unarchive(@PathVariable UUID id) {
        return messaging.archive(me(), id, false);
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable UUID messageId) {
        messaging.deleteOwnMessage(me(), messageId);
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record StartRequest(@NotNull UUID userId, @NotBlank @Size(max = 4500) String body) {
    }

    public record MessageRequest(@NotBlank @Size(max = 4500) String body) {
    }
}
