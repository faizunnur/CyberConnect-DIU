package edu.diu.cyberconnect.research.application;

import edu.diu.cyberconnect.common.model.ResearchStatus;
import java.util.UUID;

public record ResearchQuery(String q, ResearchStatus status, String skill, Boolean lookingForMembers, UUID memberId) {
}
