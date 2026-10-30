package edu.diu.cyberconnect.common.realtime;

import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.security.AccountSnapshotProvider;
import edu.diu.cyberconnect.common.security.UserPrincipal;
import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.Cookie;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * STOMP over WebSocket at {@code /ws} (ADR-0003: proxied by nginx on the same origin). The handshake reuses the
 * session cookie; the origin is restricted to the application base URL; only verified accounts may connect; the
 * CONNECT frame must carry the CSRF token (double submit, checked by {@link WebSocketCsrfInterceptor}). Clients only
 * subscribe to their own user queues; nothing is sent from the browser over STOMP.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String CSRF_ATTRIBUTE = WebSocketConfig.class.getName() + ".CSRF";
    static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final AppProperties app;
    private final AccountSnapshotProvider snapshots;

    public WebSocketConfig(AppProperties app, AccountSnapshotProvider snapshots) {
        this.app = app;
        this.snapshots = snapshots;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = app.isProduction()
                ? new String[] {app.baseUrl()}
                : new String[] {app.baseUrl(), "http://localhost:3000", "http://localhost:8088"};
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(new VerifiedMemberHandshakeInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** Rejects handshakes from anonymous or non-verified accounts and stashes the CSRF token for the CONNECT check. */
    private final class VerifiedMemberHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                Map<String, Object> attributes) {
            Principal principal = request.getPrincipal();
            if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof UserPrincipal user)) {
                return false;
            }
            boolean verified = snapshots.snapshot(user.getId())
                    .map(s -> s.status() == AccountStatus.VERIFIED && s.emailVerified())
                    .orElse(false);
            if (!verified) {
                return false;
            }
            if (request instanceof ServletServerHttpRequest servletRequest) {
                // Double submit: the CONNECT header must equal the XSRF-TOKEN cookie, which a cross-site page cannot read.
                // (The CsrfToken request attribute is XOR-masked per request and therefore unusable for comparison.)
                Cookie[] cookies = servletRequest.getServletRequest().getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (CSRF_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                            attributes.put(CSRF_ATTRIBUTE, cookie.getValue());
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                Exception exception) {
        }
    }
}
