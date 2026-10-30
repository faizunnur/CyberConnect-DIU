package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.DocumentAccessLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, UUID> {

    List<DocumentAccessLog> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
