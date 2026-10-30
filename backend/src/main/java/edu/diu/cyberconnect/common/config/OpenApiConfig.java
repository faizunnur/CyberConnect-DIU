package edu.diu.cyberconnect.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI cyberConnectOpenApi(AppProperties app) {
        return new OpenAPI().info(new Info()
                .title(app.name() + " API")
                .version("v1")
                .description("Private, verified professional network for the DIU cybersecurity community. "
                        + "All non-public endpoints require an authenticated session cookie and a CSRF token."));
    }
}
