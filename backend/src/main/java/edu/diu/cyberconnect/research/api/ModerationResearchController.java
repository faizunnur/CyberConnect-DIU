package edu.diu.cyberconnect.research.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.research.application.ResearchService;
import edu.diu.cyberconnect.research.application.ResearchViews;
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
@RequestMapping("/api/v1/moderation/research")
@PreAuthorize("hasAuthority('MODERATION_ACTION')")
public class ModerationResearchController {

    private final ResearchService research;

    public ModerationResearchController(ResearchService research) {
        this.research = research;
    }

    @GetMapping("/{id}")
    public ResearchViews.Project get(@PathVariable UUID id) {
        return research.moderatorGet(CurrentUser.require().getId(), id);
    }

    @PostMapping("/{id}/remove")
    public ResearchViews.Project remove(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return research.remove(CurrentUser.require().getId(), id, request.reason());
    }

    @PostMapping("/{id}/restore")
    public ResearchViews.Project restore(@PathVariable UUID id) {
        return research.restore(CurrentUser.require().getId(), id);
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
