package edu.diu.cyberconnect.admin.api;

import edu.diu.cyberconnect.audit.application.AuditQuery;
import edu.diu.cyberconnect.audit.application.AuditRecord;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAuthority('AUDIT_READ')")
public class AdminAuditController {

    private final AuditQuery auditQuery;

    public AdminAuditController(AuditQuery auditQuery) {
        this.auditQuery = auditQuery;
    }

    @GetMapping
    public Page<AuditRecord> search(@RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditQuery.find(targetType, targetId, actorId, action,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    }
}
