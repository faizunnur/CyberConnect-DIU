package edu.diu.cyberconnect.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hashtags")
public class Hashtag {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Hashtag() {
    }

    public static Hashtag of(String tag) {
        Hashtag hashtag = new Hashtag();
        hashtag.id = UUID.randomUUID();
        hashtag.tag = tag;
        hashtag.createdAt = Instant.now();
        return hashtag;
    }

    public UUID getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }
}
