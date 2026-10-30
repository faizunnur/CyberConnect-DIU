package edu.diu.cyberconnect.impact.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ApplicationStatus;
import edu.diu.cyberconnect.common.model.ImpactDecision;
import edu.diu.cyberconnect.common.model.ImpactStatus;
import edu.diu.cyberconnect.common.model.ImpactType;
import edu.diu.cyberconnect.common.model.MentorshipStatus;
import edu.diu.cyberconnect.common.model.NotificationType;
import edu.diu.cyberconnect.common.model.ResearchStatus;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.impact.application.ImpactViews.Role;
import edu.diu.cyberconnect.impact.domain.ImpactConfirmation;
import edu.diu.cyberconnect.impact.domain.ImpactRecord;
import edu.diu.cyberconnect.impact.infrastructure.ImpactConfirmationRepository;
import edu.diu.cyberconnect.impact.infrastructure.ImpactRecordRepository;
import edu.diu.cyberconnect.networking.application.MentorshipLink;
import edu.diu.cyberconnect.networking.application.MentorshipService;
import edu.diu.cyberconnect.notification.application.NotificationService;
import edu.diu.cyberconnect.opportunity.application.ApplicationLink;
import edu.diu.cyberconnect.opportunity.application.OpportunityService;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.research.application.ResearchLink;
import edu.diu.cyberconnect.research.application.ResearchService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmed impact (D-12): a record references exactly one on-platform source both parties are linked to; the
 * proposer's side is confirmed at proposal time, the counterpart confirms or disputes, a moderator resolves disputes.
 * Statistics only ever count CONFIRMED records and nothing here is editable by clients.
 */
@Service
@Transactional
public class ImpactService {

    static final String AUDIT_RESOLVED = "IMPACT_DISPUTE_RESOLVED";
    static final String TARGET = "IMPACT_RECORD";

    private final ImpactRecordRepository records;
    private final ImpactConfirmationRepository confirmations;
    private final OpportunityService opportunities;
    private final ResearchService research;
    private final MentorshipService mentorship;
    private final ProfileService profiles;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AuditContext auditContext;

    public ImpactService(ImpactRecordRepository records, ImpactConfirmationRepository confirmations,
            OpportunityService opportunities, ResearchService research, MentorshipService mentorship,
            ProfileService profiles, NotificationService notifications, AuditService audit, AuditContext auditContext) {
        this.records = records;
        this.confirmations = confirmations;
        this.opportunities = opportunities;
        this.research = research;
        this.mentorship = mentorship;
        this.profiles = profiles;
        this.notifications = notifications;
        this.audit = audit;
        this.auditContext = auditContext;
    }

    /** Sources the member may propose impact from, with the counterpart already resolved. */
    @Transactional(readOnly = true)
    public List<ImpactViews.SourceOption> sources(UUID userId) {
        List<ImpactViews.SourceOption> options = new ArrayList<>();
        for (ApplicationLink link : opportunities.acceptedApplicationsInvolving(userId)) {
            boolean beneficiary = link.applicantId().equals(userId);
            UUID counterpart = beneficiary ? link.authorId() : link.applicantId();
            options.add(option(ImpactType.JOB_REFERRAL, link.applicationId(), link.title(), userId, counterpart,
                    beneficiary ? Role.BENEFICIARY : Role.CONTRIBUTOR, beneficiary ? userId : counterpart, beneficiary ? counterpart : userId));
        }
        for (ResearchLink link : research.projectsInvolving(userId)) {
            if (link.leadId().equals(userId)) {
                for (UUID member : link.memberIds()) {
                    if (!member.equals(userId)) {
                        options.add(option(ImpactType.RESEARCH, link.projectId(), link.title(), userId, member,
                                Role.CONTRIBUTOR, member, userId));
                    }
                }
            } else {
                options.add(option(ImpactType.RESEARCH, link.projectId(), link.title(), userId, link.leadId(),
                        Role.BENEFICIARY, userId, link.leadId()));
            }
        }
        for (MentorshipLink link : mentorship.completedInvolving(userId)) {
            boolean beneficiary = link.menteeId().equals(userId);
            UUID counterpart = beneficiary ? link.mentorId() : link.menteeId();
            options.add(option(ImpactType.MENTORSHIP, link.requestId(), link.topic(), userId, counterpart,
                    beneficiary ? Role.BENEFICIARY : Role.CONTRIBUTOR, link.menteeId(), link.mentorId()));
        }
        return options;
    }

