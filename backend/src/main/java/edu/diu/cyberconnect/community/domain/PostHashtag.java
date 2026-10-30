package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "post_hashtags")
public class PostHashtag {

    @EmbeddedId
    private Key id;

    protected PostHashtag() {
    }

    public static PostHashtag of(UUID postId, UUID hashtagId) {
        PostHashtag link = new PostHashtag();
        link.id = new Key(postId, hashtagId);
        return link;
    }

    public Key getId() {
        return id;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "post_id", nullable = false)
        private UUID postId;

        @Column(name = "hashtag_id", nullable = false)
        private UUID hashtagId;

        protected Key() {
        }

        public Key(UUID postId, UUID hashtagId) {
            this.postId = postId;
            this.hashtagId = hashtagId;
        }

        public UUID getPostId() {
            return postId;
        }

        public UUID getHashtagId() {
            return hashtagId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && postId.equals(other.postId) && hashtagId.equals(other.hashtagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, hashtagId);
        }
    }
}
