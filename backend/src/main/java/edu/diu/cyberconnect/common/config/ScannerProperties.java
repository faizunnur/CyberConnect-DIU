package edu.diu.cyberconnect.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Malware scanner selection. {@code noop} is only acceptable outside production. */
@ConfigurationProperties(prefix = "scanner")
public record ScannerProperties(String type, ClamAv clamav) {

    public record ClamAv(String host, int port, int timeoutMs) {
    }

    public boolean isNoOp() {
        return type == null || type.isBlank() || "noop".equalsIgnoreCase(type);
    }
}
