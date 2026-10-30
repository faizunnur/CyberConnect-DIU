package edu.diu.cyberconnect.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Skill() {
    }

    public static Skill create(String name) {
        Skill skill = new Skill();
        skill.id = UUID.randomUUID();
        skill.name = normalizeName(name);
        skill.slug = slugOf(skill.name);
        skill.createdAt = Instant.now();
        return skill;
    }

    /** Collapses whitespace and trims; keeps the member's capitalisation for display. */
    public static String normalizeName(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    public static String slugOf(String name) {
        String slug = normalizeName(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
