package edu.diu.cyberconnect.opportunity.api;

import edu.diu.cyberconnect.common.model.ApplicationMethod;
import edu.diu.cyberconnect.common.model.ApplicationStatus;
import edu.diu.cyberconnect.common.model.LocationType;
import edu.diu.cyberconnect.common.model.OpportunityType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.opportunity.application.ApplicationView;
import edu.diu.cyberconnect.opportunity.application.OpportunityCard;
import edu.diu.cyberconnect.opportunity.application.OpportunityCommand;
import edu.diu.cyberconnect.opportunity.application.OpportunityQuery;
import edu.diu.cyberconnect.opportunity.application.OpportunityService;
import edu.diu.cyberconnect.opportunity.application.OpportunityView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {

    private final OpportunityService opportunities;

    public OpportunityController(OpportunityService opportunities) {
        this.opportunities = opportunities;
    }

    @GetMapping
    public Page<OpportunityCard> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) OpportunityType type,
            @RequestParam(required = false) LocationType locationType,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) UUID author,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return opportunities.search(me(), new OpportunityQuery(q, type, locationType, skill, author), pageable(page, size));
    }

    @GetMapping("/mine")
    public Page<OpportunityCard> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return opportunities.mine(me(), pageable(page, size));
    }

    @GetMapping("/applications")
    public Page<ApplicationView> myApplications(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return opportunities.myApplications(me(), pageable(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunityView create(@Valid @RequestBody OpportunityRequest request) {
        return opportunities.create(me(), request.command());
    }

    @GetMapping("/{id}")
    public OpportunityView get(@PathVariable UUID id) {
        return opportunities.get(me(), id);
    }

    @PutMapping("/{id}")
    public OpportunityView update(@PathVariable UUID id, @Valid @RequestBody OpportunityRequest request) {
        return opportunities.update(me(), id, request.command());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID id) {
        opportunities.deleteDraft(me(), id);
    }

    @PostMapping("/{id}/publish")
    public OpportunityView publish(@PathVariable UUID id) {
        return opportunities.publish(me(), id);
    }

    @PostMapping("/{id}/close")
    public OpportunityView close(@PathVariable UUID id) {
        return opportunities.close(me(), id);
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView apply(@PathVariable UUID id, @Valid @RequestBody(required = false) ApplyRequest request) {
        return opportunities.apply(me(), id, request == null ? null : request.coverNote());
    }

    @DeleteMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id) {
        opportunities.withdraw(me(), id);
    }

    @GetMapping("/{id}/applicants")
    public Page<ApplicationView> applicants(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return opportunities.applicants(me(), id, pageable(page, size));
    }

    @PostMapping("/{id}/applicants/{applicationId}/decide")
    public ApplicationView decide(@PathVariable UUID id, @PathVariable UUID applicationId,
            @Valid @RequestBody DecisionRequest request) {
        return opportunities.decide(me(), id, applicationId, request.status());
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    public record OpportunityRequest(
            @NotNull OpportunityType type,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 160) String organization,
            LocationType locationType,
            @Size(max = 160) String location,
            @NotBlank @Size(max = 12_000) String description,
            @Size(max = 160) String compensation,
            LocalDate deadline,
            ApplicationMethod applicationMethod,
            @Size(max = 500) String applicationUrl,
            @Size(max = 254) String applicationEmail,
            @Size(max = 15) List<@Size(max = 60) String> skills,
            Boolean publish) {

        OpportunityCommand command() {
            return new OpportunityCommand(type, title, organization, locationType, location, description, compensation,
                    deadline, applicationMethod, applicationUrl, applicationEmail, skills, Boolean.TRUE.equals(publish));
        }
    }

    public record ApplyRequest(@Size(max = 3500) String coverNote) {
    }

    public record DecisionRequest(@NotNull ApplicationStatus status) {
    }
}
