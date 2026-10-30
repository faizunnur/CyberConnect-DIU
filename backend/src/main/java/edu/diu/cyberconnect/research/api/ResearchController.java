package edu.diu.cyberconnect.research.api;

import edu.diu.cyberconnect.common.model.ResearchStatus;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.research.application.ResearchCommand;
import edu.diu.cyberconnect.research.application.ResearchQuery;
import edu.diu.cyberconnect.research.application.ResearchService;
import edu.diu.cyberconnect.research.application.ResearchViews;
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
@RequestMapping("/api/v1/research")
public class ResearchController {

    private final ResearchService research;

    public ResearchController(ResearchService research) {
        this.research = research;
    }

    @GetMapping
    public Page<ResearchViews.Card> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) ResearchStatus status,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) Boolean lookingForMembers,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID me = me();
        return research.search(me, new ResearchQuery(q, status, skill, lookingForMembers, Boolean.TRUE.equals(mine) ? me : null),
                pageable(page, size));
    }

    @GetMapping("/applications")
    public Page<ResearchViews.ApplicationView> myApplications(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return research.myApplications(me(), pageable(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResearchViews.Project create(@Valid @RequestBody ProjectRequest request) {
        return research.create(me(), request.command());
    }

    @GetMapping("/{id}")
    public ResearchViews.Project get(@PathVariable UUID id) {
        return research.get(me(), id);
    }

    @PutMapping("/{id}")
    public ResearchViews.Project update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return research.update(me(), id, request.command());
    }

    @PostMapping("/{id}/status")
    public ResearchViews.Project setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return research.setStatus(me(), id, request.status());
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ResearchViews.ApplicationView apply(@PathVariable UUID id, @Valid @RequestBody(required = false) ApplyRequest request) {
        return research.apply(me(), id, request == null ? null : request.message());
    }

    @DeleteMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id) {
        research.withdraw(me(), id);
    }

    @PostMapping("/{id}/applications/{applicationId}/accept")
    public ResearchViews.Project accept(@PathVariable UUID id, @PathVariable UUID applicationId) {
        return research.decide(me(), id, applicationId, true);
    }

    @PostMapping("/{id}/applications/{applicationId}/decline")
    public ResearchViews.Project decline(@PathVariable UUID id, @PathVariable UUID applicationId) {
        return research.decide(me(), id, applicationId, false);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResearchViews.Project removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        return research.removeMember(me(), id, userId);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID id) {
        research.leave(me(), id);
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 12_000) String summary,
            Boolean lookingForMembers,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 15) List<@Size(max = 60) String> skills) {

        ResearchCommand command() {
            return new ResearchCommand(title, summary, lookingForMembers == null || lookingForMembers, startDate, endDate, skills);
        }
    }

    public record StatusRequest(@NotNull ResearchStatus status) {
    }

    public record ApplyRequest(@Size(max = 2500) String message) {
    }
}
