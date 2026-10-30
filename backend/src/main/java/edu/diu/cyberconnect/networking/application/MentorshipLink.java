package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.common.model.MentorshipStatus;
import java.util.UUID;

/** Cross-module view of a mentorship as an impact source (D-12). */
public record MentorshipLink(UUID requestId, String topic, MentorshipStatus status, UUID menteeId, UUID mentorId) {
}
