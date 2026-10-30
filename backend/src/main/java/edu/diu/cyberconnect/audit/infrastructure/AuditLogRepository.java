package edu.diu.cyberconnect.audit.infrastructure;

import edu.diu.cyberconnect.audit.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
              and (:actorId is null or a.actorId = :actorId)
              and (:action is null or a.action = :action)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("targetType") String targetType, @Param("targetId") UUID targetId,
            @Param("actorId") UUID actorId, @Param("action") String action, Pageable pageable);
}
