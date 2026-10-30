package edu.diu.cyberconnect.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse limits for enumeration-prone endpoints. Production keeps the strict defaults; local/docker/test profiles raise
 * them so automated flows can create many accounts from one address.
 */
@ConfigurationProperties(prefix = "app.rate-limits")
public record RateLimitProperties(
        int registerPerHourPerIp,
        int loginPerTenMinutesPerIp,
        int loginPerFifteenMinutesPerEmail,
        int forgotPasswordPerFifteenMinutesPerIp) {

    public RateLimitProperties {
        registerPerHourPerIp = registerPerHourPerIp <= 0 ? 5 : registerPerHourPerIp;
        loginPerTenMinutesPerIp = loginPerTenMinutesPerIp <= 0 ? 20 : loginPerTenMinutesPerIp;
        loginPerFifteenMinutesPerEmail = loginPerFifteenMinutesPerEmail <= 0 ? 8 : loginPerFifteenMinutesPerEmail;
        forgotPasswordPerFifteenMinutesPerIp = forgotPasswordPerFifteenMinutesPerIp <= 0 ? 5 : forgotPasswordPerFifteenMinutesPerIp;
    }
}
