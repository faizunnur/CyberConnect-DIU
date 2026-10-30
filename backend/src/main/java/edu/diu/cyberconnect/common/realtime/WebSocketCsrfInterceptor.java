package edu.diu.cyberconnect.common.realtime;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * Double-submit CSRF check for STOMP CONNECT: the {@code X-XSRF-TOKEN} native header must equal the token the
 * handshake saw (which equals the {@code XSRF-TOKEN} cookie a cross-site page cannot read). Replaces Spring's
 * XOR-based interceptor, which expects session-stored tokens that the SPA cookie repository does not use.
 */
public final class WebSocketCsrfInterceptor implements ChannelInterceptor {

    static final String HEADER = "X-XSRF-TOKEN";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.CONNECT) {
            return message;
        }
        Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
        Object expected = attributes == null ? null : attributes.get(WebSocketConfig.CSRF_ATTRIBUTE);
        if (!(expected instanceof String expectedToken) || expectedToken.isEmpty()) {
            throw new MissingCsrfTokenException("");
        }
        String actual = accessor.getFirstNativeHeader(HEADER);
        if (actual == null || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidCsrfTokenException(null, actual == null ? "" : "present");
        }
        return message;
    }
}
