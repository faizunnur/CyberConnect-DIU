package edu.diu.cyberconnect.moderation.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.moderation.application.MemberModerationService;
import edu.diu.cyberconnect.moderation.application.ModerationActionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/members")
@PreAuthorize("hasAuthority('MODERATION_ACTION')")
public class ModerationMemberController {

    private final MemberModerationService moderation;
    private final StepUpService stepUp;

    public ModerationMemberController(MemberModerationService moderation, StepUpService stepUp) {
        this.moderation = moderation;
        this.stepUp = stepUp;
    }

    @PostMapping("/{userId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable UUID userId, @Valid @RequestBody SuspendRequest request, HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        moderation.suspend(userId, request.reason(), request.expiresAt(), CurrentUser.require().getId());
    }

    @PostMapping("/{userId}/unsuspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsuspend(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        moderation.unsuspend(userId, request.reason(), CurrentUser.require().getId());
    }

    @GetMapping("/{userId}/actions")
    public List<ModerationActionView> history(@PathVariable UUID userId) {
        return moderation.history(userId);
    }

    public record SuspendRequest(@NotBlank @Size(max = 1000) String reason, @Future Instant expiresAt) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