    public ImpactViews.Record propose(UUID proposerId, ImpactType type, UUID sourceId, UUID counterpartId,
            String rawTitle, String rawDescription) {
        String title = PlainText.sanitize(rawTitle);
        String description = PlainText.sanitize(rawDescription);
        if (title.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Describe the impact in a short title.");
        }
        if (title.length() > 200 || description.length() > 2000) {
            throw new BadRequestException("BODY_TOO_LONG", "Keep the title under 200 and the description under 2000 characters.");
        }
        Parties parties = resolveParties(type, sourceId, proposerId, counterpartId);
        if (!records.live(type, sourceId, parties.beneficiaryId(), parties.contributorId()).isEmpty()) {
            throw new ConflictException("IMPACT_EXISTS", "An impact record for this already exists.");
        }
        ImpactRecord record = records.save(ImpactRecord.propose(type, sourceId, parties.beneficiaryId(),
                parties.contributorId(), proposerId, title, description.isEmpty() ? null : description));
        confirmations.save(ImpactConfirmation.of(record.getId(), proposerId, roleOf(record, proposerId),
                ImpactDecision.PROPOSE, null));
        UUID counterpart = record.counterpartOf(proposerId);
        notifications.notify(counterpart, NotificationType.IMPACT_PROPOSED, "Please confirm an impact record: " + title,
                "Confirm or dispute it so it can count.", "/impact", proposerId);
        return view(proposerId, record, false);
    }

    public ImpactViews.Record confirm(UUID userId, UUID id) {
        ImpactRecord record = awaiting(userId, id);
        record.confirm(userId);
        confirmations.save(ImpactConfirmation.of(id, userId, roleOf(record, userId), ImpactDecision.CONFIRM, null));
        notifications.notify(record.getProposedBy(), NotificationType.IMPACT_RESOLVED, "Impact confirmed: " + record.getTitle(),
                null, "/impact", userId);
        return view(userId, record, false);
    }

    public ImpactViews.Record dispute(UUID userId, UUID id, String rawReason) {
        ImpactRecord record = awaiting(userId, id);
        String reason = PlainText.sanitize(rawReason);
        if (reason.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Explain why you dispute this record.");
        }
        record.dispute(userId, reason);
        confirmations.save(ImpactConfirmation.of(id, userId, roleOf(record, userId), ImpactDecision.DISPUTE, reason));
        notifications.notify(record.getProposedBy(), NotificationType.IMPACT_RESOLVED, "Impact disputed: " + record.getTitle(),
                "A moderator will review it.", "/impact", userId);
        return view(userId, record, false);
    }

    public ImpactViews.Record withdraw(UUID userId, UUID id) {
        ImpactRecord record = party(userId, id);
        if (!record.getProposedBy().equals(userId) || record.getStatus() != ImpactStatus.PENDING_CONFIRMATION) {
            throw new ConflictException("NOT_WITHDRAWABLE", "Only a pending record can be withdrawn by its proposer.");
        }
        record.withdraw();
        confirmations.save(ImpactConfirmation.of(id, userId, roleOf(record, userId), ImpactDecision.WITHDRAW, null));
        return view(userId, record, false);
    }

    @Transactional(readOnly = true)
    public Page<ImpactViews.Record> mine(UUID userId, ImpactStatus status, Pageable pageable) {
        return records.involving(userId, status, pageable).map(r -> view(userId, r, false));
    }

    @Transactional(readOnly = true)
    public ImpactViews.Record get(UUID userId, UUID id) {
        return view(userId, party(userId, id), false);
    }

    @Transactional(readOnly = true)
    public ImpactViews.CommunityStats communityStats() {
        return new ImpactViews.CommunityStats(records.countByStatus(ImpactStatus.CONFIRMED), byType(records.confirmedByType()),
                records.distinctContributors(), records.distinctBeneficiaries(),
                records.countByStatus(ImpactStatus.PENDING_CONFIRMATION), records.countByStatus(ImpactStatus.DISPUTED));
    }

