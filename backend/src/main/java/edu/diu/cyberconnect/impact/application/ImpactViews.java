package edu.diu.cyberconnect.impact.application;

import edu.diu.cyberconnect.common.model.ImpactDecision;
import edu.diu.cyberconnect.common.model.ImpactStatus;
import edu.diu.cyberconnect.common.model.ImpactType;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImpactViews {

    private ImpactViews() {
    }

    public enum Role { BENEFICIARY, CONTRIBUTOR }

    public record TrailEntry(MemberCard actor, String role, ImpactDecision decision, String note, Instant createdAt) {
    }

    /** {@code myRole} and {@code awaitingMe} are relative to the viewer (null/false for moderators). */
    public record Record(UUID id, ImpactType type, UUID sourceId, String sourceTitle, MemberCard beneficiary,
            MemberCard contributor, UUID proposedBy, String title, String description, ImpactStatus status,
            Instant beneficiaryConfirmedAt, Instant contributorConfirmedAt, Instant confirmedAt, String disputeReason,
            String resolutionNote, Role myRole, boolean awaitingMe, List<TrailEntry> trail, Instant createdAt,
            Instant updatedAt) {
    }

    /** Something the member can propose impact from: the on-platform source and the counterpart. */
    public record SourceOption(ImpactType type, UUID sourceId, String title, MemberCard counterpart, Role myRole,
            boolean alreadyRecorded) {
    }

    public record CommunityStats(long confirmed, Map<ImpactType, Long> byType, long contributors, long beneficiaries,
            long pending, long disputed) {
    }

    public record MemberImpact(long asContributor, long asBeneficiary, Map<ImpactType, Long> contributedByType,
            List<Record> recent) {
    }
}
