package edu.diu.cyberconnect.opportunity.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.opportunity.application.OpportunityService;
import edu.diu.cyberconnect.opportunity.application.OpportunityView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/opportunities")
@PreAuthorize("hasAuthority('MODERATION_ACTION')")
public class ModerationOpportunityController {

    private final OpportunityService opportunities;

    public ModerationOpportunityController(OpportunityService opportunities) {
        this.opportunities = opportunities;
    }

    @GetMapping("/{id}")
    public OpportunityView get(@PathVariable UUID id) {
        return opportunities.moderatorGet(CurrentUser.require().getId(), id);
    }

    @PostMapping("/{id}/remove")
    public OpportunityView remove(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return opportunities.remove(CurrentUser.require().getId(), id, request.reason());
    }

    @PostMapping("/{id}/restore")
    public OpportunityView restore(@PathVariable UUID id) {
        return opportunities.restore(CurrentUser.require().getId(), id);
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