    /** Confirmed impact shown on a profile; the profile must be visible to the viewer. */
    @Transactional(readOnly = true)
    public ImpactViews.MemberImpact memberImpact(UUID viewerId, UUID memberId) {
        if (!viewerId.equals(memberId)) {
            profiles.view(viewerId, memberId); // 404 when hidden or blocked
        }
        Map<ImpactType, Long> byType = byType(records.confirmedAsContributorByType(memberId));
        long asContributor = byType.values().stream().mapToLong(Long::longValue).sum();
        List<ImpactViews.Record> recent = records.confirmedInvolving(memberId, PageRequest.of(0, 20)).stream()
                .map(r -> view(viewerId, r, false)).toList();
        return new ImpactViews.MemberImpact(asContributor, records.confirmedAsBeneficiary(memberId), byType, recent);
    }

    // Moderation ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ImpactViews.Record> disputes(UUID moderatorId, Pageable pageable) {
        return records.findByStatusOrderByUpdatedAtAsc(ImpactStatus.DISPUTED, pageable).map(r -> view(moderatorId, r, true));
    }

    @Transactional(readOnly = true)
    public ImpactViews.Record moderatorGet(UUID moderatorId, UUID id) {
        return view(moderatorId, records.findById(id).orElseThrow(ImpactService::notFound), true);
    }

    public ImpactViews.Record resolve(UUID moderatorId, UUID id, boolean confirmed, String rawNote) {
        ImpactRecord record = records.findById(id).orElseThrow(ImpactService::notFound);
        if (record.getStatus() != ImpactStatus.DISPUTED) {
            throw new ConflictException("NOT_DISPUTED", "Only disputed records are resolved by moderators.");
        }
        String note = PlainText.sanitize(rawNote);
        record.resolve(moderatorId, confirmed, note.isEmpty() ? null : note);
        confirmations.save(ImpactConfirmation.of(id, moderatorId, ImpactConfirmation.Role.MODERATOR,
                confirmed ? ImpactDecision.RESOLVE_CONFIRMED : ImpactDecision.RESOLVE_REJECTED, note.isEmpty() ? null : note));
        audit.record(auditContext.entryAs(moderatorId, AUDIT_RESOLVED, TARGET, id, Map.of("confirmed", confirmed,
                "beneficiaryId", record.getBeneficiaryId().toString(), "contributorId", record.getContributorId().toString())));
        String title = (confirmed ? "Impact confirmed after review: " : "Impact rejected after review: ") + record.getTitle();
        notifications.notify(record.getBeneficiaryId(), NotificationType.IMPACT_RESOLVED, title, note.isEmpty() ? null : note, "/impact", null);
        notifications.notify(record.getContributorId(), NotificationType.IMPACT_RESOLVED, title, note.isEmpty() ? null : note, "/impact", null);
        return view(moderatorId, record, true);
    }

    // ---------------------------------------------------------------------------------------------------------

    private record Parties(UUID beneficiaryId, UUID contributorId) {
    }

    private Parties resolveParties(ImpactType type, UUID sourceId, UUID proposerId, UUID counterpartId) {
        switch (type) {
            case JOB_REFERRAL -> {
                ApplicationLink link = opportunities.applicationLink(sourceId).orElseThrow(ImpactService::sourceNotFound);
                if (link.status() != ApplicationStatus.ACCEPTED) {
                    throw new ConflictException("SOURCE_NOT_ELIGIBLE", "Only accepted applications can be recorded as impact.");
                }
                requireParty(proposerId, link.applicantId(), link.authorId());
                return new Parties(link.applicantId(), link.authorId());
            }
            case RESEARCH -> {
                ResearchLink link = research.link(sourceId).orElseThrow(ImpactService::sourceNotFound);
                if (link.status() != ResearchStatus.ACTIVE && link.status() != ResearchStatus.COMPLETED) {
                    throw new ConflictException("SOURCE_NOT_ELIGIBLE", "Only active or completed projects can be recorded as impact.");
                }
                if (link.leadId().equals(proposerId)) {
                    if (counterpartId == null || counterpartId.equals(proposerId) || !link.memberIds().contains(counterpartId)) {
                        throw new BadRequestException("COUNTERPART_REQUIRED", "Choose the project member this impact is about.");
                    }
                    return new Parties(counterpartId, proposerId);
                }
                if (!link.memberIds().contains(proposerId)) {
                    throw sourceNotFound();
                }
                return new Parties(proposerId, link.leadId());
            }
            case MENTORSHIP -> {
                MentorshipLink link = mentorship.link(sourceId).orElseThrow(ImpactService::sourceNotFound);
                if (link.status() != MentorshipStatus.COMPLETED) {
                    throw new ConflictException("SOURCE_NOT_ELIGIBLE", "Only completed mentorships can be recorded as impact.");
                }
                requireParty(proposerId, link.menteeId(), link.mentorId());
                return new Parties(link.menteeId(), link.mentorId());
            }
            default -> throw new BadRequestException("UNSUPPORTED_TYPE", "Unknown impact type.");
        }
    }

    private static void requireParty(UUID proposerId, UUID a, UUID b) {
        if (!proposerId.equals(a) && !proposerId.equals(b)) {
            throw sourceNotFound();
        }
    }

    private ImpactRecord party(UUID userId, UUID id) {
        return records.findById(id).filter(r -> r.involves(userId)).orElseThrow(ImpactService::notFound);
    }

    private ImpactRecord awaiting(UUID userId, UUID id) {
        ImpactRecord record = party(userId, id);
        if (!record.awaiting(userId)) {
            throw new ConflictException("NOT_AWAITING_YOU", "This record is not waiting for your decision.");
        }
        return record;
    }

    private ImpactViews.SourceOption option(ImpactType type, UUID sourceId, String title, UUID userId, UUID counterpartId,
            Role myRole, UUID beneficiaryId, UUID contributorId) {
        MemberCard counterpart = profiles.cards(userId, List.of(counterpartId)).get(counterpartId);
        boolean recorded = !records.live(type, sourceId, beneficiaryId, contributorId).isEmpty();
        return new ImpactViews.SourceOption(type, sourceId, title, counterpart, myRole, recorded);
    }

    private ImpactViews.Record view(UUID viewerId, ImpactRecord r, boolean moderator) {
        Set<UUID> people = new HashSet<>(List.of(r.getBeneficiaryId(), r.getContributorId()));
        List<ImpactConfirmation> trail = confirmations.findByImpactIdOrderByCreatedAtAsc(r.getId());
        trail.forEach(c -> people.add(c.getUserId()));
        Map<UUID, MemberCard> cards = profiles.cards(viewerId, people);
        Role myRole = moderator ? null : r.getBeneficiaryId().equals(viewerId) ? Role.BENEFICIARY
                : r.getContributorId().equals(viewerId) ? Role.CONTRIBUTOR : null;
        List<ImpactViews.TrailEntry> entries = trail.stream().map(c -> new ImpactViews.TrailEntry(
                c.getRole() == ImpactConfirmation.Role.MODERATOR ? null : cards.get(c.getUserId()), c.getRole().name(),
                c.getDecision(), c.getNote(), c.getCreatedAt())).toList();
        return new ImpactViews.Record(r.getId(), r.getType(), r.sourceId(), sourceTitle(r), cards.get(r.getBeneficiaryId()),
                cards.get(r.getContributorId()), r.getProposedBy(), r.getTitle(), r.getDescription(), r.getStatus(),
                r.getBeneficiaryConfirmedAt(), r.getContributorConfirmedAt(), r.getConfirmedAt(), r.getDisputeReason(),
                r.getResolutionNote(), myRole, !moderator && r.awaiting(viewerId), entries, r.getCreatedAt(), r.getUpdatedAt());
    }

    private String sourceTitle(ImpactRecord r) {
        return switch (r.getType()) {
            case JOB_REFERRAL -> opportunities.applicationLink(r.sourceId()).map(ApplicationLink::title).orElse(null);
            case RESEARCH -> research.link(r.sourceId()).map(ResearchLink::title).orElse(null);
            case MENTORSHIP -> mentorship.link(r.sourceId()).map(MentorshipLink::topic).orElse(null);
        };
    }

    private static ImpactConfirmation.Role roleOf(ImpactRecord r, UUID userId) {
        return r.getBeneficiaryId().equals(userId) ? ImpactConfirmation.Role.BENEFICIARY : ImpactConfirmation.Role.CONTRIBUTOR;
    }

    private static Map<ImpactType, Long> byType(List<Object[]> rows) {
        Map<ImpactType, Long> result = new EnumMap<>(ImpactType.class);
        for (ImpactType type : ImpactType.values()) {
            result.put(type, 0L);
        }
        for (Object[] row : rows) {
            result.put((ImpactType) row[0], (Long) row[1]);
        }
        return result;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("IMPACT_NOT_FOUND", "Impact record not found.");
    }

    private static NotFoundException sourceNotFound() {
        return new NotFoundException("SOURCE_NOT_FOUND", "The source is not available to you.");
    }
}
