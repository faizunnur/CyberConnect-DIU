package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookmarks")
public class Bookmark {

    @EmbeddedId
    private UserPostKey id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Bookmark() {
    }

    public static Bookmark of(UUID userId, UUID postId) {
        Bookmark bookmark = new Bookmark();
        bookmark.id = new UserPostKey(userId, postId);
        bookmark.createdAt = Instant.now();
        return bookmark;
    }

    public UserPostKey getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
