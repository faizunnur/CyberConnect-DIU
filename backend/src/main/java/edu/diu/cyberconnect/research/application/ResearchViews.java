package edu.diu.cyberconnect.research.application;

import edu.diu.cyberconnect.common.model.ResearchApplicationStatus;
import edu.diu.cyberconnect.common.model.ResearchRole;
import edu.diu.cyberconnect.common.model.ResearchStatus;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.SkillView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Views of the research hub. */
public final class ResearchViews {

    private ResearchViews() {
    }

    public record Card(UUID id, MemberCard lead, String title, ResearchStatus status, boolean lookingForMembers,
            int memberCount, List<String> skills, boolean own, boolean member, Instant createdAt) {
    }

    public record Member(MemberCard member, ResearchRole role, Instant joinedAt) {
    }

    public record ApplicationView(UUID id, UUID projectId, MemberCard applicant, ResearchApplicationStatus status,
            String message, Instant createdAt, Instant decidedAt, Card project) {
    }

    /** {@code pendingApplications} is only populated for the lead. */
    public record Project(UUID id, MemberCard lead, String title, String summary, ResearchStatus status,
            boolean lookingForMembers, int memberCount, LocalDate startDate, LocalDate endDate, List<SkillView> skills,
            List<Member> members, boolean own, boolean member, boolean canApply, ApplicationView myApplication,
            List<ApplicationView> pendingApplications, String removedReason, Instant createdAt, Instant updatedAt) {
    }
}
