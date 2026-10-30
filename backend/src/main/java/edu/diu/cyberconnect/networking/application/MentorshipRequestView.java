package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.common.model.MentorshipStatus;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

/** {@code incoming} is true when the viewer is the mentor. */
public record MentorshipRequestView(UUID id, MemberCard mentee, MemberCard mentor, MentorshipStatus status, String topic,
        String message, String responseNote, boolean incoming, Instant createdAt, Instant respondedAt,
        Instant completedAt) {
}
