package edu.diu.cyberconnect.audit.infrastructure;

import edu.diu.cyberconnect.audit.application.AuditQuery;
import edu.diu.cyberconnect.audit.application.AuditRecord;
import edu.diu.cyberconnect.audit.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JpaAuditQuery implements AuditQuery {

    private final AuditLogRepository repository;

    public JpaAuditQuery(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<AuditRecord> find(String targetType, UUID targetId, UUID actorId, String action, Pageable pageable) {
        return repository.search(blankToNull(targetType), targetId, actorId, blankToNull(action), pageable)
                .map(JpaAuditQuery::toRecord);
    }

    private static AuditRecord toRecord(AuditLog log) {
        return new AuditRecord(log.getId(), log.getActorId(), log.getActorRoles(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getOutcome(), log.getMetadata(), log.getIp(),
                log.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
