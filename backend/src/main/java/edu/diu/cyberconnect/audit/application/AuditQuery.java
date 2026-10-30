package edu.diu.cyberconnect.audit.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read access to the audit trail for administrators and reviewers. */
public interface AuditQuery {

    Page<AuditRecord> find(String targetType, UUID targetId, UUID actorId, String action, Pageable pageable);
}
