package edu.diu.cyberconnect.networking.api;

import edu.diu.cyberconnect.common.model.MentorshipStatus;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.networking.application.MentorshipRequestView;
import edu.diu.cyberconnect.networking.application.MentorshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mentorship/requests")
public class MentorshipController {

    private final MentorshipService mentorship;

    public MentorshipController(MentorshipService mentorship) {
        this.mentorship = mentorship;
    }

    @GetMapping
    public Page<MentorshipRequestView> list(@RequestParam(defaultValue = "mentee") String role,
            @RequestParam(required = false) MentorshipStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return mentorship.list(me(), "mentor".equalsIgnoreCase(role), status,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of("pendingAsMentor", mentorship.pendingForMentor(me()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MentorshipRequestView request(@Valid @RequestBody MentorshipRequestBody body) {
        return mentorship.request(me(), body.mentorId(), body.topic(), body.message());
    }

    @GetMapping("/{id}")
    public MentorshipRequestView get(@PathVariable UUID id) {
        return mentorship.get(me(), id);
    }

    @PostMapping("/{id}/accept")
    public MentorshipRequestView accept(@PathVariable UUID id, @RequestBody(required = false) NoteBody body) {
        return mentorship.respond(me(), id, true, body == null ? null : body.note());
    }

    @PostMapping("/{id}/decline")
    public MentorshipRequestView decline(@PathVariable UUID id, @RequestBody(required = false) NoteBody body) {
        return mentorship.respond(me(), id, false, body == null ? null : body.note());
    }

    @PostMapping("/{id}/complete")
    public MentorshipRequestView complete(@PathVariable UUID id) {
        return mentorship.complete(me(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        mentorship.cancel(me(), id);
    }

    private static UUID me() {
        return CurrentUser.require().getId();
    }

    public record MentorshipRequestBody(@NotNull UUID mentorId, @NotBlank @Size(max = 200) String topic,
            @NotBlank @Size(max = 2500) String message) {
    }

    public record NoteBody(@Size(max = 1000) String note) {
    }
}
