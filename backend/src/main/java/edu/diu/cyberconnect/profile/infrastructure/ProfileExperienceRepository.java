package edu.diu.cyberconnect.profile.infrastructure;

import edu.diu.cyberconnect.profile.domain.ProfileExperience;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileExperienceRepository extends JpaRepository<ProfileExperience, UUID> {

    List<ProfileExperience> findByUserIdOrderByCurrentDescStartDateDesc(UUID userId);

    Optional<ProfileExperience> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
