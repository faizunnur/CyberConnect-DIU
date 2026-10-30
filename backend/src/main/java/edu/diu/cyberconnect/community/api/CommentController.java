package edu.diu.cyberconnect.community.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.community.application.CommentService;
import edu.diu.cyberconnect.community.application.CommentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @PutMapping("/{commentId}")
    public CommentView edit(@PathVariable UUID commentId, @Valid @RequestBody EditCommentRequest request) {
        return comments.edit(CurrentUser.require().getId(), commentId, request.body());
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID commentId) {
        comments.delete(CurrentUser.require().getId(), commentId);
    }

    public record EditCommentRequest(@NotNull @Size(max = 2500) String body) {
    }
}
