package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.Appeal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppealRepository extends JpaRepository<Appeal, UUID> {

    Optional<Appeal> findBySubmissionId(UUID submissionId);

    List<Appeal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Appeal> findByStatusInOrderByCreatedAtAsc(Collection<Appeal.Status> statuses, Pageable pageable);
}
