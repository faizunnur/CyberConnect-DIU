package edu.diu.cyberconnect.research.infrastructure;

import edu.diu.cyberconnect.common.model.ResearchApplicationStatus;
import edu.diu.cyberconnect.research.domain.ResearchApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchApplicationRepository extends JpaRepository<ResearchApplication, UUID> {

    Optional<ResearchApplication> findByProjectIdAndApplicantIdAndStatus(UUID projectId, UUID applicantId,
            ResearchApplicationStatus status);

    List<ResearchApplication> findByProjectIdAndStatusOrderByCreatedAtAsc(UUID projectId, ResearchApplicationStatus status);

    Page<ResearchApplication> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId, Pageable pageable);
}
