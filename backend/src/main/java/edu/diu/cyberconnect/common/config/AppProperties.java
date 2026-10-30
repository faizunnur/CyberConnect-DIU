package edu.diu.cyberconnect.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application-wide settings. */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank String name,
        @NotBlank String baseUrl,
        @NotBlank String environment,
        @NotBlank String mailFrom) {

    public boolean isProduction() {
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }
}
