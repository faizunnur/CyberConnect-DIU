package edu.diu.cyberconnect.community.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ReportReason;
import edu.diu.cyberconnect.common.model.ReportResolution;
import edu.diu.cyberconnect.common.model.ReportStatus;
import edu.diu.cyberconnect.common.model.ReportTargetType;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.community.domain.Comment;
import edu.diu.cyberconnect.community.domain.Post;
import edu.diu.cyberconnect.community.domain.Report;
import edu.diu.cyberconnect.community.infrastructure.ReportRepository;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.messaging.application.MessagingService;
import edu.diu.cyberconnect.messaging.application.MessagingViews;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.ProfileView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member reports and the moderator queue. Reports carry a text snapshot so moderators can judge edited content. */
@Service
@Transactional
public class ReportService {

    static final int REPORTS_PER_HOUR = 20;
    private static final int SNAPSHOT_MAX = 2000;

    private final ReportRepository reports;
    private final PostService postService;
    private final CommentService commentService;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final MessagingService messaging;

    public ReportService(ReportRepository reports, PostService postService, CommentService commentService,
            ProfileService profiles, RateLimiter rateLimiter, AuditService audit, AuditContext auditContext,
            MessagingService messaging) {
        this.reports = reports;
        this.postService = postService;
        this.commentService = commentService;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
        this.messaging = messaging;
    }

    public UUID report(UUID reporterId, ReportTargetType targetType, UUID targetId, ReportReason reason, String details) {
        UUID ownerId;
        String snapshot;
        switch (targetType) {
            case POST -> {
                Post post = postService.visible(reporterId, targetId);
                ownerId = post.getAuthorId();
                snapshot = post.getBody();
            }
            case COMMENT -> {
                Comment comment = commentService.find(targetId);
                postService.visible(reporterId, comment.getPostId());
                if (!comment.isActive()) {
                    throw new NotFoundException("COMMENT_NOT_FOUND", "Comment not found.");
                }
                ownerId = comment.getAuthorId();
                snapshot = comment.getBody();
            }
            case USER -> {
                ProfileView profile = profiles.view(reporterId, targetId);
                ownerId = targetId;
                snapshot = profile.displayName() + (profile.headline() == null ? "" : " — " + profile.headline());
            }
            case MESSAGE -> {
                // D-14: only the reported message is captured; moderators never see the conversation.
                MessagingViews.MessageSnapshot message = messaging.snapshot(reporterId, targetId);
                ownerId = message.senderId();
                snapshot = message.body();
            }
            default -> throw new BadRequestException("UNSUPPORTED_TARGET", "This kind of content cannot be reported here.");
        }
        if (ownerId.equals(reporterId)) {
            throw new BadRequestException("SELF_REPORT", "You cannot report your own content.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, targetType, targetId, ReportStatus.OPEN)) {
            throw new ConflictException("ALREADY_REPORTED", "You already reported this; a moderator will review it.");
        }
        rateLimiter.check("report-create", reporterId.toString(), REPORTS_PER_HOUR, Duration.ofHours(1));
        String cleanDetails = PlainText.sanitize(details);
        Report report = reports.save(Report.create(reporterId, targetType, targetId, ownerId, reason,
                cleanDetails.isEmpty() ? null : cleanDetails, truncate(snapshot)));
        return report.getId();
    }

    @Transactional(readOnly = true)
    public Page<ReportView> queue(UUID moderatorId, ReportStatus status, Pageable pageable) {
        Page<Report> page = status == null ? reports.findAllByOrderByCreatedAtDesc(pageable)
                : reports.findByStatusOrderByCreatedAtAsc(status, pageable);
        return page.map(r -> view(moderatorId, r, false));
    }

    @Transactional(readOnly = true)
    public ReportView get(UUID moderatorId, UUID reportId) {
        return view(moderatorId, load(reportId), true);
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return reports.countByStatus(ReportStatus.OPEN);
    }

    public ReportView resolve(UUID moderatorId, UUID reportId, ReportResolution resolution, String note) {
        Report report = load(reportId);
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("REPORT_CLOSED", "This report was already handled.");
        }
        String cleanNote = PlainText.sanitize(note);
        if (resolution == ReportResolution.CONTENT_REMOVED) {
            String reason = cleanNote.isEmpty() ? "Removed after a member report (" + report.getReason() + ")." : cleanNote;
            switch (report.getTargetType()) {
                case POST -> postService.remove(moderatorId, report.getTargetId(), reason);
                case COMMENT -> commentService.remove(moderatorId, report.getTargetId(), reason);
                case MESSAGE -> messaging.removeByModeration(moderatorId, report.getTargetId(), reason);
                default -> throw new BadRequestException("NOT_CONTENT",
                        "Member reports are handled with member actions (warn, suspend); choose no action or dismiss here.");
            }
        }
        report.resolve(moderatorId, resolution, cleanNote.isEmpty() ? null : cleanNote);
        audit.record(auditContext.entryAs(moderatorId, CommunityAuditActions.REPORT_RESOLVED,
                CommunityAuditActions.TARGET_REPORT, reportId, Map.of("resolution", resolution.name(),
                        "targetType", report.getTargetType().name(), "targetId", report.getTargetId().toString())));
        // Other open reports on the same item are settled by the same decision.
        if (resolution == ReportResolution.CONTENT_REMOVED) {
            for (Report other : reports.findByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(),
                    ReportStatus.OPEN)) {
                other.resolve(moderatorId, resolution, "Resolved together with report " + reportId + ".");
            }
        }
        return view(moderatorId, report, true);
    }

    // ---------------------------------------------------------------------------------------------------------

    private ReportView view(UUID moderatorId, Report r, boolean withTarget) {
        List<UUID> people = new ArrayList<>();
        people.add(r.getReporterId());
        if (r.getTargetOwnerId() != null) {
            people.add(r.getTargetOwnerId());
        }
        Map<UUID, MemberCard> cards = profiles.cards(moderatorId, people);
        PostView post = null;
        CommentView comment = null;
        MemberCard member = null;
        if (withTarget) {
            try {
                switch (r.getTargetType()) {
                    case POST -> post = postService.moderatorGet(moderatorId, r.getTargetId());
                    case COMMENT -> comment = commentService.moderatorGet(moderatorId, r.getTargetId());
                    case USER -> member = cards.get(r.getTargetId());
                    default -> { }
                }
            } catch (NotFoundException e) {
                // Target vanished (account removed); the snapshot still tells the story.
            }
        }
        long open = reports.countByTargetTypeAndTargetIdAndStatus(r.getTargetType(), r.getTargetId(), ReportStatus.OPEN);
        return new ReportView(r.getId(), r.getTargetType(), r.getTargetId(), cards.get(r.getReporterId()),
                r.getTargetOwnerId() == null ? null : cards.get(r.getTargetOwnerId()), r.getReason(), r.getDetails(),
                r.getContentSnapshot(), r.getStatus(), r.getResolution(), r.getResolutionNote(), open, post, comment,
                member, r.getCreatedAt(), r.getResolvedAt());
    }

    private Report load(UUID reportId) {
        return reports.findById(reportId).orElseThrow(() -> new NotFoundException("REPORT_NOT_FOUND", "Report not found."));
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SNAPSHOT_MAX ? text : text.substring(0, SNAPSHOT_MAX) + "…";
    }
}
