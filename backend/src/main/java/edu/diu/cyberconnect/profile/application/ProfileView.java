package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.AffiliationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full profile as seen by a given viewer. When {@code restricted} is true only name, headline, photo, affiliations
 * and relationship are populated (CONNECTIONS_ONLY profile viewed by a non-connection). {@code privacy} is present
 * only for the owner; {@code email} only when the owner's setting allows the viewer.
 */
public record ProfileView(UUID userId, String displayName, String headline, String bio, String organization,
        String jobTitle, String location, String country, List<String> specializations, Boolean mentorAvailable,
        String mentorTopics, String photoUrl, String email, List<AffiliationType> affiliations,
        List<SkillView> skills, List<ExperienceView> experiences, List<EducationView> educations,
        Instant memberSince, boolean restricted, RelationshipView relationship, PrivacySettings privacy) {
}
