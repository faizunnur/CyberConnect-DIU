package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserPostKey implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    protected UserPostKey() {
    }

    public UserPostKey(UUID userId, UUID postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPostId() {
        return postId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserPostKey other && userId.equals(other.userId) && postId.equals(other.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, postId);
    }
}
