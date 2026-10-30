package edu.diu.cyberconnect.identity.api;

import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.identity.application.DevFixtureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only test fixtures (never present in production; see {@code E2eFixturesGuard}). */
@RestController
@RequestMapping("/api/v1/dev/fixtures")
@Profile("!prod")
@ConditionalOnBean(DevFixtureService.class)
public class DevFixtureController {

    private final DevFixtureService fixtures;

    public DevFixtureController(DevFixtureService fixtures) {
        this.fixtures = fixtures;
    }

    @PostMapping("/verified-member")
    @ResponseStatus(HttpStatus.CREATED)
    public DevFixtureService.Fixture verifiedMember(@Valid @RequestBody(required = false) FixtureRequest request) {
        FixtureRequest r = request == null ? new FixtureRequest(null, null, null) : request;
        return fixtures.verifiedMember(r.prefix(), r.affiliationType(), r.fullName());
    }

    public record FixtureRequest(@Pattern(regexp = "[a-z0-9-]{0,24}") String prefix, AffiliationType affiliationType,
            @Size(max = 120) String fullName) {
    }
}
