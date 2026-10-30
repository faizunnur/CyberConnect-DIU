package edu.diu.cyberconnect.impact.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.impact.application.ImpactService;
import edu.diu.cyberconnect.impact.application.ImpactViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/impact")
@PreAuthorize("hasAuthority('MODERATION_ACTION')")
public class ModerationImpactController {

    private final ImpactService impact;

    public ModerationImpactController(ImpactService impact) {
        this.impact = impact;
    }

    @GetMapping("/disputes")
    public Page<ImpactViews.Record> disputes(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return impact.disputes(me(), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/{id}")
    public ImpactViews.Record get(@PathVariable UUID id) {
        return impact.moderatorGet(me(), id);
    }

    @PostMapping("/{id}/resolve")
    public ImpactViews.Record resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        return impact.resolve(me(), id, request.confirmed(), request.note());
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record ResolveRequest(@NotNull Boolean confirmed, @Size(max = 2000) String note) {
    }
}
