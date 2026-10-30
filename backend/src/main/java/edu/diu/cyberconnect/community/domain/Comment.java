package edu.diu.cyberconnect.community.domain;

import edu.diu.cyberconnect.common.model.ContentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Comment on a post; {@code parentId} points at a top-level comment for one-level replies. */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_reason")
    private String removedReason;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Comment() {
    }

    public static Comment create(UUID postId, UUID authorId, UUID parentId, String body) {
        Comment comment = new Comment();
        comment.id = UUID.randomUUID();
        comment.postId = postId;
        comment.authorId = authorId;
        comment.parentId = parentId;
        comment.body = body;
        comment.status = ContentStatus.ACTIVE;
        comment.createdAt = Instant.now();
        comment.updatedAt = comment.createdAt;
        return comment;
    }

    public void edit(String body) {
        this.body = body;
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

    public boolean isReply() {
        return parentId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getBody() {
        return body;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
