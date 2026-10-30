package edu.diu.cyberconnect.common.web;

import edu.diu.cyberconnect.common.config.AppProperties;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight public liveness endpoint for the reverse proxy and the frontend. Actuator stays internal. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final AppProperties app;
    private final String version;

    public HealthController(AppProperties app, ObjectProvider<BuildProperties> buildProperties) {
        this.app = app;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build == null ? "dev" : build.getVersion();
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", app.name(), app.environment(), version, Instant.now());
    }

    public record HealthResponse(String status, String application, String environment, String version,
            Instant timestamp) {
    }
}
