package edu.diu.cyberconnect.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The dev-only fixture endpoint must never exist in production: refuse to start rather than expose it. */
@Component
public class E2eFixturesGuard implements InitializingBean {

    private final AppProperties app;
    private final boolean enabled;

    public E2eFixturesGuard(AppProperties app, @Value("${app.e2e-fixtures.enabled:false}") boolean enabled) {
        this.app = app;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (app.isProduction() && enabled) {
            throw new IllegalStateException(
                    "Refusing to start: app.e2e-fixtures.enabled=true is not allowed in production.");
        }
    }
}
