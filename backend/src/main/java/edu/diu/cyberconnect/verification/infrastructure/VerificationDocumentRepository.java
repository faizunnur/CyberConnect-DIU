package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.VerificationDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    long countBySubmissionIdAndPurgedAtIsNull(UUID submissionId);
}
