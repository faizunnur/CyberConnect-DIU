package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.community.domain.Comment;
import edu.diu.cyberconnect.community.domain.Post;
import edu.diu.cyberconnect.community.infrastructure.CommentRepository;
import edu.diu.cyberconnect.community.infrastructure.PostRepository;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.profile.application.ProfileService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommentService {

    public static final int MAX_BODY = 2000;
    static final int COMMENTS_PER_HOUR = 60;

    private final CommentRepository comments;
    private final PostRepository posts;
    private final PostService postService;
    private final PostAssembler assembler;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;

    public CommentService(CommentRepository comments, PostRepository posts, PostService postService,
            PostAssembler assembler, ProfileService profiles, RateLimiter rateLimiter, AuditService audit,
            AuditContext auditContext) {
        this.comments = comments;
        this.posts = posts;
        this.postService = postService;
        this.assembler = assembler;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
    }

    @Transactional(readOnly = true)
    public Page<CommentView> list(UUID viewerId, UUID postId, Pageable pageable) {
        postService.visible(viewerId, postId);
        Page<Comment> topLevel = comments.topLevel(postId, pageable);
        List<Comment> replies = topLevel.isEmpty() ? List.of()
                : comments.findByParentIdInAndStatusOrderByCreatedAtAsc(topLevel.map(Comment::getId).toList(),
                        ContentStatus.ACTIVE);
        return new PageImpl<>(assembler.assembleComments(viewerId, topLevel.getContent(), replies), pageable,
                topLevel.getTotalElements());
    }

    public CommentView add(UUID authorId, UUID postId, UUID parentId, String rawBody) {
        Post post = postService.visible(authorId, postId);
        if (!post.isActive()) {
            throw new ConflictException("POST_NOT_ACTIVE", "This post no longer accepts comments.");
        }
        if (parentId != null) {
            Comment parent = comments.findById(parentId).orElseThrow(() ->
                    new BadRequestException("INVALID_PARENT", "The comment you reply to does not exist."));
            if (!parent.getPostId().equals(postId) || parent.isReply() || !parent.isActive()) {
                throw new BadRequestException("INVALID_PARENT", "Replies can only answer a top-level comment of this post.");
            }
        }
        String body = sanitize(rawBody);
        rateLimiter.check("comment-create", authorId.toString(), COMMENTS_PER_HOUR, Duration.ofHours(1));
        Comment comment = comments.save(Comment.create(postId, authorId, parentId, body));
        posts.adjustComments(postId, 1);
        return view(authorId, comment);
    }

    public CommentView edit(UUID userId, UUID commentId, String rawBody) {
        Comment comment = own(userId, commentId);
        if (!comment.isActive()) {
            throw new ConflictException("COMMENT_NOT_EDITABLE", "This comment can no longer be edited.");
        }
        comment.edit(sanitize(rawBody));
        return view(userId, comment);
    }

    public void delete(UUID userId, UUID commentId) {
        Comment comment = own(userId, commentId);
        if (comment.isActive()) {
            comment.deleteByAuthor();
            posts.adjustComments(comment.getPostId(), -1);
        }
    }

    // Moderation ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CommentView moderatorGet(UUID moderatorId, UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::notFound);
        return assembler.commentView(moderatorId, comment, profiles.cards(moderatorId, List.of(comment.getAuthorId())),
                List.of());
    }

    public CommentView remove(UUID moderatorId, UUID commentId, String reason) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::notFound);
        if (comment.getStatus() == ContentStatus.DELETED) {
            throw new ConflictException("COMMENT_DELETED", "The author already deleted this comment.");
        }
        if (comment.isActive()) {
            comment.remove(moderatorId, reason);
            posts.adjustComments(comment.getPostId(), -1);
            audit.record(auditContext.entryAs(moderatorId, CommunityAuditActions.COMMENT_REMOVED,
                    CommunityAuditActions.TARGET_COMMENT, commentId,
                    Map.of("reason", reason, "authorId", comment.getAuthorId().toString(), "postId", comment.getPostId().toString())));
        }
        return moderatorGet(moderatorId, commentId);
    }

    public CommentView restore(UUID moderatorId, UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::notFound);
        if (comment.getStatus() == ContentStatus.REMOVED) {
            comment.restore();
            posts.adjustComments(comment.getPostId(), 1);
            audit.record(auditContext.entryAs(moderatorId, CommunityAuditActions.COMMENT_RESTORED,
                    CommunityAuditActions.TARGET_COMMENT, commentId, Map.of("authorId", comment.getAuthorId().toString())));
        }
        return moderatorGet(moderatorId, commentId);
    }

    Comment find(UUID commentId) {
        return comments.findById(commentId).orElseThrow(CommentService::notFound);
    }

    // ---------------------------------------------------------------------------------------------------------

    private CommentView view(UUID viewerId, Comment comment) {
        return assembler.commentView(viewerId, comment, profiles.cards(viewerId, List.of(comment.getAuthorId())),
                List.of());
    }

    private Comment own(UUID userId, UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(CommentService::notFound);
        if (!comment.getAuthorId().equals(userId)) {
            throw notFound();
        }
        return comment;
    }

    private static String sanitize(String raw) {
        String body = PlainText.sanitize(raw);
        if (body.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Write something before commenting.");
        }
        if (body.length() > MAX_BODY) {
            throw new BadRequestException("BODY_TOO_LONG", "Comments may be at most " + MAX_BODY + " characters.");
        }
        return body;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("COMMENT_NOT_FOUND", "Comment not found.");
    }
}
