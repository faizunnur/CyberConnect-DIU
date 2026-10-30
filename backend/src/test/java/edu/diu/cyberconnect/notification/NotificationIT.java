package edu.diu.cyberconnect.notification;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.CapturingMailSender;
import edu.diu.cyberconnect.support.TestActors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** In-app notifications, preferences and email fan-out produced by other modules. */
class NotificationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;
    @Autowired
    private CapturingMailSender mail;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void connectionsCommentsAndMentorshipProduceNotificationsAccordingToPreferences() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "nt-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "nt-bob");
        TestActors.Actor carol = actors.verifiedMember(api, "nt-carol");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        ApiClient.Session c = actors.login(api, carol);

        // Defaults: everything in-app, decisions by email.
        JsonNode prefs = api.body(api.get("/api/v1/notifications/preferences", a));
        assertThat(prefs.findValuesAsString("type")).contains("CONNECTION_REQUEST", "VERIFICATION_DECISION", "NEW_MESSAGE");
        JsonNode connectionPref = find(prefs, "CONNECTION_REQUEST");
        assertThat(connectionPref.get("inApp").asBoolean()).isTrue();
        assertThat(connectionPref.get("email").asBoolean()).isFalse();
        assertThat(find(prefs, "APPLICATION_DECISION").get("email").asBoolean()).isTrue();
        assertThat(find(prefs, "VERIFICATION_DECISION").get("email").asBoolean()).as("decisions are emailed by the verification module itself").isFalse();

        String request = api.body(api.post("/api/v1/connections/requests", Map.of("userId", alice.id().toString(), "message", "Let's connect"), b)).get("id").asString();
        JsonNode inbox = api.body(api.get("/api/v1/notifications", a));
        assertThat(inbox.get("content").size()).isEqualTo(1);
        JsonNode first = inbox.get("content").get(0);
        assertThat(first.get("type").asString()).isEqualTo("CONNECTION_REQUEST");
        assertThat(first.get("actor").get("userId").asString()).isEqualTo(bob.id().toString());
        assertThat(first.get("link").asString()).isEqualTo("/connections");
        assertThat(first.get("readAt").isNull()).isTrue();
        assertThat(api.body(api.get("/api/v1/notifications/summary", a)).get("unread").asLong()).isEqualTo(1);
        assertThat(mail.lastTo(alice.email()).map(m -> m.subject()).orElse("")).as("no email for connection requests by default")
                .doesNotContain("connect");

        // IDOR: only the owner marks it read.
        api.assertStatus(api.post("/api/v1/notifications/" + first.get("id").asString() + "/read", null, b), 404);
        api.assertStatus(api.post("/api/v1/notifications/" + first.get("id").asString() + "/read", null, a), 204);
        assertThat(api.body(api.get("/api/v1/notifications/summary", a)).get("unread").asLong()).isZero();
        assertThat(api.body(api.get("/api/v1/notifications?unread=true", a)).get("content").size()).isZero();

        api.assertStatus(api.post("/api/v1/connections/requests/" + request + "/accept", null, a), 204);
        assertThat(api.body(api.get("/api/v1/notifications", b)).get("content").get(0).get("type").asString()).isEqualTo("CONNECTION_ACCEPTED");

        // Comments notify the post author and reply targets, never the actor themselves.
        String postId = api.body(api.post("/api/v1/posts", Map.of("intent", "EVENT", "body", "CTF night on Friday"), a)).get("id").asString();
        String commentId = api.body(api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "Count me in"), b)).get("id").asString();
        api.assertStatus(api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "See you there", "parentId", commentId), a), 201);
        api.assertStatus(api.post("/api/v1/posts/" + postId + "/comments", Map.of("body", "my own note"), a), 201);
        List<String> aliceTypes = api.body(api.get("/api/v1/notifications", a)).get("content").findValuesAsString("type");
        assertThat(aliceTypes).containsExactly("POST_COMMENT", "CONNECTION_REQUEST");
        List<String> bobTypes = api.body(api.get("/api/v1/notifications", b)).get("content").findValuesAsString("type");
        assertThat(bobTypes).containsExactly("COMMENT_REPLY", "CONNECTION_ACCEPTED");

        // Preferences: switching a type off stops in-app records; mandatory types stay on; email follows the setting.
        api.assertStatus(api.put("/api/v1/notifications/preferences", List.of(
                Map.of("type", "CONNECTION_REQUEST", "inApp", false, "email", false),
                Map.of("type", "VERIFICATION_DECISION", "inApp", false, "email", false),
                Map.of("type", "MENTORSHIP_REQUEST", "inApp", true, "email", true)), a), 200);
        JsonNode updated = api.body(api.get("/api/v1/notifications/preferences", a));
        assertThat(find(updated, "CONNECTION_REQUEST").get("inApp").asBoolean()).isFalse();
        assertThat(find(updated, "VERIFICATION_DECISION").get("inApp").asBoolean()).as("mandatory type cannot be silenced").isTrue();
        api.assertStatus(api.post("/api/v1/connections/requests", Map.of("userId", alice.id().toString()), c), 201);
        List<String> afterOptOut = api.body(api.get("/api/v1/notifications", a)).get("content").findValuesAsString("type");
        assertThat(afterOptOut.stream().filter("CONNECTION_REQUEST"::equals).count()).as("no new in-app record once switched off").isEqualTo(1);
        assertThat(afterOptOut).hasSize(2);

        api.assertStatus(api.put("/api/v1/me/profile", Map.of("displayName", "Alice Mentor", "mentorAvailable", true), a), 200);
        api.assertStatus(api.post("/api/v1/mentorship/requests", Map.of("mentorId", alice.id().toString(), "topic", "Blue team",
                "message", "Can you review my SOC home lab plan?"), c), 201);
        JsonNode mentorship = api.body(api.get("/api/v1/notifications", a)).get("content").get(0);
        assertThat(mentorship.get("type").asString()).isEqualTo("MENTORSHIP_REQUEST");
        assertThat(mail.lastTo(alice.email()).orElseThrow().subject()).contains("mentorship request");
        assertThat(mail.lastTo(alice.email()).orElseThrow().textBody()).contains("Settings");

        assertThat(api.body(api.post("/api/v1/notifications/read-all", null, a)).get("updated").asInt()).isEqualTo(2);
        assertThat(api.body(api.get("/api/v1/notifications/summary", a)).get("unread").asLong()).isZero();
    }

    private static JsonNode find(JsonNode prefs, String type) {
        for (JsonNode p : prefs) {
            if (p.get("type").asString().equals(type)) {
                return p;
            }
        }
        throw new AssertionError("missing preference " + type);
    }
}
