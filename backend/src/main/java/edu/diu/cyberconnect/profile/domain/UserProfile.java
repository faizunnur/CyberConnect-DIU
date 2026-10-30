package edu.diu.cyberconnect.profile.domain;

import edu.diu.cyberconnect.common.model.FieldVisibility;
import edu.diu.cyberconnect.common.model.MessagingPolicy;
import edu.diu.cyberconnect.common.model.ProfileVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Member-facing profile and privacy settings. Student ids and documents never live here. */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String headline;

    private String bio;

    private String organization;

    @Column(name = "job_title")
    private String jobTitle;

    private String location;

    private String country;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "specializations", nullable = false, columnDefinition = "text[]")
    private List<String> specializations = new ArrayList<>();

    @Column(name = "specializations_text", nullable = false)
    private String specializationsText = "";

    @Column(name = "mentor_available", nullable = false)
    private boolean mentorAvailable;

    @Column(name = "mentor_topics")
    private String mentorTopics;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "photo_version", nullable = false)
    private int photoVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false)
    private ProfileVisibility profileVisibility = ProfileVisibility.ALL_MEMBERS;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_visibility", nullable = false)
    private FieldVisibility emailVisibility = FieldVisibility.NOBODY;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_visibility", nullable = false)
    private FieldVisibility organizationVisibility = FieldVisibility.ALL_MEMBERS;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_visibility", nullable = false)
    private FieldVisibility locationVisibility = FieldVisibility.ALL_MEMBERS;

    @Enumerated(EnumType.STRING)
    @Column(name = "mentor_visibility", nullable = false)
    private FieldVisibility mentorVisibility = FieldVisibility.ALL_MEMBERS;

    @Column(nullable = false)
    private boolean searchable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "messaging_policy", nullable = false)
    private MessagingPolicy messagingPolicy = MessagingPolicy.CONNECTIONS_ONLY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public static UserProfile create(UUID userId, String displayName) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.displayName = displayName;
        profile.createdAt = Instant.now();
        profile.updatedAt = profile.createdAt;
        return profile;
    }

    public void update(String displayName, String headline, String bio, String organization, String jobTitle,
            String location, String country, List<String> specializations, boolean mentorAvailable,
            String mentorTopics) {
        this.displayName = displayName.trim();
        this.headline = blankToNull(headline);
        this.bio = blankToNull(bio);
        this.organization = blankToNull(organization);
        this.jobTitle = blankToNull(jobTitle);
        this.location = blankToNull(location);
        this.country = blankToNull(country);
        this.specializations = new ArrayList<>(specializations);
        this.specializationsText = String.join(" ", specializations);
        this.mentorAvailable = mentorAvailable;
        this.mentorTopics = mentorAvailable ? blankToNull(mentorTopics) : null;
        touch();
    }

    public void updatePrivacy(ProfileVisibility profileVisibility, FieldVisibility emailVisibility,
            FieldVisibility organizationVisibility, FieldVisibility locationVisibility,
            FieldVisibility mentorVisibility, boolean searchable, MessagingPolicy messagingPolicy) {
        this.profileVisibility = profileVisibility;
        this.emailVisibility = emailVisibility;
        this.organizationVisibility = organizationVisibility;
        this.locationVisibility = locationVisibility;
        this.mentorVisibility = mentorVisibility;
        this.searchable = searchable;
        this.messagingPolicy = messagingPolicy;
        touch();
    }

    public void setPhoto(String key) {
        this.photoKey = key;
        this.photoVersion++;
        touch();
    }

    public void clearPhoto() {
        this.photoKey = null;
        this.photoVersion++;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHeadline() {
        return headline;
    }

    public String getBio() {
        return bio;
    }

    public String getOrganization() {
        return organization;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getLocation() {
        return location;
    }

    public String getCountry() {
        return country;
    }

    public List<String> getSpecializations() {
        return List.copyOf(specializations);
    }

    public boolean isMentorAvailable() {
        return mentorAvailable;
    }

    public String getMentorTopics() {
        return mentorTopics;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public int getPhotoVersion() {
        return photoVersion;
    }

    public ProfileVisibility getProfileVisibility() {
        return profileVisibility;
    }

    public FieldVisibility getEmailVisibility() {
        return emailVisibility;
    }

    public FieldVisibility getOrganizationVisibility() {
        return organizationVisibility;
    }

    public FieldVisibility getLocationVisibility() {
        return locationVisibility;
    }

    public FieldVisibility getMentorVisibility() {
        return mentorVisibility;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public MessagingPolicy getMessagingPolicy() {
        return messagingPolicy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
