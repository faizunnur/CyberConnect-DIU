package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityStatus;
import edu.diu.cyberconnect.common.model.OpportunityType;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** List representation. */
public record OpportunityCard(UUID id, MemberCard author, OpportunityType type, String title, String organization,
        LocationType locationType, String location, LocalDate deadline, OpportunityStatus status,
        ApplicationMethod applicationMethod, List<String> skills, int applicationCount, boolean own,
        Instant publishedAt, Instant createdAt) {
}
