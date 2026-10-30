package edu.diu.cyberconnect.impact.api;

import edu.diu.cyberconnect.common.model.ImpactStatus;
import edu.diu.cyberconnect.common.model.ImpactType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.impact.application.ImpactService;
import edu.diu.cyberconnect.impact.application.ImpactViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImpactController {

    private final ImpactService impact;

    public ImpactController(ImpactService impact) {
        this.impact = impact;
    }

    @GetMapping("/impact")
    public Page<ImpactViews.Record> mine(@RequestParam(required = false) ImpactStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return impact.mine(me(), status, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/impact/sources")
    public List<ImpactViews.SourceOption> sources() {
        return impact.sources(me());
    }

    @GetMapping("/impact/stats")
    public ImpactViews.CommunityStats stats() {
        return impact.communityStats();
    }

    @PostMapping("/impact")
    @ResponseStatus(HttpStatus.CREATED)
    public ImpactViews.Record propose(@Valid @RequestBody ProposeRequest request) {
        return impact.propose(me(), request.type(), request.sourceId(), request.counterpartId(), request.title(),
                request.description());
    }

    @GetMapping("/impact/{id}")
    public ImpactViews.Record get(@PathVariable UUID id) {
        return impact.get(me(), id);
    }

    @PostMapping("/impact/{id}/confirm")
    public ImpactViews.Record confirm(@PathVariable UUID id) {
        return impact.confirm(me(), id);
    }

    @PostMapping("/impact/{id}/dispute")
    public ImpactViews.Record dispute(@PathVariable UUID id, @Valid @RequestBody DisputeRequest request) {
        return impact.dispute(me(), id, request.reason());
    }

    @PostMapping("/impact/{id}/withdraw")
    public ImpactViews.Record withdraw(@PathVariable UUID id) {
        return impact.withdraw(me(), id);
    }

    @GetMapping("/members/{userId}/impact")
    public ImpactViews.MemberImpact memberImpact(@PathVariable UUID userId) {
        return impact.memberImpact(me(), userId);
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record ProposeRequest(@NotNull ImpactType type, @NotNull UUID sourceId, UUID counterpartId,
            @NotBlank @Size(max = 250) String title, @Size(max = 2500) String description) {
    }

    public record DisputeRequest(@NotBlank @Size(max = 2000) String reason) {
    }
}
