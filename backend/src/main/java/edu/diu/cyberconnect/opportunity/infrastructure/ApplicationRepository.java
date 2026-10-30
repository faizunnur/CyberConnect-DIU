package edu.diu.cyberconnect.opportunity.infrastructure;

import edu.diu.cyberconnect.opportunity.domain.Application;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByOpportunityIdAndApplicantId(UUID opportunityId, UUID applicantId);

    Page<Application> findByOpportunityIdOrderByCreatedAtAsc(UUID opportunityId, Pageable pageable);

    Page<Application> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId, Pageable pageable);
}
