package edu.diu.cyberconnect.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Explicit Spring Session cookie settings (ADR-0002). Spring Boot 4 does not map {@code server.servlet.session.cookie.*}
 * onto Spring Session's serializer, so the attributes are declared here to make them deterministic.
 */
@Configuration
public class SessionCookieConfig {

    public static final String COOKIE_NAME = "CCSESSION";

    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        serializer.setUseBase64Encoding(true);
        return serializer;
    }
}
