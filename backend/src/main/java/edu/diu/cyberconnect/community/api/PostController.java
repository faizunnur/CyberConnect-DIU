package edu.diu.cyberconnect.community.api;

import edu.diu.cyberconnect.common.model.PostIntent;
import edu.diu.cyberconnect.common.model.PostVisibility;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.community.application.CommentService;
import edu.diu.cyberconnect.community.application.CommentView;
import edu.diu.cyberconnect.community.application.PostService;
import edu.diu.cyberconnect.community.application.PostView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Posts, reactions, bookmarks and comments. Verified members only; every read is visibility-checked server-side. */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService posts;
    private final CommentService comments;

    public PostController(PostService posts, CommentService comments) {
        this.posts = posts;
        this.comments = comments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostView create(@Valid @RequestBody CreatePostRequest request) {
        return posts.create(me(), request.intent(), request.body(), request.visibility(), request.repostOfId());
    }

    @GetMapping("/{postId}")
    public PostView get(@PathVariable UUID postId) {
        return posts.get(me(), postId);
    }

    @PutMapping("/{postId}")
    public PostView edit(@PathVariable UUID postId, @Valid @RequestBody EditPostRequest request) {
        return posts.edit(me(), postId, request.intent(), request.body(), request.visibility());
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID postId) {
        posts.delete(me(), postId);
    }

    @PostMapping("/{postId}/like")
    public PostView like(@PathVariable UUID postId) {
        return posts.like(me(), postId);
    }

    @DeleteMapping("/{postId}/like")
    public PostView unlike(@PathVariable UUID postId) {
        return posts.unlike(me(), postId);
    }

    @PostMapping("/{postId}/bookmark")
    public PostView bookmark(@PathVariable UUID postId) {
        return posts.bookmark(me(), postId);
    }

    @DeleteMapping("/{postId}/bookmark")
    public PostView unbookmark(@PathVariable UUID postId) {
        return posts.unbookmark(me(), postId);
    }

    @GetMapping("/{postId}/comments")
    public Page<CommentView> comments(@PathVariable UUID postId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return comments.list(me(), postId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@PathVariable UUID postId, @Valid @RequestBody CommentRequest request) {
        return comments.add(me(), postId, request.parentId(), request.body());
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record CreatePostRequest(@NotNull PostIntent intent, @Size(max = 6000) String body,
            PostVisibility visibility, UUID repostOfId) {
    }

    public record EditPostRequest(@NotNull PostIntent intent, @Size(max = 6000) String body, PostVisibility visibility) {
    }

    public record CommentRequest(@NotNull @Size(max = 2500) String body, UUID parentId) {
    }
}
