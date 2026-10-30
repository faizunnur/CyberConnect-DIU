package edu.diu.cyberconnect.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** S3-compatible object storage settings. The provider (RustFS, S3, Garage, ...) is interchangeable. */
@ConfigurationProperties(prefix = "storage")
@Validated
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        boolean pathStyle,
        boolean ensureBuckets,
        @Valid Buckets buckets) {

    public record Buckets(@NotBlank String documents, @NotBlank String media) {
    }
}
