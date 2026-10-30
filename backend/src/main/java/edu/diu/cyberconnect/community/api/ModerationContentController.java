package edu.diu.cyberconnect.community.api;

import edu.diu.cyberconnect.common.model.ReportResolution;
import edu.diu.cyberconnect.common.model.ReportStatus;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.community.application.CommentService;
import edu.diu.cyberconnect.community.application.CommentView;
import edu.diu.cyberconnect.community.application.PostService;
import edu.diu.cyberconnect.community.application.PostView;
import edu.diu.cyberconnect.community.application.ReportService;
import edu.diu.cyberconnect.community.application.ReportView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Report queue and content removal/restoration for moderators (MFA-enrolled, MODERATION_ACTION). */
@RestController
@RequestMapping("/api/v1/moderation")
@PreAuthorize("hasAuthority('MODERATION_ACTION')")
public class ModerationContentController {

    private final ReportService reports;
    private final PostService posts;
    private final CommentService comments;

    public ModerationContentController(ReportService reports, PostService posts, CommentService comments) {
        this.reports = reports;
        this.posts = posts;
        this.comments = comments;
    }

    @GetMapping("/reports")
    public Page<ReportView> reports(@RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.queue(me(), status, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/reports/summary")
    public Map<String, Long> summary() {
        return Map.of("open", reports.openCount());
    }

    @GetMapping("/reports/{reportId}")
    public ReportView report(@PathVariable UUID reportId) {
        return reports.get(me(), reportId);
    }

    @PostMapping("/reports/{reportId}/resolve")
    public ReportView resolve(@PathVariable UUID reportId, @Valid @RequestBody ResolveRequest request) {
        return reports.resolve(me(), reportId, request.resolution(), request.note());
    }

    @GetMapping("/posts/{postId}")
    public PostView post(@PathVariable UUID postId) {
        return posts.moderatorGet(me(), postId);
    }

    @PostMapping("/posts/{postId}/remove")
    public PostView removePost(@PathVariable UUID postId, @Valid @RequestBody ReasonRequest request) {
        return posts.remove(me(), postId, request.reason());
    }

    @PostMapping("/posts/{postId}/restore")
    public PostView restorePost(@PathVariable UUID postId) {
        return posts.restore(me(), postId);
    }

    @PostMapping("/comments/{commentId}/remove")
    public CommentView removeComment(@PathVariable UUID commentId, @Valid @RequestBody ReasonRequest request) {
        return comments.remove(me(), commentId, request.reason());
    }

    @PostMapping("/comments/{commentId}/restore")
    public CommentView restoreComment(@PathVariable UUID commentId) {
        return comments.restore(me(), commentId);
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record ResolveRequest(@NotNull ReportResolution resolution, @Size(max = 2000) String note) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
