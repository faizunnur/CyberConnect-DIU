package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.ApplicationStatus;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

/** {@code applicant} is present for the opportunity author; {@code opportunity} for the applicant's own list. */
public record ApplicationView(UUID id, UUID opportunityId, MemberCard applicant, ApplicationStatus status,
        String coverNote, Instant createdAt, Instant decidedAt, OpportunityCard opportunity) {
}
