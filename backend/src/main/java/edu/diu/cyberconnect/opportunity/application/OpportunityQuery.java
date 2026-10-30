package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityType;
import java.util.UUID;

public record OpportunityQuery(String q, OpportunityType type, LocationType locationType, String skill, UUID authorId) {
}
