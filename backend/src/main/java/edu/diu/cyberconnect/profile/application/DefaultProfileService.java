package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.affiliation.application.AffiliationSummary;
import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.model.MessagingPolicy;
import edu.diu.cyberconnect.common.model.ProfileVisibility;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.profile.domain.ProfileEducation;
import edu.diu.cyberconnect.profile.domain.ProfileExperience;
import edu.diu.cyberconnect.profile.domain.Skill;
import edu.diu.cyberconnect.profile.domain.UserProfile;
import edu.diu.cyberconnect.profile.domain.UserSkill;
import edu.diu.cyberconnect.profile.infrastructure.DirectorySearchRepository;
import edu.diu.cyberconnect.profile.infrastructure.ProfileEducationRepository;
import edu.diu.cyberconnect.profile.infrastructure.ProfileExperienceRepository;
import edu.diu.cyberconnect.profile.infrastructure.SkillRepository;
import edu.diu.cyberconnect.profile.infrastructure.UserProfileRepository;
import edu.diu.cyberconnect.profile.infrastructure.UserSkillRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultProfileService implements ProfileService {

    static final int MAX_SKILLS = 30;
    static final int MAX_EXPERIENCES = 30;
    static final int MAX_EDUCATIONS = 15;
    static final int MAX_SPECIALIZATIONS = 10;

    private final UserProfileRepository profiles;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final ProfileExperienceRepository experiences;
    private final ProfileEducationRepository educations;
    private final DirectorySearchRepository directory;
    private final ProfilePhotoService photos;
    private final UserAccounts accounts;
    private final AffiliationService affiliations;
    private final RelationshipLookup relationships;

    public DefaultProfileService(UserProfileRepository profiles, SkillRepository skills, UserSkillRepository userSkills,
            ProfileExperienceRepository experiences, ProfileEducationRepository educations,
            DirectorySearchRepository directory, ProfilePhotoService photos, UserAccounts accounts,
            AffiliationService affiliations, @Lazy RelationshipLookup relationships) {
        this.profiles = profiles;
        this.skills = skills;
        this.userSkills = userSkills;
        this.experiences = experiences;
        this.educations = educations;
        this.directory = directory;
        this.photos = photos;
        this.accounts = accounts;
        this.affiliations = affiliations;
        this.relationships = relationships;
    }

    @Override
    public void ensureProfile(UUID userId, String displayName) {
        if (!profiles.existsById(userId)) {
            profiles.save(UserProfile.create(userId, displayName));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileView getOwn(UUID userId) {
        UserProfile profile = own(userId);
        UserSummary user = accounts.get(userId);
        return full(profile, user, user.email(), true, RelationshipView.ofSelf(), privacyOf(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileView view(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            return getOwn(viewerId);
        }
        UserSummary target = visibleMember(viewerId, targetId);
        Relationship rel = relationships.between(viewerId, targetId);
        if (rel.blockedEitherWay()) {
            throw notFound();
        }
        UserProfile profile = profiles.findById(targetId).orElseThrow(DefaultProfileService::notFound);
        RelationshipView relationship = new RelationshipView(false, rel.connected(), rel.pendingIncoming(),
                rel.pendingOutgoing(), rel.requestId());
        boolean connected = rel.connected();
        if (profile.getProfileVisibility() == ProfileVisibility.CONNECTIONS_ONLY && !connected) {
            return new ProfileView(profile.getUserId(), profile.getDisplayName(), profile.getHeadline(), null, null,
                    null, null, null, List.of(), null, null, photoUrl(profile), null, verifiedAffiliations(targetId),
                    List.of(), List.of(), List.of(), target.createdAt(), true, relationship, null);
        }
        String email = profile.getEmailVisibility().allows(connected) ? target.email() : null;
        return full(profile, target, email, connected, relationship, null);
    }

    @Override
    public ProfileView update(UUID userId, UpdateProfileCommand command) {
        UserProfile profile = own(userId);
        List<String> specializations = normalizeList(command.specializations(), MAX_SPECIALIZATIONS, 60);
        profile.update(command.displayName(), command.headline(), command.bio(), command.organization(),
                command.jobTitle(), command.location(), command.country(), specializations, command.mentorAvailable(),
                command.mentorTopics());
        return getOwn(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PrivacySettings privacy(UUID userId) {
        return privacyOf(own(userId));
    }

    @Override
    public PrivacySettings updatePrivacy(UUID userId, PrivacySettings settings) {
        UserProfile profile = own(userId);
        profile.updatePrivacy(settings.profileVisibility(), settings.emailVisibility(),
                settings.organizationVisibility(), settings.locationVisibility(), settings.mentorVisibility(),
                settings.searchable(), settings.messagingPolicy());
        return privacyOf(profile);
    }

    @Override
    public List<SkillView> replaceSkills(UUID userId, List<String> skillNames) {
        own(userId);
        LinkedHashMap<String, String> bySlug = new LinkedHashMap<>();
        for (String raw : skillNames == null ? List.<String>of() : skillNames) {
            String name = Skill.normalizeName(raw);
            String slug = Skill.slugOf(name);
            if (slug.isEmpty()) {
                continue;
            }
            if (name.length() > 60) {
                throw new BadRequestException("SKILL_TOO_LONG", "Skill names may be at most 60 characters.");
            }
            bySlug.putIfAbsent(slug, name);
        }
        if (bySlug.size() > MAX_SKILLS) {
            throw new BadRequestException("TOO_MANY_SKILLS", "You can list at most " + MAX_SKILLS + " skills.");
        }
        Map<String, Skill> existing = skills.findBySlugIn(bySlug.keySet()).stream()
                .collect(Collectors.toMap(Skill::getSlug, Function.identity()));
        userSkills.deleteAllForUser(userId);
        List<UserSkill> links = new ArrayList<>();
        List<SkillView> result = new ArrayList<>();
        int position = 0;
        for (Map.Entry<String, String> entry : bySlug.entrySet()) {
            Skill skill = existing.get(entry.getKey());
            if (skill == null) {
                skill = skills.save(Skill.create(entry.getValue()));
            }
            links.add(UserSkill.of(userId, skill.getId(), position++));
            result.add(toView(skill));
        }
        userSkills.saveAll(links);
        return result;
    }

    @Override
    public ExperienceView addExperience(UUID userId, ExperienceCommand command) {
        own(userId);
        if (experiences.countByUserId(userId) >= MAX_EXPERIENCES) {
            throw new BadRequestException("TOO_MANY_ENTRIES", "You can list at most " + MAX_EXPERIENCES + " experiences.");
        }
        validate(command);
        ProfileExperience experience = ProfileExperience.create(userId);
        apply(experience, command);
        return toView(experiences.save(experience));
    }

    @Override
    public ExperienceView updateExperience(UUID userId, UUID experienceId, ExperienceCommand command) {
        ProfileExperience experience = experiences.findByIdAndUserId(experienceId, userId)
                .orElseThrow(() -> new NotFoundException("ENTRY_NOT_FOUND", "Experience not found."));
        validate(command);
        apply(experience, command);
        return toView(experience);
    }

    @Override
    public void deleteExperience(UUID userId, UUID experienceId) {
        ProfileExperience experience = experiences.findByIdAndUserId(experienceId, userId)
                .orElseThrow(() -> new NotFoundException("ENTRY_NOT_FOUND", "Experience not found."));
        experiences.delete(experience);
    }

    @Override
    public EducationView addEducation(UUID userId, EducationCommand command) {
        own(userId);
        if (educations.countByUserId(userId) >= MAX_EDUCATIONS) {
            throw new BadRequestException("TOO_MANY_ENTRIES", "You can list at most " + MAX_EDUCATIONS + " education entries.");
        }
        validate(command);
        ProfileEducation education = ProfileEducation.create(userId);
        apply(education, command);
        return toView(educations.save(education));
    }

    @Override
    public EducationView updateEducation(UUID userId, UUID educationId, EducationCommand command) {
        ProfileEducation education = educations.findByIdAndUserId(educationId, userId)
                .orElseThrow(() -> new NotFoundException("ENTRY_NOT_FOUND", "Education entry not found."));
        validate(command);
        apply(education, command);
        return toView(education);
    }

    @Override
    public void deleteEducation(UUID userId, UUID educationId) {
        ProfileEducation education = educations.findByIdAndUserId(educationId, userId)
                .orElseThrow(() -> new NotFoundException("ENTRY_NOT_FOUND", "Education entry not found."));
        educations.delete(education);
    }

    @Override
    public ProfileView updatePhoto(UUID userId, byte[] content) {
        UserProfile profile = own(userId);
        String previous = profile.getPhotoKey();
        String key = photos.store(userId, content);
        profile.setPhoto(key);
        photos.delete(previous);
        return getOwn(userId);
    }

    @Override
    public void removePhoto(UUID userId) {
        UserProfile profile = own(userId);
        String previous = profile.getPhotoKey();
        if (previous != null) {
            profile.clearPhoto();
            photos.delete(previous);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfilePhoto> photo(UUID viewerId, UUID targetId) {
        if (!viewerId.equals(targetId)) {
            visibleMember(viewerId, targetId);
            if (relationships.between(viewerId, targetId).blockedEitherWay()) {
                return Optional.empty();
            }
        }
        return profiles.findById(targetId).flatMap(p -> photos.load(p.getPhotoKey()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MemberCard> search(UUID viewerId, DirectoryQuery query, Pageable pageable) {
        return directory.search(viewerId, query, pageable).map(this::toCard);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MemberCard> cards(UUID viewerId, Collection<UUID> userIds) {
        Set<UUID> ids = new LinkedHashSet<>(userIds);
        return directory.byIds(viewerId, ids).stream()
                .collect(Collectors.toMap(DirectorySearchRepository.Row::userId, this::toCard, (a, b) -> a,
                        LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillView> suggestSkills(String query) {
        String q = query == null ? "" : query.trim();
        List<Skill> found = q.isEmpty() ? skills.findTop10ByOrderByNameAsc()
                : skills.findTop10ByNameContainingIgnoreCaseOrderByNameAsc(q);
        return found.stream().map(DefaultProfileService::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessagingPolicy messagingPolicy(UUID userId) {
        return profiles.findById(userId).map(UserProfile::getMessagingPolicy).orElse(MessagingPolicy.CONNECTIONS_ONLY);
    }

    // ---------------------------------------------------------------------------------------------------------

    private UserProfile own(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> {
            UserSummary user = accounts.get(userId);
            return profiles.save(UserProfile.create(userId, user.fullName()));
        });
    }

    /** The target must exist and be a verified member; everything else is a uniform 404. */
    private UserSummary visibleMember(UUID viewerId, UUID targetId) {
        UserSummary target;
        try {
            target = accounts.get(targetId);
        } catch (NotFoundException e) {
            throw notFound();
        }
        if (target.accountStatus() != AccountStatus.VERIFIED) {
            throw notFound();
        }
        return target;
    }

    private ProfileView full(UserProfile profile, UserSummary user, String email, boolean connected,
            RelationshipView relationship, PrivacySettings privacy) {
        boolean organizationVisible = relationship.self() || profile.getOrganizationVisibility().allows(connected);
        boolean locationVisible = relationship.self() || profile.getLocationVisibility().allows(connected);
        boolean mentorVisible = relationship.self() || profile.getMentorVisibility().allows(connected);
        return new ProfileView(profile.getUserId(), profile.getDisplayName(), profile.getHeadline(), profile.getBio(),
                organizationVisible ? profile.getOrganization() : null, profile.getJobTitle(),
                locationVisible ? profile.getLocation() : null, locationVisible ? profile.getCountry() : null,
                profile.getSpecializations(), mentorVisible ? profile.isMentorAvailable() : null,
                mentorVisible ? profile.getMentorTopics() : null, photoUrl(profile), email,
                verifiedAffiliations(profile.getUserId()), skillsOf(profile.getUserId()),
                experiences.findByUserIdOrderByCurrentDescStartDateDesc(profile.getUserId()).stream()
                        .map(DefaultProfileService::toView).toList(),
                educations.findByUserIdOrdered(profile.getUserId()).stream()
                        .map(DefaultProfileService::toView).toList(),
                user.createdAt(), false, relationship, privacy);
    }

    private MemberCard toCard(DirectorySearchRepository.Row row) {
        boolean connected = row.connected();
        return new MemberCard(row.userId(), row.displayName(), row.headline(),
                row.organizationVisibility().allows(connected) ? row.organization() : null,
                row.locationVisibility().allows(connected) ? row.location() : null,
                row.mentorVisibility().allows(connected) ? row.mentorAvailable() : null,
                photoUrl(row.userId(), row.photoKey(), row.photoVersion()), connected, row.affiliations(),
                row.skills());
    }

    private List<SkillView> skillsOf(UUID userId) {
        List<UserSkill> links = userSkills.findByIdUserIdOrderByPositionAsc(userId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, Skill> byId = skills.findAllById(links.stream().map(l -> l.getId().getSkillId()).toList()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        return links.stream().map(l -> byId.get(l.getId().getSkillId())).filter(s -> s != null)
                .map(DefaultProfileService::toView).toList();
    }

    private List<AffiliationType> verifiedAffiliations(UUID userId) {
        return affiliations.listForUser(userId).stream()
                .filter(a -> a.status() == AffiliationStatus.VERIFIED)
                .map(AffiliationSummary::type)
                .toList();
    }

    private PrivacySettings privacyOf(UserProfile p) {
        return new PrivacySettings(p.getProfileVisibility(), p.getEmailVisibility(), p.getOrganizationVisibility(),
                p.getLocationVisibility(), p.getMentorVisibility(), p.isSearchable(), p.getMessagingPolicy());
    }

    private static String photoUrl(UserProfile profile) {
        return photoUrl(profile.getUserId(), profile.getPhotoKey(), profile.getPhotoVersion());
    }

    /** Same-origin API path streamed by the backend; never a storage URL. */
    private static String photoUrl(UUID userId, String photoKey, int version) {
        return photoKey == null ? null : "/api/v1/members/" + userId + "/photo?v=" + version;
    }

    private static void validate(ExperienceCommand c) {
        if (!c.current() && c.endDate() == null) {
            throw new BadRequestException("END_DATE_REQUIRED", "Provide an end date or mark the role as current.");
        }
        if (c.endDate() != null && c.endDate().isBefore(c.startDate())) {
            throw new BadRequestException("INVALID_DATE_RANGE", "The end date must not be before the start date.");
        }
    }

    private static void validate(EducationCommand c) {
        if (c.startYear() != null && c.endYear() != null && c.endYear() < c.startYear()) {
            throw new BadRequestException("INVALID_DATE_RANGE", "The end year must not be before the start year.");
        }
    }

    private static void apply(ProfileExperience e, ExperienceCommand c) {
        e.update(c.title(), c.organization(), c.employmentType(), c.location(), c.startDate(), c.endDate(),
                c.current(), c.description());
    }

    private static void apply(ProfileEducation e, EducationCommand c) {
        e.update(c.institution(), c.degree(), c.fieldOfStudy(), c.startYear(), c.endYear(), c.description());
    }

    /** Trims, collapses whitespace and de-duplicates case-insensitively, keeping the first spelling. */
    private static List<String> normalizeList(List<String> values, int maxItems, int maxLength) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String value : values == null ? List.<String>of() : values) {
            String v = Skill.normalizeName(value);
            if (v.isEmpty()) {
                continue;
            }
            if (v.length() > maxLength) {
                throw new BadRequestException("VALUE_TOO_LONG", "Entries may be at most " + maxLength + " characters.");
            }
            result.putIfAbsent(v.toLowerCase(java.util.Locale.ROOT), v);
        }
        if (result.size() > maxItems) {
            throw new BadRequestException("TOO_MANY_ENTRIES", "You can list at most " + maxItems + " entries.");
        }
        return new ArrayList<>(result.values());
    }

    private static SkillView toView(Skill s) {
        return new SkillView(s.getId(), s.getName(), s.getSlug());
    }

    private static ExperienceView toView(ProfileExperience e) {
        return new ExperienceView(e.getId(), e.getTitle(), e.getOrganization(), e.getEmploymentType(), e.getLocation(),
                e.getStartDate(), e.getEndDate(), e.isCurrent(), e.getDescription());
    }

    private static EducationView toView(ProfileEducation e) {
        return new EducationView(e.getId(), e.getInstitution(), e.getDegree(), e.getFieldOfStudy(), e.getStartYear(),
                e.getEndYear(), e.getDescription());
    }

    private static NotFoundException notFound() {
        return new NotFoundException("MEMBER_NOT_FOUND", "Member not found.");
    }
}
