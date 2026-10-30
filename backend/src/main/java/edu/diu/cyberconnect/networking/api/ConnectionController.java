package edu.diu.cyberconnect.networking.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.networking.application.ConnectionRequestView;
import edu.diu.cyberconnect.networking.application.ConnectionView;
import edu.diu.cyberconnect.networking.application.NetworkSummary;
import edu.diu.cyberconnect.networking.application.NetworkingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/connections")
public class ConnectionController {

    private final NetworkingService networking;

    public ConnectionController(NetworkingService networking) {
        this.networking = networking;
    }

    @GetMapping
    public Page<ConnectionView> connections(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return networking.connections(CurrentUser.require().getId(), pageable(page, size));
    }

    @GetMapping("/summary")
    public NetworkSummary summary() {
        return networking.summary(CurrentUser.require().getId());
    }

    @GetMapping("/requests")
    public Page<ConnectionRequestView> requests(@RequestParam(defaultValue = "incoming") String direction,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID me = CurrentUser.require().getId();
        return "outgoing".equalsIgnoreCase(direction)
                ? networking.outgoing(me, pageable(page, size))
                : networking.incoming(me, pageable(page, size));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionRequestView request(@Valid @RequestBody ConnectionRequest request) {
        return networking.request(CurrentUser.require().getId(), request.userId(), request.message());
    }

    @PostMapping("/requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID requestId) {
        networking.accept(CurrentUser.require().getId(), requestId);
    }

    @PostMapping("/requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID requestId) {
        networking.decline(CurrentUser.require().getId(), requestId);
    }

    @DeleteMapping("/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID requestId) {
        networking.cancel(CurrentUser.require().getId(), requestId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID userId) {
        networking.remove(CurrentUser.require().getId(), userId);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    public record ConnectionRequest(@NotNull UUID userId, @Size(max = 500) String message) {
    }
}
