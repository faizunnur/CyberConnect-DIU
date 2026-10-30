package edu.diu.cyberconnect.networking.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.networking.application.NetworkingService;
import edu.diu.cyberconnect.profile.application.MemberCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blocks")
public class BlockController {

    private final NetworkingService networking;

    public BlockController(NetworkingService networking) {
        this.networking = networking;
    }

    @GetMapping
    public List<MemberCard> list() {
        return networking.blocked(CurrentUser.require().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@Valid @RequestBody BlockRequest request) {
        networking.block(CurrentUser.require().getId(), request.userId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable UUID userId) {
        networking.unblock(CurrentUser.require().getId(), userId);
    }

    public record BlockRequest(@NotNull UUID userId) {
    }
}
