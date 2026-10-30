package edu.diu.cyberconnect.research.application;

import edu.diu.cyberconnect.common.model.ResearchStatus;
import java.util.Set;
import java.util.UUID;

/** Cross-module view of a project as an impact source (D-12). */
public record ResearchLink(UUID projectId, String title, ResearchStatus status, UUID leadId, Set<UUID> memberIds) {
}
