package edu.diu.cyberconnect.affiliation.infrastructure;

import edu.diu.cyberconnect.affiliation.domain.UserAffiliation;
import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAffiliationRepository extends JpaRepository<UserAffiliation, UUID> {

    List<UserAffiliation> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<UserAffiliation> findByUserIdAndType(UUID userId, AffiliationType type);

    boolean existsByUserIdAndStatus(UUID userId, AffiliationStatus status);
}
