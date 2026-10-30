package edu.diu.cyberconnect.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * STOMP endpoint: session cookie + origin + CSRF double submit on CONNECT; verified members receive their own
 * notifications and messages on user queues; anonymous, foreign-origin and non-verified connections are refused.
 */
class WebSocketIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void verifiedMemberReceivesRealtimeNotificationsAndMessages() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "ws-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "ws-bob");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        api.assertStatus(api.put("/api/v1/me/profile/privacy", Map.of("profileVisibility", "ALL_MEMBERS",
                "emailVisibility", "NOBODY", "organizationVisibility", "ALL_MEMBERS", "locationVisibility", "ALL_MEMBERS",
                "mentorVisibility", "ALL_MEMBERS", "searchable", true, "messagingPolicy", "EVERYONE"), a), 200);

        StompSession session = connect(a, "http://localhost:3000", a.csrfToken());
        CompletableFuture<String> notification = subscribe(session, "/user/queue/notifications");
        CompletableFuture<String> message = subscribe(session, "/user/queue/messages");

        api.assertStatus(api.post("/api/v1/connections/requests", Map.of("userId", alice.id().toString()), b), 201);
        JsonNode pushed = objectMapper.readTree(notification.get(15, TimeUnit.SECONDS));
        assertThat(pushed.get("type").asString()).isEqualTo("CONNECTION_REQUEST");
        assertThat(pushed.get("link").asString()).isEqualTo("/connections");

        api.assertStatus(api.post("/api/v1/conversations", Map.of("userId", alice.id().toString(), "body", "ping over ws"), b), 201);
        JsonNode live = objectMapper.readTree(message.get(15, TimeUnit.SECONDS));
        assertThat(live.get("body").asString()).isEqualTo("ping over ws");
        assertThat(live.get("mine").asBoolean()).isFalse();
        session.disconnect();
    }

    @Test
    void anonymousForeignOriginUnverifiedAndCsrfLessConnectionsAreRefused() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "ws2-alice");
        TestActors.Actor pending = actors.registerApplicant(api, "ws2-pending", AffiliationType.STUDENT);
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session p = actors.login(api, pending);

        assertThatThrownBy(() -> connect(null, "http://localhost:3000", null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(a, "https://evil.example", a.csrfToken())).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(p, "http://localhost:3000", p.csrfToken())).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(a, "http://localhost:3000", "not-the-token")).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(a, "http://localhost:3000", null)).isInstanceOf(ExecutionException.class);
        // The same session connects fine when everything is in place.
        connect(a, "http://localhost:3000", a.csrfToken()).disconnect();
    }

    private StompSession connect(ApiClient.Session http, String origin, String csrf) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new RawStringConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        if (http != null) {
            // A browser sends both cookies; the CSRF cookie is what the CONNECT header is compared against.
            headers.add("Cookie", ApiClient.SESSION_COOKIE + "=" + http.sessionCookie().getValue()
                    + "; " + ApiClient.CSRF_COOKIE + "=" + http.csrfToken());
        }
        StompHeaders connectHeaders = new StompHeaders();
        if (csrf != null) {
            connectHeaders.add("X-XSRF-TOKEN", csrf);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", headers, connectHeaders, new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    /** Returns every payload as a UTF-8 string regardless of its content type (the server sends JSON). */
    private static final class RawStringConverter implements org.springframework.messaging.converter.MessageConverter {
        @Override
        public Object fromMessage(org.springframework.messaging.Message<?> message, Class<?> targetClass) {
            Object payload = message.getPayload();
            return payload instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : payload.toString();
        }

        @Override
        public org.springframework.messaging.Message<?> toMessage(Object payload, org.springframework.messaging.MessageHeaders headers) {
            return org.springframework.messaging.support.MessageBuilder.withPayload(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .copyHeaders(headers).build();
        }
    }

    private static CompletableFuture<String> subscribe(StompSession session, String destination) {
        CompletableFuture<String> future = new CompletableFuture<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                future.complete((String) payload);
            }
        });
        return future;
    }
}
