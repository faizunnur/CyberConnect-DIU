package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.common.model.ReportReason;
import edu.diu.cyberconnect.common.model.ReportResolution;
import edu.diu.cyberconnect.common.model.ReportStatus;
import edu.diu.cyberconnect.common.model.ReportTargetType;
import edu.diu.cyberconnect.profile.application.MemberCard;
import java.time.Instant;
import java.util.UUID;

/**
 * Moderator view of a report. {@code contentSnapshot} is the text at report time; the {@code target*} fields show
 * the current state of the reported item (only one is set, matching {@code targetType}).
 */
public record ReportView(UUID id, ReportTargetType targetType, UUID targetId, MemberCard reporter,
        MemberCard targetOwner, ReportReason reason, String details, String contentSnapshot, ReportStatus status,
        ReportResolution resolution, String resolutionNote, long openReportsOnTarget, PostView targetPost,
        CommentView targetComment, MemberCard targetMember, Instant createdAt, Instant resolvedAt) {
}
