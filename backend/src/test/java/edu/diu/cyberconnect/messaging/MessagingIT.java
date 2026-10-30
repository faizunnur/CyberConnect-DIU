package edu.diu.cyberconnect.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.support.AbstractIntegrationTest;
import edu.diu.cyberconnect.support.ApiClient;
import edu.diu.cyberconnect.support.TestActors;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Direct messaging: policies and requests, membership checks, history, read state, blocking, deletion, reports. */
class MessagingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestActors actors;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, objectMapper);
    }

    private void setPolicy(ApiClient.Session session, String policy) throws Exception {
        api.assertStatus(api.put("/api/v1/me/profile/privacy", Map.of("profileVisibility", "ALL_MEMBERS",
                "emailVisibility", "NOBODY", "organizationVisibility", "ALL_MEMBERS", "locationVisibility", "ALL_MEMBERS",
                "mentorVisibility", "ALL_MEMBERS", "searchable", true, "messagingPolicy", policy), session), 200);
    }

    @Test
    void requestsPoliciesHistoryReadStateAndBlocking() throws Exception {
        TestActors.Actor alice = actors.verifiedMember(api, "ms-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "ms-bob");
        TestActors.Actor carol = actors.verifiedMember(api, "ms-carol");
        TestActors.Actor dave = actors.verifiedMember(api, "ms-dave");
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        ApiClient.Session c = actors.login(api, carol);
        ApiClient.Session d = actors.login(api, dave);
        setPolicy(a, "EVERYONE");
        setPolicy(c, "NOBODY"); // bob keeps the default CONNECTIONS_ONLY

        // Alice writes to Bob (not connected): Bob sees a request; Alice cannot send a second message yet.
        MvcResult started = api.post("/api/v1/conversations", Map.of("userId", bob.id().toString(), "body", "Hi Bob\u0000, fancy a CTF?"), a);
        api.assertStatus(started, 201);
        JsonNode conversation = api.body(started);
        String id = conversation.get("id").asString();
        assertThat(conversation.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(conversation.get("otherState").asString()).isEqualTo("REQUEST");
        assertThat(conversation.get("canSend").asBoolean()).isFalse();
        assertThat(conversation.get("lastMessagePreview").asString()).isEqualTo("Hi Bob, fancy a CTF?");
        MvcResult second = api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "again"), a);
        api.assertStatus(second, 409);
        assertThat(api.code(second)).isEqualTo("REQUEST_PENDING");
        assertThat(ids(api.body(api.get("/api/v1/conversations?box=requests", b)))).contains(id);
        assertThat(ids(api.body(api.get("/api/v1/conversations", b)))).doesNotContain(id);
        // Starting again reuses the same conversation.
        assertThat(api.body(api.post("/api/v1/conversations", Map.of("userId", alice.id().toString(), "body", "Sure!"), b)).get("id").asString()).isEqualTo(id);
        // Bob replying accepted the request implicitly; an explicit accept now has nothing to do.
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/accept", null, b), 409);
        assertThat(api.body(api.get("/api/v1/conversations/" + id, a)).get("otherState").asString()).isEqualTo("ACTIVE");
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "Great, Saturday?"), a), 201);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "Bring coffee."), a), 201);

        // Unread counts and read marking.
        JsonNode inboxForBob = api.body(api.get("/api/v1/conversations", b));
        assertThat(inboxForBob.get("content").get(0).get("unreadCount").asLong()).isEqualTo(2);
        assertThat(api.body(api.get("/api/v1/conversations/summary", b)).get("unread").asLong()).isEqualTo(1);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/read", null, b), 204);
        assertThat(api.body(api.get("/api/v1/conversations/" + id, b)).get("unreadCount").asLong()).isZero();
        assertThat(api.body(api.get("/api/v1/conversations/summary", b)).get("unread").asLong()).isZero();

        // Keyset history, newest first.
        JsonNode page1 = api.body(api.get("/api/v1/conversations/" + id + "/messages?size=2", b));
        assertThat(page1.size()).isEqualTo(2);
        assertThat(page1.get(0).get("body").asString()).isEqualTo("Bring coffee.");
        assertThat(page1.get(0).get("mine").asBoolean()).isFalse();
        JsonNode page2 = api.body(api.get("/api/v1/conversations/" + id + "/messages?size=2&before=" + page1.get(1).get("id").asString(), b));
        assertThat(page2.size()).isEqualTo(2);
        assertThat(page2.get(1).get("body").asString()).isEqualTo("Hi Bob, fancy a CTF?");
        assertThat(api.body(api.get("/api/v1/conversations/" + id + "/messages?size=2&before=" + page2.get(1).get("id").asString(), b)).size()).isZero();

        // Membership is enforced on every read and write.
        api.assertStatus(api.get("/api/v1/conversations/" + id, d), 404);
        api.assertStatus(api.get("/api/v1/conversations/" + id + "/messages", d), 404);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "intruder"), d), 404);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/read", null, d), 404);
        api.assertStatus(api.delete("/api/v1/messages/" + page1.get(0).get("id").asString(), b), 404);
        api.assertStatus(api.delete("/api/v1/messages/" + page1.get(0).get("id").asString(), a), 204);
        JsonNode afterDelete = api.body(api.get("/api/v1/conversations/" + id + "/messages?size=1", b));
        assertThat(afterDelete.get(0).get("deleted").asBoolean()).isTrue();
        assertThat(afterDelete.get(0).get("body").isNull()).isTrue();

        // Policies: NOBODY refuses; EVERYONE opens directly; a declined request blocks further messages.
        MvcResult toCarol = api.post("/api/v1/conversations", Map.of("userId", carol.id().toString(), "body", "hello"), a);
        api.assertStatus(toCarol, 403);
        assertThat(api.code(toCarol)).isEqualTo("MESSAGING_NOT_ALLOWED");
        JsonNode carolToAlice = api.body(api.post("/api/v1/conversations", Map.of("userId", alice.id().toString(), "body", "hi"), c));
        assertThat(carolToAlice.get("otherState").asString()).isEqualTo("ACTIVE");
        String daveToBob = api.body(api.post("/api/v1/conversations", Map.of("userId", bob.id().toString(), "body", "sup"), d)).get("id").asString();
        assertThat(api.body(api.post("/api/v1/conversations/" + daveToBob + "/decline", null, b)).get("state").asString()).isEqualTo("DECLINED");
        MvcResult declined = api.post("/api/v1/conversations/" + daveToBob + "/messages", Map.of("body", "please"), d);
        api.assertStatus(declined, 409);
        assertThat(api.code(declined)).isEqualTo("REQUEST_DECLINED");
        api.assertStatus(api.post("/api/v1/conversations/" + daveToBob + "/messages", Map.of("body", "changed my mind"), b), 201);
        api.assertStatus(api.post("/api/v1/conversations/" + daveToBob + "/messages", Map.of("body", "thanks"), d), 201);

        // Archive and blocking.
        assertThat(api.body(api.post("/api/v1/conversations/" + id + "/archive", null, b)).get("state").asString()).isEqualTo("ARCHIVED");
        assertThat(ids(api.body(api.get("/api/v1/conversations?box=archived", b)))).contains(id);
        api.assertStatus(api.post("/api/v1/blocks", Map.of("userId", bob.id().toString()), a), 204);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "blocked?"), a), 404);
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "blocked?"), b), 404);
        api.assertStatus(api.post("/api/v1/conversations", Map.of("userId", alice.id().toString(), "body", "x"), b), 404);
        api.assertStatus(api.post("/api/v1/conversations", Map.of("userId", alice.id().toString(), "body", "x"), a), 400);
    }

    @Test
    void reportedMessagesCarryOnlyASnapshotAndCanBeRemoved() throws Exception {
        TestActors.Actor moderator = actors.privileged(api, "ms-mod", Set.of(RoleName.MODERATOR));
        TestActors.Actor alice = actors.verifiedMember(api, "ms2-alice");
        TestActors.Actor bob = actors.verifiedMember(api, "ms2-bob");
        ApiClient.Session m = actors.login(api, moderator);
        ApiClient.Session a = actors.login(api, alice);
        ApiClient.Session b = actors.login(api, bob);
        setPolicy(b, "EVERYONE");

        String id = api.body(api.post("/api/v1/conversations", Map.of("userId", bob.id().toString(), "body", "first"), a)).get("id").asString();
        String abusive = api.body(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "you are worthless"), a)).get("id").asString();
        api.assertStatus(api.post("/api/v1/conversations/" + id + "/messages", Map.of("body", "private context"), b), 201);

        api.assertStatus(api.post("/api/v1/reports", Map.of("targetType", "MESSAGE", "targetId", abusive, "reason", "HARASSMENT"), a), 400);
        MvcResult report = api.post("/api/v1/reports", Map.of("targetType", "MESSAGE", "targetId", abusive, "reason", "HARASSMENT"), b);
        api.assertStatus(report, 201);
        String reportId = api.body(report).get("id").asString();
        api.assertStatus(api.post("/api/v1/reports", Map.of("targetType", "MESSAGE", "targetId", UUID.randomUUID().toString(), "reason", "SPAM"), b), 404);

        JsonNode detail = api.body(api.get("/api/v1/moderation/reports/" + reportId, m));
        assertThat(detail.get("contentSnapshot").asString()).isEqualTo("you are worthless");
        assertThat(detail.get("targetOwner").get("userId").asString()).isEqualTo(alice.id().toString());
        assertThat(detail.get("targetPost").isNull()).isTrue();
        assertThat(detail.toString()).as("moderators never see the rest of the thread").doesNotContain("private context").doesNotContain("first");
        api.assertStatus(api.get("/api/v1/conversations/" + id, m), 404);

        assertThat(api.body(api.post("/api/v1/moderation/reports/" + reportId + "/resolve",
                Map.of("resolution", "CONTENT_REMOVED", "note", "Harassment"), m)).get("status").asString()).isEqualTo("RESOLVED");
        JsonNode history = api.body(api.get("/api/v1/conversations/" + id + "/messages", b));
        JsonNode removed = null;
        for (JsonNode node : history) {
            if (node.get("id").asString().equals(abusive)) {
                removed = node;
            }
        }
        assertThat(removed).isNotNull();
        assertThat(removed.get("deleted").asBoolean()).isTrue();
        assertThat(api.body(api.get("/api/v1/notifications", a)).get("content").findValuesAsString("type")).contains("CONTENT_REMOVED");
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").findValuesAsString("id").stream().toList();
    }
}
