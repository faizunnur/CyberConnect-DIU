package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityType;
import java.time.LocalDate;
import java.util.List;

public record OpportunityCommand(OpportunityType type, String title, String organization, LocationType locationType,
        String location, String description, String compensation, LocalDate deadline, ApplicationMethod applicationMethod,
        String applicationUrl, String applicationEmail, List<String> skills, boolean publish) {
}
