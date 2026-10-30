package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.common.model.ReportStatus;
import edu.diu.cyberconnect.common.model.ReportTargetType;
import edu.diu.cyberconnect.community.domain.Report;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, ReportTargetType targetType,
            UUID targetId, ReportStatus status);

    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ReportStatus status);

    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, UUID targetId, ReportStatus status);

    List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, UUID targetId, ReportStatus status);
}
