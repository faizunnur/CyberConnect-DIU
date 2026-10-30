package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.SubmissionStatus;
import edu.diu.cyberconnect.verification.domain.VerificationSubmission;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationSubmissionRepository extends JpaRepository<VerificationSubmission, UUID> {

    List<VerificationSubmission> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<VerificationSubmission> findByAffiliationIdOrderByCreatedAtDesc(UUID affiliationId);

    Optional<VerificationSubmission> findFirstByAffiliationIdAndStatusIn(UUID affiliationId,
            Collection<SubmissionStatus> statuses);

    long countByAffiliationId(UUID affiliationId);

    @Query("""
            select s from VerificationSubmission s
            where s.status in :statuses
              and (:assignee is null or s.assignedReviewerId = :assignee)
            order by s.submittedAt asc nulls last, s.createdAt asc
            """)
    Page<VerificationSubmission> queue(@Param("statuses") Collection<SubmissionStatus> statuses,
            @Param("assignee") UUID assignee, Pageable pageable);

    @Query("""
            select s from VerificationSubmission s
            where s.status in :statuses and s.decidedAt < :cutoff
              and exists (select d from VerificationDocument d where d.submissionId = s.id and d.purgedAt is null)
            """)
    List<VerificationSubmission> finalDecidedBeforeWithUnpurgedDocuments(
            @Param("statuses") Collection<SubmissionStatus> statuses, @Param("cutoff") Instant cutoff);
}
