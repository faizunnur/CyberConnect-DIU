package edu.diu.cyberconnect.impact.infrastructure;

import edu.diu.cyberconnect.impact.domain.ImpactConfirmation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpactConfirmationRepository extends JpaRepository<ImpactConfirmation, UUID> {

    List<ImpactConfirmation> findByImpactIdOrderByCreatedAtAsc(UUID impactId);
}
