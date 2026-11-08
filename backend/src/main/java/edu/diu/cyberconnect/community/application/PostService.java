package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.common.model.PostIntent;
import edu.diu.cyberconnect.common.model.PostVisibility;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.community.domain.Bookmark;
import edu.diu.cyberconnect.community.domain.Hashtag;
import edu.diu.cyberconnect.community.domain.Post;
import edu.diu.cyberconnect.community.domain.PostHashtag;
import edu.diu.cyberconnect.community.domain.PostLike;
import edu.diu.cyberconnect.community.domain.UserPostKey;
import edu.diu.cyberconnect.community.infrastructure.BookmarkRepository;
import edu.diu.cyberconnect.community.infrastructure.FeedQueryRepository;
import edu.diu.cyberconnect.community.infrastructure.HashtagRepository;
import edu.diu.cyberconnect.community.infrastructure.PostHashtagRepository;
import edu.diu.cyberconnect.community.infrastructure.PostLikeRepository;
import edu.diu.cyberconnect.community.infrastructure.PostRepository;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.notification.application.NotificationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PostService {

    public static final int MAX_BODY = 5000;
    static final int POSTS_PER_HOUR = 20;

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final HashtagRepository hashtags;
    private final PostHashtagRepository postHashtags;
    private final FeedQueryRepository feedQuery;
    private final PostAssembler assembler;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final NotificationService notifications;
    private final PostAttachmentService attachments;

    public PostService(PostRepository posts, PostLikeRepository likes, BookmarkRepository bookmarks,
            HashtagRepository hashtags, PostHashtagRepository postHashtags, FeedQueryRepository feedQuery,
            PostAssembler assembler, RateLimiter rateLimiter, AuditService audit, AuditContext auditContext,
            NotificationService notifications, PostAttachmentService attachments) {
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.hashtags = hashtags;
        this.postHashtags = postHashtags;
        this.feedQuery = feedQuery;
        this.assembler = assembler;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
        this.notifications = notifications;
        this.attachments = attachments;
    }

    public PostView create(UUID authorId, PostIntent intent, String rawBody, PostVisibility visibility, UUID repostOfId) {
        return create(authorId, intent, rawBody, visibility, repostOfId, List.of());
    }

    /** Creates a post; {@code attachmentIds} are the author's own unclaimed uploads (photos or one document). */
    public PostView create(UUID authorId, PostIntent intent, String rawBody, PostVisibility visibility, UUID repostOfId,
            List<UUID> attachmentIds) {
        boolean hasAttachments = attachmentIds != null && !attachmentIds.isEmpty();
        if (hasAttachments && repostOfId != null) {
            throw new BadRequestException("ATTACHMENT_ON_REPOST", "Reposts cannot carry attachments.");
        }
        String body = sanitizeBody(rawBody, repostOfId != null || hasAttachments);
        if (repostOfId != null) {
            Post original = posts.findById(repostOfId).orElseThrow(PostService::postNotFound);
            if (!original.isActive() || !feedQuery.isVisible(authorId, original.getId())) {
                throw postNotFound();
            }
            if (original.isRepost() || original.getVisibility() != PostVisibility.ALL_MEMBERS) {
                throw new ConflictException("REPOST_NOT_ALLOWED",
                        "Only original posts shared with all members can be reposted.");
            }
        }
        rateLimiter.check("post-create", authorId.toString(), POSTS_PER_HOUR, Duration.ofHours(1));
        Post post = posts.save(Post.create(authorId, intent, body, visibility == null ? PostVisibility.ALL_MEMBERS : visibility,
                repostOfId));
        linkHashtags(post);
        attachments.claim(authorId, post.getId(), attachmentIds);
        if (repostOfId != null) {
            posts.adjustReposts(repostOfId, 1);
        }
        posts.flush();
        return get(authorId, post.getId());
    }

    public PostView edit(UUID userId, UUID postId, PostIntent intent, String rawBody, PostVisibility visibility) {
        Post post = own(userId, postId);
        if (!post.isActive()) {
            throw new ConflictException("POST_NOT_EDITABLE", "This post can no longer be edited.");
        }
        boolean hasAttachments = !attachments.forPosts(List.of(postId), false).isEmpty();
        post.edit(intent, sanitizeBody(rawBody, post.isRepost() || hasAttachments), visibility == null ? post.getVisibility() : visibility);
        hashtags.unlinkAll(postId);
        linkHashtags(post);
        return get(userId, postId);
    }

    public void delete(UUID userId, UUID postId) {
        Post post = own(userId, postId);
        if (post.getStatus() == ContentStatus.DELETED) {
            return;
        }
        boolean wasActive = post.isActive();
        post.deleteByAuthor();
        attachments.deleteForPost(postId);
        if (wasActive && post.isRepost()) {
            posts.adjustReposts(post.getRepostOfId(), -1);
        }
    }

    @Transactional(readOnly = true)
    public PostView get(UUID viewerId, UUID postId) {
        return assembler.assembleOne(viewerId, visible(viewerId, postId), false);
    }

    @Transactional(readOnly = true)
    public Page<PostView> feed(UUID viewerId, FeedQuery query, Pageable pageable) {
        Page<UUID> ids = feedQuery.feed(viewerId, query, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<UUID, Post> byId = posts.findAllById(ids.getContent()).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> ordered = ids.getContent().stream().map(byId::get).filter(p -> p != null).toList();
        return new PageImpl<>(assembler.assemble(viewerId, ordered, false), pageable, ids.getTotalElements());
    }

    public PostView like(UUID userId, UUID postId) {
        Post post = visible(userId, postId);
        UserPostKey key = new UserPostKey(userId, postId);
        if (!likes.existsById(key)) {
            likes.save(PostLike.of(postId, userId));
            posts.adjustLikes(post.getId(), 1);
        }
        return get(userId, postId);
    }

    public PostView unlike(UUID userId, UUID postId) {
        visible(userId, postId);
        UserPostKey key = new UserPostKey(userId, postId);
        if (likes.existsById(key)) {
            likes.deleteById(key);
            posts.adjustLikes(postId, -1);
        }
        return get(userId, postId);
    }

    public PostView bookmark(UUID userId, UUID postId) {
        visible(userId, postId);
        UserPostKey key = new UserPostKey(userId, postId);
        if (!bookmarks.existsById(key)) {
            bookmarks.save(Bookmark.of(userId, postId));
        }
        return get(userId, postId);
    }

    public PostView unbookmark(UUID userId, UUID postId) {
        bookmarks.findById(new UserPostKey(userId, postId)).ifPresent(bookmarks::delete);
        return get(userId, postId);
    }

    /** Bytes of an attachment of a post the viewer may see (own posts included). */
    @Transactional(readOnly = true)
    public PostAttachmentService.AttachmentContent attachment(UUID viewerId, UUID postId, UUID attachmentId) {
        Post post = visible(viewerId, postId);
        if (!post.isActive() && !post.getAuthorId().equals(viewerId)) {
            throw PostAttachmentService.notFound();
        }
        return attachments.load(postId, attachmentId);
    }

    /** Moderators may open attachments of any post, including removed ones, when handling reports. */
    @Transactional(readOnly = true)
    public PostAttachmentService.AttachmentContent moderatorAttachment(UUID postId, UUID attachmentId) {
        posts.findById(postId).orElseThrow(PostService::postNotFound);
        return attachments.load(postId, attachmentId);
    }

    @Transactional(readOnly = true)
    public List<TrendingTag> trending() {
        return feedQuery.trending(7, 10);
    }

    // Moderation ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PostView moderatorGet(UUID moderatorId, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(PostService::postNotFound);
        return assembler.assembleOne(moderatorId, post, true);
    }

    public PostView remove(UUID moderatorId, UUID postId, String reason) {
        Post post = posts.findById(postId).orElseThrow(PostService::postNotFound);
        if (post.getStatus() == ContentStatus.DELETED) {
            throw new ConflictException("POST_DELETED", "The author already deleted this post.");
        }
        if (post.isActive()) {
            post.remove(moderatorId, reason);
            if (post.isRepost()) {
                posts.adjustReposts(post.getRepostOfId(), -1);
            }
            audit.record(auditContext.entryAs(moderatorId, CommunityAuditActions.POST_REMOVED,
                    CommunityAuditActions.TARGET_POST, postId, Map.of("reason", reason, "authorId", post.getAuthorId().toString())));
            notifications.notify(post.getAuthorId(), NotificationType.CONTENT_REMOVED, "A post of yours was removed", reason,
                    "/posts/" + postId, null);
        }
        return assembler.assembleOne(moderatorId, post, true);
    }

    public PostView restore(UUID moderatorId, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(PostService::postNotFound);
        if (post.getStatus() == ContentStatus.REMOVED) {
            post.restore();
            if (post.isRepost()) {
                posts.adjustReposts(post.getRepostOfId(), 1);
            }
            audit.record(auditContext.entryAs(moderatorId, CommunityAuditActions.POST_RESTORED,
                    CommunityAuditActions.TARGET_POST, postId, Map.of("authorId", post.getAuthorId().toString())));
        }
        return assembler.assembleOne(moderatorId, post, true);
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Post the viewer may read: their own (any status) or one that passes the feed visibility rules. */
    Post visible(UUID viewerId, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(PostService::postNotFound);
        if (post.getAuthorId().equals(viewerId)) {
            return post;
        }
        if (!feedQuery.isVisible(viewerId, postId)) {
            throw postNotFound();
        }
        return post;
    }

    private Post own(UUID userId, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(PostService::postNotFound);
        if (!post.getAuthorId().equals(userId)) {
            throw postNotFound();
        }
        return post;
    }

    private void linkHashtags(Post post) {
        List<String> tags = PlainText.hashtags(post.getBody());
        if (tags.isEmpty()) {
            return;
        }
        Map<String, Hashtag> existing = hashtags.findByTagIn(tags).stream()
                .collect(Collectors.toMap(Hashtag::getTag, Function.identity()));
        List<PostHashtag> links = new ArrayList<>();
        for (String tag : tags) {
            Hashtag hashtag = existing.get(tag);
            if (hashtag == null) {
                hashtag = hashtags.save(Hashtag.of(tag));
            }
            links.add(PostHashtag.of(post.getId(), hashtag.getId()));
        }
        postHashtags.saveAll(links);
    }

    static String sanitizeBody(String raw, boolean commentaryOptional) {
        String body = PlainText.sanitize(raw);
        if (body.isEmpty() && !commentaryOptional) {
            throw new BadRequestException("BODY_REQUIRED", "Write something before posting.");
        }
        if (body.length() > MAX_BODY) {
            throw new BadRequestException("BODY_TOO_LONG", "Posts may be at most " + MAX_BODY + " characters.");
        }
        return body;
    }

    static NotFoundException postNotFound() {
        return new NotFoundException("POST_NOT_FOUND", "Post not found.");
    }
}
