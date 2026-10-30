package edu.diu.cyberconnect.community.api;

import edu.diu.cyberconnect.common.model.ReportReason;
import edu.diu.cyberconnect.common.model.ReportTargetType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.community.application.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> report(@Valid @RequestBody ReportRequest request) {
        UUID id = reports.report(CurrentUser.require().getId(), request.targetType(), request.targetId(),
                request.reason(), request.details());
        return Map.of("id", id);
    }

    public record ReportRequest(@NotNull ReportTargetType targetType, @NotNull UUID targetId,
            @NotNull ReportReason reason, @Size(max = 2000) String details) {
    }
}
