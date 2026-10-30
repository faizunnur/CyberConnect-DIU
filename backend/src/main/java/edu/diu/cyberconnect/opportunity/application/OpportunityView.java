package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityStatus;
import edu.diu.cyberconnect.common.model.OpportunityType;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.SkillView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full opportunity. {@code myApplication} is the viewer's own application if any; {@code canApply} is computed
 * server-side (open, internal method, deadline not passed, not the author, no live application).
 */
public record OpportunityView(UUID id, MemberCard author, OpportunityType type, String title, String organization,
        LocationType locationType, String location, String description, String compensation, LocalDate deadline,
        OpportunityStatus status, ApplicationMethod applicationMethod, String applicationUrl, String applicationEmail,
        List<SkillView> skills, int applicationCount, boolean own, boolean canApply, ApplicationView myApplication,
        String removedReason, Instant publishedAt, Instant closedAt, Instant createdAt, Instant updatedAt) {
}
