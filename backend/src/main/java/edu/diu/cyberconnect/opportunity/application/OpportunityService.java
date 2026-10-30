package edu.diu.cyberconnect.opportunity.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.ApplicationStatus;
import edu.diu.cyberconnect.common.model.OpportunityStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.opportunity.domain.Application;
import edu.diu.cyberconnect.opportunity.domain.Opportunity;
import edu.diu.cyberconnect.opportunity.domain.OpportunitySkill;
import edu.diu.cyberconnect.opportunity.infrastructure.ApplicationRepository;
import edu.diu.cyberconnect.opportunity.infrastructure.OpportunityRepository;
import edu.diu.cyberconnect.opportunity.infrastructure.OpportunitySearchRepository;
import edu.diu.cyberconnect.opportunity.infrastructure.OpportunitySkillRepository;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.SkillView;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OpportunityService {

    public static final int MAX_DESCRIPTION = 10_000;
    static final int CREATES_PER_DAY = 10;
    static final int APPLICATIONS_PER_DAY = 30;
    private static final Set<ApplicationStatus> DECISIONS = Set.of(ApplicationStatus.REVIEWED, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED);

    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository links;
    private final ApplicationRepository applications;
    private final OpportunitySearchRepository search;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;
    private final Clock clock;

    public OpportunityService(OpportunityRepository opportunities, OpportunitySkillRepository links,
            ApplicationRepository applications, OpportunitySearchRepository search, ProfileService profiles,
            RateLimiter rateLimiter, AuditService audit, AuditContext auditContext, Clock clock) {
        this.opportunities = opportunities;
        this.links = links;
        this.applications = applications;
        this.search = search;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
        this.clock = clock;
    }

    public OpportunityView create(UUID authorId, OpportunityCommand command) {
        rateLimiter.check("opportunity-create", authorId.toString(), CREATES_PER_DAY, Duration.ofDays(1));
        Opportunity o = Opportunity.draft(authorId);
        apply(o, command);
        if (command.publish()) {
            requirePublishable(o);
            o.publish();
        }
        opportunities.save(o);
        relink(o.getId(), command.skills()); // last: the bulk delete clears the persistence context
        return get(authorId, o.getId());
    }

    public OpportunityView update(UUID authorId, UUID id, OpportunityCommand command) {
        Opportunity o = own(authorId, id);
        if (o.getStatus() != OpportunityStatus.DRAFT && o.getStatus() != OpportunityStatus.OPEN) {
            throw new ConflictException("OPPORTUNITY_NOT_EDITABLE", "Closed, expired or removed opportunities cannot be edited.");
        }
        apply(o, command);
        if (command.publish() && o.getStatus() == OpportunityStatus.DRAFT) {
            requirePublishable(o);
            o.publish();
        }
        opportunities.saveAndFlush(o);
        relink(id, command.skills());
        return get(authorId, id);
    }

    public OpportunityView publish(UUID authorId, UUID id) {
        Opportunity o = own(authorId, id);
        if (o.getStatus() != OpportunityStatus.DRAFT && o.getStatus() != OpportunityStatus.CLOSED) {
            throw new ConflictException("OPPORTUNITY_NOT_PUBLISHABLE", "Only drafts and closed opportunities can be (re)opened.");
        }
        requirePublishable(o);
        o.publish();
        return get(authorId, id);
    }

    public OpportunityView close(UUID authorId, UUID id) {
        Opportunity o = own(authorId, id);
        if (!o.isOpen()) {
            throw new ConflictException("OPPORTUNITY_NOT_OPEN", "Only open opportunities can be closed.");
        }
        o.close();
        return get(authorId, id);
    }

    public void deleteDraft(UUID authorId, UUID id) {
        Opportunity o = own(authorId, id);
        if (o.getStatus() != OpportunityStatus.DRAFT) {
            throw new ConflictException("OPPORTUNITY_NOT_DRAFT", "Only drafts can be deleted; close published ones instead.");
        }
        opportunities.delete(o);
    }

    @Transactional(readOnly = true)
    public OpportunityView get(UUID viewerId, UUID id) {
        Opportunity o = visible(viewerId, id);
        return view(viewerId, o, false);
    }

    @Transactional(readOnly = true)
    public Page<OpportunityCard> search(UUID viewerId, OpportunityQuery query, Pageable pageable) {
        Page<UUID> ids = search.search(viewerId, query, LocalDate.now(clock), pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<UUID, Opportunity> byId = opportunities.findAllById(ids.getContent()).stream()
                .collect(Collectors.toMap(Opportunity::getId, Function.identity()));
        List<Opportunity> ordered = ids.getContent().stream().map(byId::get).filter(o -> o != null).toList();
        return new PageImpl<>(cards(viewerId, ordered), pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<OpportunityCard> mine(UUID authorId, Pageable pageable) {
        Page<Opportunity> page = opportunities.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable);
        return new PageImpl<>(cards(authorId, page.getContent()), pageable, page.getTotalElements());
    }

    public ApplicationView apply(UUID applicantId, UUID id, String rawCoverNote) {
        Opportunity o = visible(applicantId, id);
        if (o.getAuthorId().equals(applicantId)) {
            throw new BadRequestException("OWN_OPPORTUNITY", "You cannot apply to your own opportunity.");
        }
        if (o.getApplicationMethod() != ApplicationMethod.INTERNAL) {
            throw new ConflictException("EXTERNAL_APPLICATION", "This opportunity is applied for outside the platform.");
        }
        if (!o.acceptsApplications(LocalDate.now(clock))) {
            throw new ConflictException("OPPORTUNITY_NOT_OPEN", "This opportunity no longer accepts applications.");
        }
        String note = PlainText.sanitize(rawCoverNote);
        if (note.length() > 3000) {
            throw new BadRequestException("BODY_TOO_LONG", "Cover notes may be at most 3000 characters.");
        }
        Optional<Application> existing = applications.findByOpportunityIdAndApplicantId(id, applicantId);
        if (existing.isPresent() && existing.get().isLive()) {
            throw new ConflictException("ALREADY_APPLIED", "You already applied to this opportunity.");
        }
        rateLimiter.check("opportunity-apply", applicantId.toString(), APPLICATIONS_PER_DAY, Duration.ofDays(1));
        Application application;
        if (existing.isPresent()) {
            application = existing.get();
            application.resubmit(note.isEmpty() ? null : note);
        } else {
            application = applications.save(Application.submit(id, applicantId, note.isEmpty() ? null : note));
        }
        opportunities.adjustApplications(id, 1);
        return applicationView(applicantId, application, null, cards(applicantId, List.of(opportunities.findById(id).orElseThrow())).get(0));
    }

    public void withdraw(UUID applicantId, UUID id) {
        Application application = applications.findByOpportunityIdAndApplicantId(id, applicantId)
                .filter(Application::isLive)
                .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND", "No live application found."));
        application.withdraw();
        opportunities.adjustApplications(id, -1);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationView> applicants(UUID authorId, UUID id, Pageable pageable) {
        own(authorId, id);
        Page<Application> page = applications.findByOpportunityIdOrderByCreatedAtAsc(id, pageable);
        Map<UUID, MemberCard> cards = profiles.cards(authorId, page.map(Application::getApplicantId).toList());
        return page.map(a -> applicationView(authorId, a, cards.get(a.getApplicantId()), null));
    }

    public ApplicationView decide(UUID authorId, UUID id, UUID applicationId, ApplicationStatus decision) {
        own(authorId, id);
        if (!DECISIONS.contains(decision)) {
            throw new BadRequestException("INVALID_DECISION", "Choose reviewed, shortlisted, accepted or rejected.");
        }
        Application application = applications.findById(applicationId)
                .filter(a -> a.getOpportunityId().equals(id) && a.isLive())
                .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND", "Application not found."));
        application.decide(decision);
        MemberCard applicant = profiles.cards(authorId, List.of(application.getApplicantId())).get(application.getApplicantId());
        return applicationView(authorId, application, applicant, null);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationView> myApplications(UUID applicantId, Pageable pageable) {
        Page<Application> page = applications.findByApplicantIdOrderByCreatedAtDesc(applicantId, pageable);
        Map<UUID, Opportunity> byId = opportunities.findAllById(page.map(Application::getOpportunityId).toList()).stream()
                .collect(Collectors.toMap(Opportunity::getId, Function.identity()));
        Map<UUID, OpportunityCard> cards = cards(applicantId, new ArrayList<>(byId.values())).stream()
                .collect(Collectors.toMap(OpportunityCard::id, Function.identity()));
        return page.map(a -> applicationView(applicantId, a, null, cards.get(a.getOpportunityId())));
    }

    /** Opportunities past their deadline expire; runs nightly and can be triggered by tests. */
    @Scheduled(cron = "0 15 0 * * *")
    public int expireOverdue() {
        List<Opportunity> overdue = opportunities.findByStatusAndDeadlineBefore(OpportunityStatus.OPEN, LocalDate.now(clock));
        overdue.forEach(Opportunity::expire);
        return overdue.size();
    }

    // Moderation ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OpportunityView moderatorGet(UUID moderatorId, UUID id) {
        return view(moderatorId, opportunities.findById(id).orElseThrow(OpportunityService::notFound), true);
    }

    public OpportunityView remove(UUID moderatorId, UUID id, String reason) {
        Opportunity o = opportunities.findById(id).orElseThrow(OpportunityService::notFound);
        if (o.getStatus() != OpportunityStatus.REMOVED) {
            o.remove(moderatorId, reason);
            audit.record(auditContext.entryAs(moderatorId, OpportunityAuditActions.REMOVED, OpportunityAuditActions.TARGET, id,
                    Map.of("reason", reason, "authorId", o.getAuthorId().toString())));
        }
        return view(moderatorId, o, true);
    }

    public OpportunityView restore(UUID moderatorId, UUID id) {
        Opportunity o = opportunities.findById(id).orElseThrow(OpportunityService::notFound);
        if (o.getStatus() == OpportunityStatus.REMOVED) {
            o.restore();
            audit.record(auditContext.entryAs(moderatorId, OpportunityAuditActions.RESTORED, OpportunityAuditActions.TARGET, id,
                    Map.of("authorId", o.getAuthorId().toString())));
        }
        return view(moderatorId, o, true);
    }

    // ---------------------------------------------------------------------------------------------------------

    private Opportunity visible(UUID viewerId, UUID id) {
        Opportunity o = opportunities.findById(id).orElseThrow(OpportunityService::notFound);
        if (o.getAuthorId().equals(viewerId)) {
            return o;
        }
        boolean published = o.getStatus() == OpportunityStatus.OPEN || o.getStatus() == OpportunityStatus.CLOSED
                || o.getStatus() == OpportunityStatus.EXPIRED;
        if (!published || !search.authorVisible(viewerId, id)) {
            throw notFound();
        }
        return o;
    }

    private Opportunity own(UUID authorId, UUID id) {
        Opportunity o = opportunities.findById(id).orElseThrow(OpportunityService::notFound);
        if (!o.getAuthorId().equals(authorId)) {
            throw notFound();
        }
        return o;
    }

    private void apply(Opportunity o, OpportunityCommand c) {
        String description = PlainText.sanitize(c.description());
        if (description.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Describe the opportunity.");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new BadRequestException("BODY_TOO_LONG", "Descriptions may be at most " + MAX_DESCRIPTION + " characters.");
        }
        ApplicationMethod method = c.applicationMethod() == null ? ApplicationMethod.INTERNAL : c.applicationMethod();
        String url = trimToNull(c.applicationUrl());
        String email = trimToNull(c.applicationEmail());
        if (method == ApplicationMethod.EXTERNAL_URL && (url == null || !url.matches("(?i)^https?://.+"))) {
            throw new BadRequestException("APPLICATION_URL_REQUIRED", "Provide an http(s) application link.");
        }
        if (method == ApplicationMethod.EMAIL && (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw new BadRequestException("APPLICATION_EMAIL_REQUIRED", "Provide a valid application email address.");
        }
        o.update(c.type(), PlainText.sanitize(c.title()), PlainText.sanitize(c.organization()),
                c.locationType() == null ? edu.diu.cyberconnect.common.model.LocationType.ONSITE : c.locationType(),
                trimToNull(c.location()), description, trimToNull(c.compensation()), c.deadline(), method, url, email);
    }

    private void requirePublishable(Opportunity o) {
        if (o.getDeadline() != null && o.getDeadline().isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("DEADLINE_PASSED", "The deadline is in the past.");
        }
    }

    private void relink(UUID id, List<String> skillNames) {
        links.deleteAllFor(id);
        List<SkillView> skills = profiles.resolveSkills(skillNames == null ? List.of() : skillNames);
        links.saveAll(skills.stream().map(s -> OpportunitySkill.of(id, s.id())).toList());
    }

    private OpportunityView view(UUID viewerId, Opportunity o, boolean moderator) {
        boolean own = o.getAuthorId().equals(viewerId);
        MemberCard author = profiles.cards(viewerId, List.of(o.getAuthorId())).get(o.getAuthorId());
        List<SkillView> skills = skillsOf(List.of(o.getId())).getOrDefault(o.getId(), List.of());
        Application mine = own ? null : applications.findByOpportunityIdAndApplicantId(o.getId(), viewerId).orElse(null);
        ApplicationView myApplication = mine == null ? null : applicationView(viewerId, mine, null, null);
        boolean canApply = !own && o.acceptsApplications(LocalDate.now(clock)) && (mine == null || !mine.isLive());
        String removedReason = (own || moderator) && o.getStatus() == OpportunityStatus.REMOVED ? o.getRemovedReason() : null;
        return new OpportunityView(o.getId(), author, o.getType(), o.getTitle(), o.getOrganization(), o.getLocationType(),
                o.getLocation(), o.getDescription(), o.getCompensation(), o.getDeadline(), o.getStatus(),
                o.getApplicationMethod(), o.getApplicationUrl(), o.getApplicationEmail(), skills, o.getApplicationCount(),
                own, canApply, myApplication, removedReason, o.getPublishedAt(), o.getClosedAt(), o.getCreatedAt(),
                o.getUpdatedAt());
    }

    private List<OpportunityCard> cards(UUID viewerId, List<Opportunity> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        Map<UUID, MemberCard> authors = profiles.cards(viewerId, batch.stream().map(Opportunity::getAuthorId).collect(Collectors.toSet()));
        Map<UUID, List<SkillView>> skills = skillsOf(batch.stream().map(Opportunity::getId).toList());
        return batch.stream().map(o -> new OpportunityCard(o.getId(), authors.get(o.getAuthorId()), o.getType(), o.getTitle(),
                o.getOrganization(), o.getLocationType(), o.getLocation(), o.getDeadline(), o.getStatus(),
                o.getApplicationMethod(), skills.getOrDefault(o.getId(), List.of()).stream().map(SkillView::name).toList(),
                o.getApplicationCount(), o.getAuthorId().equals(viewerId), o.getPublishedAt(), o.getCreatedAt())).toList();
    }

    private Map<UUID, List<SkillView>> skillsOf(Collection<UUID> opportunityIds) {
        List<OpportunitySkill> rows = links.findByIdOpportunityIdIn(opportunityIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SkillView> byId = profiles.skillsByIds(rows.stream().map(r -> r.getId().getSkillId()).collect(Collectors.toSet()));
        Map<UUID, List<SkillView>> result = new HashMap<>();
        for (OpportunitySkill row : rows) {
            SkillView skill = byId.get(row.getId().getSkillId());
            if (skill != null) {
                result.computeIfAbsent(row.getId().getOpportunityId(), k -> new ArrayList<>()).add(skill);
            }
        }
        return result;
    }

    private static ApplicationView applicationView(UUID viewerId, Application a, MemberCard applicant, OpportunityCard card) {
        return new ApplicationView(a.getId(), a.getOpportunityId(), applicant, a.getStatus(), a.getCoverNote(), a.getCreatedAt(),
                a.getDecidedAt(), card);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = PlainText.sanitize(value);
        return t.isEmpty() ? null : t;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("OPPORTUNITY_NOT_FOUND", "Opportunity not found.");
    }
}
