package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A photo or document uploaded by a member; unclaimed until a post of the same member claims it. */
@Entity
@Table(name = "post_attachments")
public class PostAttachment {

    public enum Kind { IMAGE, FILE }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "post_id")
    private UUID postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "storage_key", nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "original_filename", updatable = false)
    private String originalFilename;

    private Integer width;

    private Integer height;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostAttachment() {
    }

    public static PostAttachment upload(UUID ownerId, Kind kind, String storageKey, String contentType, long sizeBytes,
            String originalFilename, Integer width, Integer height) {
        PostAttachment a = new PostAttachment();
        a.id = UUID.randomUUID();
        a.ownerId = ownerId;
        a.kind = kind;
        a.storageKey = storageKey;
        a.contentType = contentType;
        a.sizeBytes = sizeBytes;
        a.originalFilename = originalFilename;
        a.width = width;
        a.height = height;
        a.createdAt = Instant.now();
        return a;
    }

    public void claim(UUID postId, int position) {
        if (this.postId != null) {
            throw new IllegalStateException("Attachment already belongs to a post");
        }
        this.postId = postId;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getPostId() {
        return postId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
