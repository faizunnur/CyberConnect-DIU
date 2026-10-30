package edu.diu.cyberconnect.notification.api;

import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.notification.application.NotificationService;
import edu.diu.cyberconnect.notification.application.NotificationView;
import edu.diu.cyberconnect.notification.application.PreferenceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationView> list(@RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notifications.list(me(), unread, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of("unread", notifications.unreadCount(me()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        notifications.markRead(me(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notifications.markAllRead(me()));
    }

    @GetMapping("/preferences")
    public List<PreferenceView> preferences() {
        return notifications.preferences(me());
    }

    @PutMapping("/preferences")
    public List<PreferenceView> updatePreferences(@Valid @RequestBody List<PreferenceRequest> request) {
        return notifications.updatePreferences(me(), request.stream()
                .map(p -> new PreferenceView(p.type(), p.inApp(), p.email())).toList());
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record PreferenceRequest(@NotNull NotificationType type, boolean inApp, boolean email) {
    }
}
