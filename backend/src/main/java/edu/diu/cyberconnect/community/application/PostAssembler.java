package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.community.domain.Comment;
import edu.diu.cyberconnect.community.domain.Post;
import edu.diu.cyberconnect.community.infrastructure.BookmarkRepository;
import edu.diu.cyberconnect.community.infrastructure.FeedQueryRepository;
import edu.diu.cyberconnect.community.infrastructure.HashtagRepository;
import edu.diu.cyberconnect.community.infrastructure.PostLikeRepository;
import edu.diu.cyberconnect.community.infrastructure.PostRepository;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Turns entities into viewer-specific views with batched lookups (authors, likes, bookmarks, hashtags, originals). */
@Component
public class PostAssembler {

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final HashtagRepository hashtags;
    private final FeedQueryRepository feedQuery;
    private final ProfileService profiles;

    public PostAssembler(PostRepository posts, PostLikeRepository likes, BookmarkRepository bookmarks,
            HashtagRepository hashtags, FeedQueryRepository feedQuery, ProfileService profiles) {
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.hashtags = hashtags;
        this.feedQuery = feedQuery;
        this.profiles = profiles;
    }

    /** @param moderator when true, removal reasons are included for every post */
    public List<PostView> assemble(UUID viewerId, List<Post> batch, boolean moderator) {
        if (batch.isEmpty()) {
            return List.of();
        }
        // Originals of reposts (one level), only when still visible to the viewer.
        Map<UUID, Post> originals = new HashMap<>();
        Set<UUID> originalIds = batch.stream().map(Post::getRepostOfId).filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!originalIds.isEmpty()) {
            for (Post original : posts.findAllById(originalIds)) {
                if (original.isActive() && feedQuery.isVisible(viewerId, original.getId())) {
                    originals.put(original.getId(), original);
                }
            }
        }
        List<Post> all = new ArrayList<>(batch);
        all.addAll(originals.values());
        Set<UUID> postIds = all.stream().map(Post::getId).collect(Collectors.toSet());
        Set<UUID> authorIds = all.stream().map(Post::getAuthorId).collect(Collectors.toSet());

        Map<UUID, MemberCard> authors = profiles.cards(viewerId, authorIds);
        Set<UUID> liked = new HashSet<>(likes.likedPostIds(viewerId, postIds));
        Set<UUID> bookmarked = new HashSet<>(bookmarks.bookmarkedPostIds(viewerId, postIds));
        Map<UUID, List<String>> tags = new HashMap<>();
        for (Object[] row : hashtags.tagsForPosts(postIds)) {
            tags.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }

        Function<Post, PostView> toView = p -> view(viewerId, p, authors, liked, bookmarked, tags, null, false, moderator);
        List<PostView> result = new ArrayList<>(batch.size());
        for (Post post : batch) {
            PostView original = null;
            boolean unavailable = false;
            if (post.isRepost()) {
                Post o = originals.get(post.getRepostOfId());
                if (o == null) {
                    unavailable = true;
                } else {
                    original = toView.apply(o);
                }
            }
            result.add(view(viewerId, post, authors, liked, bookmarked, tags, original, unavailable, moderator));
        }
        return result;
    }

    public PostView assembleOne(UUID viewerId, Post post, boolean moderator) {
        return assemble(viewerId, List.of(post), moderator).get(0);
    }

    public List<CommentView> assembleComments(UUID viewerId, List<Comment> topLevel, List<Comment> replies) {
        Set<UUID> authorIds = new HashSet<>();
        topLevel.stream().filter(Comment::isActive).map(Comment::getAuthorId).forEach(authorIds::add);
        replies.stream().map(Comment::getAuthorId).forEach(authorIds::add);
        Map<UUID, MemberCard> authors = authorIds.isEmpty() ? Map.of() : profiles.cards(viewerId, authorIds);
        Map<UUID, List<CommentView>> byParent = new HashMap<>();
        for (Comment reply : replies) {
            byParent.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>())
                    .add(commentView(viewerId, reply, authors, List.of()));
        }
        return topLevel.stream()
                .map(c -> commentView(viewerId, c, authors, byParent.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    public CommentView commentView(UUID viewerId, Comment c, Map<UUID, MemberCard> authors, List<CommentView> replies) {
        boolean active = c.getStatus() == ContentStatus.ACTIVE;
        return new CommentView(c.getId(), c.getPostId(), c.getParentId(), active ? authors.get(c.getAuthorId()) : null,
                active ? c.getBody() : null, c.getStatus(), c.getAuthorId().equals(viewerId), replies,
                c.getCreatedAt(), c.getEditedAt());
    }

    private static PostView view(UUID viewerId, Post p, Map<UUID, MemberCard> authors, Set<UUID> liked,
            Set<UUID> bookmarked, Map<UUID, List<String>> tags, PostView original, boolean unavailable,
            boolean moderator) {
        boolean own = p.getAuthorId().equals(viewerId);
        String removedReason = (own || moderator) && p.getStatus() == ContentStatus.REMOVED ? p.getRemovedReason() : null;
        return new PostView(p.getId(), authors.get(p.getAuthorId()), p.getIntent(), p.getBody(), p.getVisibility(),
                tags.getOrDefault(p.getId(), List.of()), p.getLikeCount(), p.getCommentCount(), p.getRepostCount(),
                liked.contains(p.getId()), bookmarked.contains(p.getId()), own, p.getStatus(), removedReason,
                original, unavailable, p.getCreatedAt(), p.getEditedAt());
    }
}
