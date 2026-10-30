package edu.diu.cyberconnect.research.application;

import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.ResearchApplicationStatus;
import edu.diu.cyberconnect.common.model.ResearchRole;
import edu.diu.cyberconnect.common.model.ResearchStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.identity.application.AuditContext;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.SkillView;
import edu.diu.cyberconnect.research.domain.ResearchApplication;
import edu.diu.cyberconnect.research.domain.ResearchMember;
import edu.diu.cyberconnect.research.domain.ResearchProject;
import edu.diu.cyberconnect.research.domain.ResearchProjectSkill;
import edu.diu.cyberconnect.research.infrastructure.ResearchApplicationRepository;
import edu.diu.cyberconnect.research.infrastructure.ResearchMemberRepository;
import edu.diu.cyberconnect.research.infrastructure.ResearchProjectRepository;
import edu.diu.cyberconnect.research.infrastructure.ResearchProjectSkillRepository;
import edu.diu.cyberconnect.research.infrastructure.ResearchSearchRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResearchService {

    public static final int MAX_SUMMARY = 10_000;
    static final int CREATES_PER_DAY = 10;
    static final int APPLICATIONS_PER_DAY = 30;
    static final String AUDIT_REMOVED = "RESEARCH_PROJECT_REMOVED";
    static final String AUDIT_RESTORED = "RESEARCH_PROJECT_RESTORED";
    static final String TARGET = "RESEARCH_PROJECT";

    private final ResearchProjectRepository projects;
    private final ResearchProjectSkillRepository links;
    private final ResearchMemberRepository members;
    private final ResearchApplicationRepository applications;
    private final ResearchSearchRepository search;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final AuditContext auditContext;

    public ResearchService(ResearchProjectRepository projects, ResearchProjectSkillRepository links,
            ResearchMemberRepository members, ResearchApplicationRepository applications, ResearchSearchRepository search,
            ProfileService profiles, RateLimiter rateLimiter, AuditService audit, AuditContext auditContext) {
        this.projects = projects;
        this.links = links;
        this.members = members;
        this.applications = applications;
        this.search = search;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContext = auditContext;
    }

    public ResearchViews.Project create(UUID leadId, ResearchCommand command) {
        rateLimiter.check("research-create", leadId.toString(), CREATES_PER_DAY, Duration.ofDays(1));
        ResearchProject project = ResearchProject.create(leadId);
        apply(project, command);
        projects.save(project);
        members.save(ResearchMember.of(project.getId(), leadId, ResearchRole.LEAD));
        relink(project.getId(), command.skills());
        return get(leadId, project.getId());
    }

    public ResearchViews.Project update(UUID leadId, UUID id, ResearchCommand command) {
        ResearchProject project = lead(leadId, id);
        if (!project.isVisible()) {
            throw new ConflictException("PROJECT_REMOVED", "A removed project cannot be edited.");
        }
        apply(project, command);
        projects.saveAndFlush(project);
        relink(id, command.skills()); // last: the bulk delete clears the persistence context
        return get(leadId, id);
    }

    public ResearchViews.Project setStatus(UUID leadId, UUID id, ResearchStatus target) {
        ResearchProject project = lead(leadId, id);
        if (target == ResearchStatus.REMOVED || !project.isVisible()) {
            throw new ConflictException("ILLEGAL_STATUS", "Choose proposed, active, completed or archived.");
        }
        project.setStatus(target);
        return get(leadId, id);
    }

    @Transactional(readOnly = true)
    public ResearchViews.Project get(UUID viewerId, UUID id) {
        return view(viewerId, visible(viewerId, id), false);
    }

    @Transactional(readOnly = true)
    public Page<ResearchViews.Card> search(UUID viewerId, ResearchQuery query, Pageable pageable) {
        Page<UUID> ids = search.search(viewerId, query, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<UUID, ResearchProject> byId = projects.findAllById(ids.getContent()).stream()
                .collect(Collectors.toMap(ResearchProject::getId, Function.identity()));
        List<ResearchProject> ordered = ids.getContent().stream().map(byId::get).filter(p -> p != null).toList();
        return new PageImpl<>(cards(viewerId, ordered), pageable, ids.getTotalElements());
    }

    public ResearchViews.ApplicationView apply(UUID applicantId, UUID id, String rawMessage) {
        ResearchProject project = visible(applicantId, id);
        if (project.getLeadId().equals(applicantId) || members.existsById(new ResearchMember.Key(id, applicantId))) {
            throw new ConflictException("ALREADY_MEMBER", "You are already part of this project.");
        }
        if (!project.isLive() || !project.isLookingForMembers()) {
            throw new ConflictException("NOT_RECRUITING", "This project is not looking for members right now.");
        }
        if (applications.findByProjectIdAndApplicantIdAndStatus(id, applicantId, ResearchApplicationStatus.PENDING).isPresent()) {
            throw new ConflictException("ALREADY_APPLIED", "Your application is still pending.");
        }
        String message = PlainText.sanitize(rawMessage);
        if (message.length() > 2000) {
            throw new BadRequestException("BODY_TOO_LONG", "Messages may be at most 2000 characters.");
        }
        rateLimiter.check("research-apply", applicantId.toString(), APPLICATIONS_PER_DAY, Duration.ofDays(1));
        ResearchApplication application = applications.save(ResearchApplication.submit(id, applicantId,
                message.isEmpty() ? null : message));
        return applicationView(application, null, cards(applicantId, List.of(project)).get(0));
    }

    public void withdraw(UUID applicantId, UUID id) {
        ResearchApplication application = applications
                .findByProjectIdAndApplicantIdAndStatus(id, applicantId, ResearchApplicationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND", "No pending application found."));
        application.withdraw();
    }

    public ResearchViews.Project decide(UUID leadId, UUID id, UUID applicationId, boolean accept) {
        lead(leadId, id);
        ResearchApplication application = applications.findById(applicationId)
                .filter(a -> a.getProjectId().equals(id) && a.isPending())
                .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND", "Application not found."));
        application.decide(leadId, accept);
        if (accept && !members.existsById(new ResearchMember.Key(id, application.getApplicantId()))) {
            members.save(ResearchMember.of(id, application.getApplicantId(), ResearchRole.MEMBER));
            projects.adjustMembers(id, 1);
        }
        return get(leadId, id);
    }

    public ResearchViews.Project removeMember(UUID leadId, UUID id, UUID userId) {
        lead(leadId, id);
        if (userId.equals(leadId)) {
            throw new BadRequestException("LEAD_CANNOT_LEAVE", "The lead cannot be removed; archive the project instead.");
        }
        members.findById(new ResearchMember.Key(id, userId)).ifPresent(m -> {
            members.delete(m);
            projects.adjustMembers(id, -1);
        });
        return get(leadId, id);
    }

    public void leave(UUID userId, UUID id) {
        ResearchProject project = visible(userId, id);
        if (project.getLeadId().equals(userId)) {
            throw new BadRequestException("LEAD_CANNOT_LEAVE", "The lead cannot leave; archive the project instead.");
        }
        ResearchMember member = members.findById(new ResearchMember.Key(id, userId))
                .orElseThrow(() -> new NotFoundException("NOT_MEMBER", "You are not a member of this project."));
        members.delete(member);
        projects.adjustMembers(id, -1);
    }

    @Transactional(readOnly = true)
    public Page<ResearchViews.ApplicationView> myApplications(UUID applicantId, Pageable pageable) {
        Page<ResearchApplication> page = applications.findByApplicantIdOrderByCreatedAtDesc(applicantId, pageable);
        Map<UUID, ResearchProject> byId = projects.findAllById(page.map(ResearchApplication::getProjectId).toList()).stream()
                .collect(Collectors.toMap(ResearchProject::getId, Function.identity()));
        Map<UUID, ResearchViews.Card> cards = cards(applicantId, new ArrayList<>(byId.values())).stream()
                .collect(Collectors.toMap(ResearchViews.Card::id, Function.identity()));
        return page.map(a -> applicationView(a, null, cards.get(a.getProjectId())));
    }

    // Moderation ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResearchViews.Project moderatorGet(UUID moderatorId, UUID id) {
        return view(moderatorId, projects.findById(id).orElseThrow(ResearchService::notFound), true);
    }

    public ResearchViews.Project remove(UUID moderatorId, UUID id, String reason) {
        ResearchProject project = projects.findById(id).orElseThrow(ResearchService::notFound);
        if (project.isVisible()) {
            project.remove(moderatorId, reason);
            audit.record(auditContext.entryAs(moderatorId, AUDIT_REMOVED, TARGET, id,
                    Map.of("reason", reason, "leadId", project.getLeadId().toString())));
        }
        return view(moderatorId, project, true);
    }

    public ResearchViews.Project restore(UUID moderatorId, UUID id) {
        ResearchProject project = projects.findById(id).orElseThrow(ResearchService::notFound);
        if (!project.isVisible()) {
            project.restore();
            audit.record(auditContext.entryAs(moderatorId, AUDIT_RESTORED, TARGET, id, Map.of("leadId", project.getLeadId().toString())));
        }
        return view(moderatorId, project, true);
    }

    // ---------------------------------------------------------------------------------------------------------

    private ResearchProject visible(UUID viewerId, UUID id) {
        ResearchProject project = projects.findById(id).orElseThrow(ResearchService::notFound);
        if (project.getLeadId().equals(viewerId)) {
            return project;
        }
        if (!project.isVisible() || !search.leadVisible(viewerId, id)) {
            throw notFound();
        }
        return project;
    }

    private ResearchProject lead(UUID leadId, UUID id) {
        ResearchProject project = projects.findById(id).orElseThrow(ResearchService::notFound);
        if (!project.getLeadId().equals(leadId)) {
            throw notFound();
        }
        return project;
    }

    private void apply(ResearchProject project, ResearchCommand c) {
        String title = PlainText.sanitize(c.title());
        String summary = PlainText.sanitize(c.summary());
        if (title.isEmpty() || summary.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Give the project a title and a summary.");
        }
        if (summary.length() > MAX_SUMMARY) {
            throw new BadRequestException("BODY_TOO_LONG", "Summaries may be at most " + MAX_SUMMARY + " characters.");
        }
        if (c.startDate() != null && c.endDate() != null && c.endDate().isBefore(c.startDate())) {
            throw new BadRequestException("INVALID_DATE_RANGE", "The end date must not be before the start date.");
        }
        project.update(title, summary, c.lookingForMembers(), c.startDate(), c.endDate());
    }

    private void relink(UUID id, List<String> skillNames) {
        links.deleteAllFor(id);
        List<SkillView> skills = profiles.resolveSkills(skillNames == null ? List.of() : skillNames);
        links.saveAll(skills.stream().map(s -> ResearchProjectSkill.of(id, s.id())).toList());
    }

    private ResearchViews.Project view(UUID viewerId, ResearchProject p, boolean moderator) {
        boolean own = p.getLeadId().equals(viewerId);
        List<ResearchMember> memberRows = members.findByIdProjectIdOrderByJoinedAtAsc(p.getId());
        boolean member = memberRows.stream().anyMatch(m -> m.getId().getUserId().equals(viewerId));
        List<ResearchApplication> pending = own ? applications.findByProjectIdAndStatusOrderByCreatedAtAsc(p.getId(),
                ResearchApplicationStatus.PENDING) : List.of();
        Set<UUID> people = new HashSet<>();
        people.add(p.getLeadId());
        memberRows.forEach(m -> people.add(m.getId().getUserId()));
        pending.forEach(a -> people.add(a.getApplicantId()));
        Map<UUID, MemberCard> cards = profiles.cards(viewerId, people);
        List<ResearchViews.Member> memberViews = memberRows.stream()
                .map(m -> new ResearchViews.Member(cards.get(m.getId().getUserId()), m.getRole(), m.getJoinedAt())).toList();
        ResearchApplication mine = own ? null : applications
                .findByProjectIdAndApplicantIdAndStatus(p.getId(), viewerId, ResearchApplicationStatus.PENDING).orElse(null);
        boolean canApply = !own && !member && p.isLive() && p.isLookingForMembers() && mine == null;
        String removedReason = (own || moderator) && !p.isVisible() ? p.getRemovedReason() : null;
        return new ResearchViews.Project(p.getId(), cards.get(p.getLeadId()), p.getTitle(), p.getSummary(), p.getStatus(),
                p.isLookingForMembers(), p.getMemberCount(), p.getStartDate(), p.getEndDate(),
                skillsOf(List.of(p.getId())).getOrDefault(p.getId(), List.of()), memberViews, own, member, canApply,
                mine == null ? null : applicationView(mine, null, null),
                pending.stream().map(a -> applicationView(a, cards.get(a.getApplicantId()), null)).toList(),
                removedReason, p.getCreatedAt(), p.getUpdatedAt());
    }

    private List<ResearchViews.Card> cards(UUID viewerId, List<ResearchProject> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        Map<UUID, MemberCard> leads = profiles.cards(viewerId, batch.stream().map(ResearchProject::getLeadId).collect(Collectors.toSet()));
        Map<UUID, List<SkillView>> skills = skillsOf(batch.stream().map(ResearchProject::getId).toList());
        Set<UUID> memberOf = new HashSet<>(members.projectIdsOf(viewerId));
        return batch.stream().map(p -> new ResearchViews.Card(p.getId(), leads.get(p.getLeadId()), p.getTitle(), p.getStatus(),
                p.isLookingForMembers(), p.getMemberCount(),
                skills.getOrDefault(p.getId(), List.of()).stream().map(SkillView::name).toList(),
                p.getLeadId().equals(viewerId), memberOf.contains(p.getId()), p.getCreatedAt())).toList();
    }

    private Map<UUID, List<SkillView>> skillsOf(Collection<UUID> projectIds) {
        List<ResearchProjectSkill> rows = links.findByIdProjectIdIn(projectIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SkillView> byId = profiles.skillsByIds(rows.stream().map(r -> r.getId().getSkillId()).collect(Collectors.toSet()));
        Map<UUID, List<SkillView>> result = new HashMap<>();
        for (ResearchProjectSkill row : rows) {
            SkillView skill = byId.get(row.getId().getSkillId());
            if (skill != null) {
                result.computeIfAbsent(row.getId().getProjectId(), k -> new ArrayList<>()).add(skill);
            }
        }
        return result;
    }

    private static ResearchViews.ApplicationView applicationView(ResearchApplication a, MemberCard applicant,
            ResearchViews.Card project) {
        return new ResearchViews.ApplicationView(a.getId(), a.getProjectId(), applicant, a.getStatus(), a.getMessage(),
                a.getCreatedAt(), a.getDecidedAt(), project);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("PROJECT_NOT_FOUND", "Research project not found.");
    }
}
