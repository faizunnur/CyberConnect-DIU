package edu.diu.cyberconnect.profile.application;

import edu.diu.cyberconnect.common.model.MessagingPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Public API of the profile module. Every viewer-facing read applies the owner's privacy settings. */
public interface ProfileService {

    /** Creates the profile row at registration (idempotent). */
    void ensureProfile(UUID userId, String displayName);

    ProfileView getOwn(UUID userId);

    /** Privacy-aware view; blocked, non-verified or unknown members all answer 404 (uniform). */
    ProfileView view(UUID viewerId, UUID targetId);

    ProfileView update(UUID userId, UpdateProfileCommand command);

    PrivacySettings privacy(UUID userId);

    PrivacySettings updatePrivacy(UUID userId, PrivacySettings settings);

    List<SkillView> replaceSkills(UUID userId, List<String> skillNames);

    ExperienceView addExperience(UUID userId, ExperienceCommand command);

    ExperienceView updateExperience(UUID userId, UUID experienceId, ExperienceCommand command);

    void deleteExperience(UUID userId, UUID experienceId);

    EducationView addEducation(UUID userId, EducationCommand command);

    EducationView updateEducation(UUID userId, UUID educationId, EducationCommand command);

    void deleteEducation(UUID userId, UUID educationId);

    /** Sanitises, scans and stores a new photo; returns the refreshed own profile. */
    ProfileView updatePhoto(UUID userId, byte[] content);

    void removePhoto(UUID userId);

    /** Photo bytes if the viewer may see the member at all; empty when no photo or not visible. */
    Optional<ProfilePhoto> photo(UUID viewerId, UUID targetId);

    Page<MemberCard> search(UUID viewerId, DirectoryQuery query, Pageable pageable);

    /** Cards for known ids (used by networking lists); privacy applied per viewer, order not guaranteed. */
    Map<UUID, MemberCard> cards(UUID viewerId, Collection<UUID> userIds);

    List<SkillView> suggestSkills(String query);

    /** Normalises names, creates missing catalogue entries and returns the skills in input order (de-duplicated). */
    List<SkillView> resolveSkills(List<String> names);

    Map<UUID, SkillView> skillsByIds(Collection<UUID> skillIds);

    MessagingPolicy messagingPolicy(UUID userId);
}
