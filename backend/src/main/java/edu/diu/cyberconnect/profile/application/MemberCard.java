package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.AffiliationType;
import java.util.List;
import java.util.UUID;

/** Directory/list representation. Fields hidden by the owner's privacy settings are {@code null}. */
public record MemberCard(UUID userId, String displayName, String headline, String organization, String location,
        Boolean mentorAvailable, String photoUrl, boolean connected, List<AffiliationType> affiliations,
        List<String> skills) {
}
