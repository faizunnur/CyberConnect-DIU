package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.common.model.ApplicationStatus;
import java.util.UUID;

/** Cross-module view of an application as an impact source (D-12). */
public record ApplicationLink(UUID applicationId, UUID opportunityId, String title, UUID applicantId, UUID authorId,
        ApplicationStatus status) {
}
