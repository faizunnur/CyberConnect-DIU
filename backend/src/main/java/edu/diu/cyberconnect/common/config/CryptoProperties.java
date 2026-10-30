package edu.diu.cyberconnect.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Keys for application-level encryption of identity documents (AES-256-GCM). */
@ConfigurationProperties(prefix = "crypto")
@Validated
public record CryptoProperties(
        /** Base64-encoded 32-byte key. Never logged. */
        @NotBlank String documentKey,
        /** Identifier stored next to each ciphertext so keys can be rotated later. */
        @NotBlank String keyId) {
}
