package edu.diu.cyberconnect.common.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/** Deny-by-default STOMP authorization: authenticated members may only subscribe to their own user queues. */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.DISCONNECT, SimpMessageType.HEARTBEAT,
                        SimpMessageType.UNSUBSCRIBE).authenticated()
                .simpSubscribeDestMatchers("/user/queue/**").authenticated()
                .anyMessage().denyAll()
                .build();
    }

    /** Overrides Spring Security's default CSRF channel interceptor with the double-submit variant. */
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        return new WebSocketCsrfInterceptor();
    }
}
