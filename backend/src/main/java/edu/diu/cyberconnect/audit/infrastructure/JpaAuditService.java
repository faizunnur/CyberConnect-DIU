package edu.diu.cyberconnect.audit.infrastructure;

import edu.diu.cyberconnect.audit.application.AuditEntry;
import edu.diu.cyberconnect.audit.application.AuditService;
import edu.diu.cyberconnect.audit.domain.AuditLog;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class JpaAuditService implements AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEntry entry) {
        repository.save(toEntity(entry));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDetached(AuditEntry entry) {
        repository.save(toEntity(entry));
    }

    private AuditLog toEntity(AuditEntry entry) {
        Map<String, Object> metadata = entry.metadata() == null ? Map.of() : entry.metadata();
        String roles = entry.actorRoles() == null || entry.actorRoles().isEmpty()
                ? null
                : String.join(",", entry.actorRoles().stream().sorted().toList());
        return new AuditLog(entry.actorId(), roles, entry.action(), entry.targetType(), entry.targetId(),
                entry.outcome().name(), objectMapper.writeValueAsString(metadata), entry.ip(), entry.userAgent(),
                entry.requestId());
    }
}
