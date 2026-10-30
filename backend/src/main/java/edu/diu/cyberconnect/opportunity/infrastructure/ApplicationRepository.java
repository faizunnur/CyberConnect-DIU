package edu.diu.cyberconnect.opportunity.infrastructure;

import edu.diu.cyberconnect.opportunity.domain.Application;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByOpportunityIdAndApplicantId(UUID opportunityId, UUID applicantId);

    Page<Application> findByOpportunityIdOrderByCreatedAtAsc(UUID opportunityId, Pageable pageable);

    Page<Application> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId, Pageable pageable);

    @Query("""
            select a from Application a, Opportunity o where o.id = a.opportunityId
              and a.status = edu.diu.cyberconnect.common.model.ApplicationStatus.ACCEPTED
              and (a.applicantId = :userId or o.authorId = :userId) order by a.decidedAt desc""")
    List<Application> acceptedInvolving(@Param("userId") UUID userId);
}
