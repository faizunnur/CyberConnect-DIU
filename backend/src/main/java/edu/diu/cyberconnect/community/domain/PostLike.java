package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_likes")
public class PostLike {

    @EmbeddedId
    private UserPostKey id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostLike() {
    }

    public static PostLike of(UUID postId, UUID userId) {
        PostLike like = new PostLike();
        like.id = new UserPostKey(userId, postId);
        like.createdAt = Instant.now();
        return like;
    }

    public UserPostKey getId() {
        return id;
    }
}
