package edu.diu.cyberconnect.community.domain;

import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.common.model.PostIntent;
import edu.diu.cyberconnect.common.model.PostVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostIntent intent;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostVisibility visibility;

    @Column(name = "repost_of_id")
    private UUID repostOfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_reason")
    private String removedReason;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
    private int commentCount;

    @Column(name = "repost_count", nullable = false, insertable = false, updatable = false)
    private int repostCount;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Post() {
    }

    public static Post create(UUID authorId, PostIntent intent, String body, PostVisibility visibility, UUID repostOfId) {
        Post post = new Post();
        post.id = UUID.randomUUID();
        post.authorId = authorId;
        post.intent = intent;
        post.body = body;
        post.visibility = visibility;
        post.repostOfId = repostOfId;
        post.status = ContentStatus.ACTIVE;
        post.createdAt = Instant.now();
        post.updatedAt = post.createdAt;
        return post;
    }

    public void edit(PostIntent intent, String body, PostVisibility visibility) {
        this.intent = intent;
        this.body = body;
        this.visibility = visibility;
        this.editedAt = Instant.now();
        this.updatedAt = this.editedAt;
    }

    public void deleteByAuthor() {
        this.status = ContentStatus.DELETED;
        this.updatedAt = Instant.now();
    }

    public void remove(UUID moderatorId, String reason) {
        this.status = ContentStatus.REMOVED;
        this.removedBy = moderatorId;
        this.removedReason = reason;
        this.removedAt = Instant.now();
        this.updatedAt = this.removedAt;
    }

    public void restore() {
        this.status = ContentStatus.ACTIVE;
        this.removedBy = null;
        this.removedReason = null;
        this.removedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == ContentStatus.ACTIVE;
    }

    public boolean isRepost() {
        return repostOfId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public PostIntent getIntent() {
        return intent;
    }

    public String getBody() {
        return body;
    }

    public PostVisibility getVisibility() {
        return visibility;
    }

    public UUID getRepostOfId() {
        return repostOfId;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public UUID getRemovedBy() {
        return removedBy;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getRepostCount() {
        return repostCount;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
