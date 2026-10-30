package edu.diu.cyberconnect.profile.infrastructure;

import edu.diu.cyberconnect.profile.domain.ProfileEducation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileEducationRepository extends JpaRepository<ProfileEducation, UUID> {

    @Query("select e from ProfileEducation e where e.userId = :userId order by e.startYear desc nulls last, e.createdAt desc")
    List<ProfileEducation> findByUserIdOrdered(@Param("userId") UUID userId);

    Optional<ProfileEducation> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
