package edu.diu.cyberconnect.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** First-run bootstrap: the account with this email becomes the first SUPER_ADMIN if none exists yet. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String superAdminEmail) {

    public boolean isConfigured() {
        return superAdminEmail != null && !superAdminEmail.isBlank();
    }
}
