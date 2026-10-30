package edu.diu.cyberconnect.verification.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.common.security.StepUpService;
import edu.diu.cyberconnect.verification.application.AppealService;
import edu.diu.cyberconnect.verification.application.VerificationViews.AppealDetail;
import edu.diu.cyberconnect.verification.domain.Appeal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/appeals")
@PreAuthorize("hasAuthority('APPEAL_DECIDE')")
public class ModerationAppealController {

    private final AppealService appeals;
    private final StepUpService stepUp;

    public ModerationAppealController(AppealService appeals, StepUpService stepUp) {
        this.appeals = appeals;
        this.stepUp = stepUp;
    }

    @GetMapping
    public Page<AppealDetail> queue(@RequestParam(required = false) Set<Appeal.Status> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return appeals.queue(status, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{appealId}")
    public AppealDetail detail(@PathVariable UUID appealId) {
        return appeals.detail(appealId);
    }

    @PostMapping("/{appealId}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID appealId) {
        appeals.assign(appealId, CurrentUser.require().getId());
    }

    @PostMapping("/{appealId}/decide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable UUID appealId, @Valid @RequestBody DecideRequest request, HttpServletRequest http) {
        stepUp.requireFresh(http.getSession(false));
        appeals.decide(appealId, CurrentUser.require().getId(), request.approved(), request.textForApplicant(),
                request.internalNotes());
    }

    public record DecideRequest(@NotNull Boolean approved, @Size(max = 2000) String textForApplicant,
            @Size(max = 4000) String internalNotes) {
    }
}
