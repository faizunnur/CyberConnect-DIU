package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.VerificationReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationReviewRepository extends JpaRepository<VerificationReview, UUID> {

    List<VerificationReview> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    Optional<VerificationReview> findTopBySubmissionIdOrderByCreatedAtDesc(UUID submissionId);
}
