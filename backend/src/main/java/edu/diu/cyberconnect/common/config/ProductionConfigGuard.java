package edu.diu.cyberconnect.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production deployment with development settings: plain-HTTP base URL, insecure session cookie,
 * API docs enabled, a development mail host or the dev fixtures flag. Complements {@code ScannerProductionGuard} and
 * {@code E2eFixturesGuard}; all three fail fast so a misconfigured VPS never serves traffic.
 */
@Component
public class ProductionConfigGuard implements InitializingBean {

    private final AppProperties app;
    private final boolean cookieSecure;
    private final boolean apiDocsEnabled;
    private final String smtpHost;
    private final String sessionNamespace;

    public ProductionConfigGuard(AppProperties app,
            @Value("${server.servlet.session.cookie.secure:true}") boolean cookieSecure,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.session.data.redis.namespace:}") String sessionNamespace) {
        this.app = app;
        this.cookieSecure = cookieSecure;
        this.apiDocsEnabled = apiDocsEnabled;
        this.smtpHost = smtpHost;
        this.sessionNamespace = sessionNamespace;
    }

    @Override
    public void afterPropertiesSet() {
        if (!app.isProduction()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (!app.baseUrl().startsWith("https://")) {
            problems.add("APP_BASE_URL must use https (" + app.baseUrl() + ")");
        }
        if (!cookieSecure) {
            problems.add("SESSION_COOKIE_SECURE must be true");
        }
        if (apiDocsEnabled) {
            problems.add("API_DOCS_ENABLED must be false");
        }
        if (smtpHost.isBlank() || smtpHost.equalsIgnoreCase("mailpit") || smtpHost.equalsIgnoreCase("localhost")) {
            problems.add("SMTP_HOST must point at a real mail relay");
        }
        if (sessionNamespace.isBlank()) {
            problems.add("spring.session.data.redis.namespace must be set");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in production: " + String.join("; ", problems));
        }
    }
}
