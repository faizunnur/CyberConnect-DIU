package edu.diu.cyberconnect.opportunity.infrastructure;

import edu.diu.cyberconnect.common.model.OpportunityStatus;
import edu.diu.cyberconnect.opportunity.domain.Opportunity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    Page<Opportunity> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    List<Opportunity> findByStatusAndDeadlineBefore(OpportunityStatus status, LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update opportunities set application_count = greatest(application_count + :delta, 0) where id = :id",
            nativeQuery = true)
    void adjustApplications(@Param("id") UUID id, @Param("delta") int delta);
}
